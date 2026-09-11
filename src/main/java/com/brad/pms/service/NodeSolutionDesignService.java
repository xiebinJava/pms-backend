package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
import com.brad.pms.common.enums.NodeStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodeSolutionDecisionConfirmCmd;
import com.brad.pms.dto.request.NodeSolutionDecisionUpdateCmd;
import com.brad.pms.dto.request.NodeSolutionPackageUpdateCmd;
import com.brad.pms.dto.response.NodeRequirementBaselineSummaryDTO;
import com.brad.pms.dto.response.NodeRequirementScopeDTO;
import com.brad.pms.dto.response.NodeSolutionDecisionDTO;
import com.brad.pms.dto.response.NodeSolutionDesignDTO;
import com.brad.pms.dto.response.NodeSolutionPackageDTO;
import com.brad.pms.dto.response.NodeSolutionReviewDTO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeSolutionDecisionDO;
import com.brad.pms.entity.ProjectNodeSolutionPackageDO;
import com.brad.pms.entity.ProjectNodeSolutionReviewDO;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectNodeSolutionDecisionMapper;
import com.brad.pms.mapper.ProjectNodeSolutionPackageMapper;
import com.brad.pms.mapper.ProjectNodeSolutionReviewMapper;
import com.brad.pms.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NodeSolutionDesignService {

    public static final String DESIGN_NODE_KEY = "design";
    public static final List<String> REVIEW_TYPES = List.of("BUSINESS_PRODUCT", "TECHNICAL", "TEST_RELEASE");
    private static final Set<String> DECISION_RESULTS = Set.of("PASS", "CONDITIONAL_PASS", "RETURN_FOR_CHANGES");

    private final ProjectNodeSolutionPackageMapper packageMapper;
    private final ProjectNodeSolutionReviewMapper reviewMapper;
    private final ProjectNodeSolutionDecisionMapper decisionMapper;
    private final ProjectNodeMapper nodeMapper;
    private final MemberService memberService;
    private final ProjectPermissionService permissionService;
    private final NodeRequirementScopeService requirementScopeService;
    private final OperationLogService operationLogService;

    public NodeSolutionDesignDTO get(Long projectId, Long nodeId) {
        ProjectNodeDO node = requireDesignNode(permissionService.requireNode(projectId, nodeId));
        permissionService.requireProjectReadable(projectId);
        return toDTO(projectId, nodeId, node);
    }

    @Transactional
    public NodeSolutionDesignDTO saveDraft(Long projectId, Long nodeId, NodeSolutionPackageUpdateCmd cmd) {
        ProjectNodeDO node = requireManageableDesignNode(projectId, nodeId, "保存方案包");
        if (cmd == null) throw BusinessException.error("方案包内容不能为空");

        ProjectNodeSolutionDecisionDO decision = findDecision(projectId, nodeId);
        ProjectNodeSolutionPackageDO current = findPackage(projectId, nodeId);
        if (current != null && (cmd.getVersion() == null || !Objects.equals(cmd.getVersion(), current.getVersion()))) {
            throw BusinessException.conflict("方案包已被其他人修改，请刷新后重试");
        }
        boolean contentChanged = current != null && packageContentChanged(current, cmd);
        ProjectNodeSolutionPackageDO target = current == null ? newPackage(projectId, nodeId) : current;
        target.setProductSolution(trim(cmd.getProductSolution()));
        target.setTechnicalSolution(trim(cmd.getTechnicalSolution()));
        target.setStatus("DRAFT");
        if (current == null) {
            try {
                packageMapper.insert(target);
            } catch (DuplicateKeyException ex) {
                throw BusinessException.conflict("方案包已被其他人创建，请刷新后重试");
            }
        } else if (packageMapper.updateById(target) != 1) {
            throw BusinessException.conflict("方案包已被其他人修改，请刷新后重试");
        }

        if (contentChanged) resetReviewsAndDecision(projectId, nodeId, decision);
        NodeSolutionDesignDTO result = toDTO(projectId, nodeId, node);
        operationLogService.record(AuditEvent.success(
                AuditAction.NODE_SOLUTION_PACKAGE_DRAFT_SAVED.name(), AuditResourceType.PROJECT_NODE.name(),
                nodeId, projectId, null, null, result));
        return result;
    }

    @Transactional
    public NodeSolutionDesignDTO submitPackage(Long projectId, Long nodeId, Integer expectedVersion) {
        ProjectNodeDO node = requireManageableDesignNode(projectId, nodeId, "提交方案包");
        ProjectNodeSolutionPackageDO pkg = requirePackage(projectId, nodeId);
        checkVersion(pkg.getVersion(), expectedVersion, "方案包已被其他人修改，请刷新后重试");
        validatePackage(pkg);
        NodeRequirementBaselineSummaryDTO baseline = upstreamBaseline(projectId);
        if (!baseline.isConfirmed()) throw BusinessException.error("请先确认需求范围基线");

        pkg.setStatus("SUBMITTED");
        if (packageMapper.updateById(pkg) != 1) {
            throw BusinessException.conflict("方案包已被其他人修改，请刷新后重试");
        }
        NodeSolutionDesignDTO result = toDTO(projectId, nodeId, node);
        operationLogService.record(AuditEvent.success(
                AuditAction.NODE_SOLUTION_PACKAGE_SUBMITTED.name(), AuditResourceType.PROJECT_NODE.name(),
                nodeId, projectId, null, java.util.Map.of("status", "DRAFT"), java.util.Map.of("status", "SUBMITTED")));
        return result;
    }

    @Transactional
    public NodeSolutionDesignDTO completeReview(Long projectId, Long nodeId, String reviewType,
                                                Integer expectedVersion, String comment) {
        ProjectNodeDO node = requireDesignNode(permissionService.requireReviewableNode(projectId, nodeId, "完成方案评审"));
        String normalizedType = upper(reviewType);
        if (!REVIEW_TYPES.contains(normalizedType)) throw BusinessException.error("方案评审类型不合法");
        ProjectNodeSolutionPackageDO pkg = requirePackage(projectId, nodeId);
        if (!"SUBMITTED".equals(pkg.getStatus())) throw BusinessException.error("请先提交方案包");

        ProjectNodeSolutionReviewDO review = findReview(projectId, nodeId, normalizedType);
        if (review == null || review.getReviewerId() == null) {
            throw BusinessException.error("请先设置指定评审人");
        }
        requireVersion(review.getVersion(), expectedVersion, "方案评审已被其他人修改，请刷新后重试");
        if ("PASSED".equals(review.getStatus())) {
            throw BusinessException.conflict("评审已完成");
        }
        if (!UserContext.isAdministrator() && !Objects.equals(review.getReviewerId(), UserContext.userIdOrNull())) {
            throw BusinessException.error("只有指定评审人或管理员可以完成评审");
        }
        review.setStatus("PASSED");
        review.setComment(trim(comment));
        review.setCompletedBy(UserContext.userIdOrNull());
        review.setCompletedAt(LocalDateTime.now());
        if (reviewMapper.updateById(review) != 1) {
            throw BusinessException.conflict("方案评审已被其他人修改，请刷新后重试");
        }
        NodeSolutionDesignDTO result = toDTO(projectId, nodeId, node);
        operationLogService.record(AuditEvent.success(
                AuditAction.NODE_SOLUTION_REVIEW_COMPLETED.name(), AuditResourceType.PROJECT_NODE.name(),
                nodeId, projectId, normalizedType, java.util.Map.of("status", "PENDING"), java.util.Map.of("status", "PASSED")));
        return result;
    }

    @Transactional
    public NodeSolutionDesignDTO assignReviewer(Long projectId, Long nodeId, String reviewType,
                                                Integer expectedVersion, Long reviewerId) {
        ProjectNodeDO node = requireManageableDesignNode(projectId, nodeId, "设置方案评审人");
        String normalizedType = upper(reviewType);
        if (!REVIEW_TYPES.contains(normalizedType)) throw BusinessException.error("方案评审类型不合法");
        if (reviewerId == null) throw BusinessException.error("请选择评审人");
        memberService.ensureMember(projectId, reviewerId);
        ProjectNodeSolutionReviewDO review = findReview(projectId, nodeId, normalizedType);
        if (review == null) {
            review = new ProjectNodeSolutionReviewDO();
            review.setProjectId(projectId);
            review.setNodeId(nodeId);
            review.setReviewType(normalizedType);
            review.setStatus("PENDING");
            review.setReviewerId(reviewerId);
            review.setVersion(0);
            try {
                reviewMapper.insert(review);
            } catch (DuplicateKeyException ex) {
                throw BusinessException.conflict("方案评审已被其他人修改，请刷新后重试");
            }
        } else {
            requireVersion(review.getVersion(), expectedVersion, "方案评审已被其他人修改，请刷新后重试");
            boolean reviewerChanged = !Objects.equals(review.getReviewerId(), reviewerId);
            review.setReviewerId(reviewerId);
            if (reviewerChanged && "PASSED".equals(review.getStatus())) {
                review.setStatus("PENDING");
                review.setCompletedBy(null);
                review.setCompletedAt(null);
                ProjectNodeSolutionDecisionDO decision = findDecision(projectId, nodeId);
                if (decision != null && "CONFIRMED".equals(decision.getStatus())) {
                    decision.setStatus("DRAFT");
                    decision.setConfirmedBy(null);
                    decision.setConfirmedAt(null);
                    if (decisionMapper.updateById(decision) != 1) {
                        throw BusinessException.conflict("方案决策已被其他人修改，请刷新后重试");
                    }
                }
            }
            if (reviewMapper.updateById(review) != 1) {
                throw BusinessException.conflict("方案评审已被其他人修改，请刷新后重试");
            }
        }
        NodeSolutionDesignDTO result = toDTO(projectId, nodeId, node);
        operationLogService.record(AuditEvent.success(
                AuditAction.NODE_SOLUTION_REVIEWER_ASSIGNED.name(), AuditResourceType.PROJECT_NODE.name(),
                nodeId, projectId, normalizedType, null, java.util.Map.of("reviewerId", reviewerId)));
        return result;
    }

    @Transactional
    public NodeSolutionDesignDTO updateReviewSuggestion(Long projectId, Long nodeId, String reviewType,
                                                       Integer expectedVersion, String comment) {
        ProjectNodeDO node = requireManageableDesignNode(projectId, nodeId, "更新方案评审建议");
        String normalizedType = upper(reviewType);
        if (!REVIEW_TYPES.contains(normalizedType)) throw BusinessException.error("方案评审类型不合法");
        ProjectNodeSolutionReviewDO review = findReview(projectId, nodeId, normalizedType);
        if (review == null) throw BusinessException.error("请先设置指定评审人");
        requireVersion(review.getVersion(), expectedVersion, "方案评审已被其他人修改，请刷新后重试");
        review.setComment(trim(comment));
        if (reviewMapper.updateById(review) != 1) {
            throw BusinessException.conflict("方案评审已被其他人修改，请刷新后重试");
        }
        NodeSolutionDesignDTO result = toDTO(projectId, nodeId, node);
        operationLogService.record(AuditEvent.success(
                AuditAction.NODE_SOLUTION_REVIEW_UPDATED.name(), AuditResourceType.PROJECT_NODE.name(),
                nodeId, projectId, normalizedType, null, java.util.Map.of("commentUpdated", true)));
        return result;
    }

    @Transactional
    public NodeSolutionDesignDTO confirmDecision(Long projectId, Long nodeId, NodeSolutionDecisionConfirmCmd cmd) {
        ProjectNodeDO node = requireManageableDesignNode(projectId, nodeId, "确认方案决策");
        ProjectNodeSolutionPackageDO pkg = requirePackage(projectId, nodeId);
        if (!"SUBMITTED".equals(pkg.getStatus())) throw BusinessException.error("请先提交方案包");
        List<ProjectNodeSolutionReviewDO> reviews = findReviews(projectId, nodeId);
        if (REVIEW_TYPES.stream().anyMatch(type -> reviews.stream()
                .noneMatch(review -> type.equals(review.getReviewType())
                        && review.getReviewerId() != null
                        && "PASSED".equals(review.getStatus())))) {
            throw BusinessException.error("请先设置三类评审人并完成评审");
        }
        if (cmd == null) throw BusinessException.error("方案决策不能为空");
        String result = upper(cmd.getResult());
        String conditions = trim(cmd.getConditions());
        if (!DECISION_RESULTS.contains(result)) throw BusinessException.error("方案决策结果不合法");
        if ("CONDITIONAL_PASS".equals(result) && conditions == null) throw BusinessException.error("请填写通过条件");

        ProjectNodeSolutionDecisionDO decision = findDecision(projectId, nodeId);
        if (decision != null && "CONFIRMED".equals(decision.getStatus())) {
            throw BusinessException.conflict("方案决策已确认");
        }
        if (decision == null) {
            decision = new ProjectNodeSolutionDecisionDO();
            decision.setProjectId(projectId);
            decision.setNodeId(nodeId);
            decision.setVersion(0);
        } else {
            checkVersion(decision.getVersion(), cmd.getVersion(), "方案决策已被其他人修改，请刷新后重试");
        }
        decision.setResult(result);
        decision.setConditions(conditions);
        decision.setStatus("CONFIRMED");
        decision.setConfirmedBy(UserContext.userIdOrNull());
        decision.setConfirmedAt(LocalDateTime.now());
        if (decision.getId() == null) {
            try {
                decisionMapper.insert(decision);
            } catch (DuplicateKeyException ex) {
                throw BusinessException.conflict("方案决策已被其他人确认，请刷新后重试");
            }
        } else if (decisionMapper.updateById(decision) != 1) {
            throw BusinessException.conflict("方案决策已被其他人修改，请刷新后重试");
        }
        NodeSolutionDesignDTO response = toDTO(projectId, nodeId, node);
        operationLogService.record(AuditEvent.success(
                AuditAction.NODE_SOLUTION_DECISION_CONFIRMED.name(), AuditResourceType.PROJECT_NODE.name(),
                nodeId, projectId, null, java.util.Map.of("status", "DRAFT"), java.util.Map.of("status", "CONFIRMED")));
        return response;
    }

    @Transactional
    public NodeSolutionDesignDTO saveDecisionDraft(Long projectId, Long nodeId, NodeSolutionDecisionUpdateCmd cmd) {
        ProjectNodeDO node = requireManageableDesignNode(projectId, nodeId, "保存方案决策");
        if (cmd == null) throw BusinessException.error("方案决策不能为空");
        String result = upper(cmd.getResult());
        String conditions = trim(cmd.getConditions());
        if (result != null && !DECISION_RESULTS.contains(result)) throw BusinessException.error("方案决策结果不合法");

        ProjectNodeSolutionDecisionDO decision = findDecision(projectId, nodeId);
        if (decision == null) {
            decision = new ProjectNodeSolutionDecisionDO();
            decision.setProjectId(projectId);
            decision.setNodeId(nodeId);
            decision.setVersion(0);
            decision.setStatus("DRAFT");
            decision.setResult(result);
            decision.setConditions(conditions);
            try {
                decisionMapper.insert(decision);
            } catch (DuplicateKeyException ex) {
                throw BusinessException.conflict("方案决策已被其他人创建，请刷新后重试");
            }
        } else {
            checkVersion(decision.getVersion(), cmd.getVersion(), "方案决策已被其他人修改，请刷新后重试");
            decision.setResult(result);
            decision.setConditions(conditions);
            decision.setStatus("DRAFT");
            decision.setConfirmedBy(null);
            decision.setConfirmedAt(null);
            if (decisionMapper.updateById(decision) != 1) {
                throw BusinessException.conflict("方案决策已被其他人修改，请刷新后重试");
            }
        }
        NodeSolutionDesignDTO response = toDTO(projectId, nodeId, node);
        operationLogService.record(AuditEvent.success(
                AuditAction.NODE_SOLUTION_DECISION_DRAFT_SAVED.name(), AuditResourceType.PROJECT_NODE.name(),
                nodeId, projectId, null, null, response));
        return response;
    }

    /** Lifecycle completion guard; it is intentionally independent from the UI button state. */
    public void requireConfirmed(Long projectId, Long nodeId) {
        permissionService.requireProjectReadable(projectId);
        requireDesignNode(permissionService.requireNode(projectId, nodeId));
        ProjectNodeSolutionDecisionDO decision = findDecision(projectId, nodeId);
        if (decision == null || !"CONFIRMED".equals(decision.getStatus())) {
            throw BusinessException.error("请先确认方案决策");
        }
    }

    private ProjectNodeDO requireManageableDesignNode(Long projectId, Long nodeId, String action) {
        return requireDesignNode(permissionService.requireManageableNode(projectId, nodeId, action));
    }

    private ProjectNodeDO requireDesignNode(ProjectNodeDO node) {
        if (node == null || !DESIGN_NODE_KEY.equals(node.getNodeKey())) {
            throw BusinessException.error("仅方案设计、评审与决策节点支持方案工作台");
        }
        return node;
    }

    private ProjectNodeSolutionPackageDO requirePackage(Long projectId, Long nodeId) {
        ProjectNodeSolutionPackageDO pkg = findPackage(projectId, nodeId);
        if (pkg == null) throw BusinessException.error("请先保存方案包");
        return pkg;
    }

    private ProjectNodeSolutionPackageDO findPackage(Long projectId, Long nodeId) {
        return packageMapper.selectOne(new LambdaQueryWrapper<ProjectNodeSolutionPackageDO>()
                .eq(ProjectNodeSolutionPackageDO::getProjectId, projectId)
                .eq(ProjectNodeSolutionPackageDO::getNodeId, nodeId));
    }

    private ProjectNodeSolutionDecisionDO findDecision(Long projectId, Long nodeId) {
        return decisionMapper.selectOne(new LambdaQueryWrapper<ProjectNodeSolutionDecisionDO>()
                .eq(ProjectNodeSolutionDecisionDO::getProjectId, projectId)
                .eq(ProjectNodeSolutionDecisionDO::getNodeId, nodeId));
    }

    private ProjectNodeSolutionReviewDO findReview(Long projectId, Long nodeId, String reviewType) {
        return reviewMapper.selectOne(new LambdaQueryWrapper<ProjectNodeSolutionReviewDO>()
                .eq(ProjectNodeSolutionReviewDO::getProjectId, projectId)
                .eq(ProjectNodeSolutionReviewDO::getNodeId, nodeId)
                .eq(ProjectNodeSolutionReviewDO::getReviewType, reviewType));
    }

    private List<ProjectNodeSolutionReviewDO> findReviews(Long projectId, Long nodeId) {
        List<ProjectNodeSolutionReviewDO> reviews = reviewMapper.selectList(new LambdaQueryWrapper<ProjectNodeSolutionReviewDO>()
                .eq(ProjectNodeSolutionReviewDO::getProjectId, projectId)
                .eq(ProjectNodeSolutionReviewDO::getNodeId, nodeId));
        return reviews == null ? List.of() : reviews;
    }

    private ProjectNodeSolutionPackageDO newPackage(Long projectId, Long nodeId) {
        ProjectNodeSolutionPackageDO pkg = new ProjectNodeSolutionPackageDO();
        pkg.setProjectId(projectId);
        pkg.setNodeId(nodeId);
        pkg.setVersion(0);
        pkg.setCreatedBy(UserContext.userIdOrNull());
        return pkg;
    }

    private boolean packageContentChanged(ProjectNodeSolutionPackageDO current, NodeSolutionPackageUpdateCmd cmd) {
        return !Objects.equals(current.getProductSolution(), trim(cmd.getProductSolution()))
                || !Objects.equals(current.getTechnicalSolution(), trim(cmd.getTechnicalSolution()));
    }

    private void resetReviewsAndDecision(Long projectId, Long nodeId, ProjectNodeSolutionDecisionDO decision) {
        for (ProjectNodeSolutionReviewDO review : findReviews(projectId, nodeId)) {
            review.setStatus("PENDING");
            review.setComment(null);
            review.setCompletedBy(null);
            review.setCompletedAt(null);
            if (reviewMapper.updateById(review) != 1) {
                throw BusinessException.conflict("方案评审已被其他人修改，请刷新后重试");
            }
        }
        if (decision != null && "CONFIRMED".equals(decision.getStatus())) {
            decision.setStatus("DRAFT");
            decision.setConfirmedBy(null);
            decision.setConfirmedAt(null);
            if (decisionMapper.updateById(decision) != 1) {
                throw BusinessException.conflict("方案决策已被其他人修改，请刷新后重试");
            }
        }
    }

    private void validatePackage(ProjectNodeSolutionPackageDO pkg) {
        if (trim(pkg.getProductSolution()) == null) throw BusinessException.error("请填写产品方案");
        if (trim(pkg.getTechnicalSolution()) == null) throw BusinessException.error("请填写技术方案");
    }

    private NodeRequirementBaselineSummaryDTO upstreamBaseline(Long projectId) {
        ProjectNodeDO requirementNode = nodeMapper.selectOne(new LambdaQueryWrapper<ProjectNodeDO>()
                .eq(ProjectNodeDO::getProjectId, projectId)
                .eq(ProjectNodeDO::getNodeKey, "requirement"));
        NodeRequirementBaselineSummaryDTO summary = new NodeRequirementBaselineSummaryDTO();
        if (requirementNode == null) return summary;
        NodeRequirementScopeDTO baseline = requirementScopeService.get(projectId, requirementNode.getId());
        summary.setAvailable(baseline != null);
        if (baseline != null) {
            summary.setConfirmed(baseline.getBaselineStatus() == 1);
            summary.setVersion(baseline.getVersion());
            List<com.brad.pms.dto.response.NodeScopeItemDTO> scopeItems = baseline.getScopeItems() == null
                    ? List.of() : baseline.getScopeItems();
            List<com.brad.pms.dto.response.NodeRequirementDTO> requirements = baseline.getRequirements() == null
                    ? List.of() : baseline.getRequirements();
            summary.setInScopeCount((int) scopeItems.stream().filter(item -> "IN".equals(item.getDirection())).count());
            summary.setRequirementCount(requirements.size());
        }
        return summary;
    }

    private NodeSolutionDesignDTO toDTO(Long projectId, Long nodeId, ProjectNodeDO node) {
        NodeSolutionDesignDTO dto = new NodeSolutionDesignDTO();
        dto.setProjectId(projectId);
        dto.setNodeId(nodeId);
        ProjectNodeSolutionDecisionDO decision = findDecision(projectId, nodeId);
        ProjectNodeSolutionPackageDO pkg = findPackage(projectId, nodeId);
        dto.setUpstreamBaseline(upstreamBaseline(projectId));
        dto.setSolutionPackage(toPackageDTO(pkg, node, decision));
        List<NodeSolutionReviewDTO> reviewDTOs = new ArrayList<>();
        List<ProjectNodeSolutionReviewDO> existingReviews = findReviews(projectId, nodeId);
        for (String type : REVIEW_TYPES) {
            ProjectNodeSolutionReviewDO review = existingReviews.stream()
                    .filter(item -> type.equals(item.getReviewType()))
                    .findFirst().orElse(null);
            reviewDTOs.add(toReviewDTO(review, type, pkg, decision));
        }
        dto.setReviews(reviewDTOs);
        dto.setDecision(toDecisionDTO(decision, node));
        return dto;
    }

    private NodeSolutionPackageDTO toPackageDTO(ProjectNodeSolutionPackageDO pkg,
                                                ProjectNodeDO node,
                                                ProjectNodeSolutionDecisionDO decision) {
        NodeSolutionPackageDTO dto = new NodeSolutionPackageDTO();
        if (pkg != null) {
            dto.setId(pkg.getId());
            dto.setProductSolution(pkg.getProductSolution());
            dto.setTechnicalSolution(pkg.getTechnicalSolution());
            dto.setStatus(pkg.getStatus());
            dto.setVersion(pkg.getVersion());
        } else {
            dto.setStatus("DRAFT");
            dto.setVersion(0);
        }
        dto.setCanEdit(!NodeStatus.isReadOnly(node.getStatus()));
        return dto;
    }

    private NodeSolutionReviewDTO toReviewDTO(ProjectNodeSolutionReviewDO review,
                                              String type,
                                              ProjectNodeSolutionPackageDO pkg,
                                              ProjectNodeSolutionDecisionDO decision) {
        NodeSolutionReviewDTO dto = new NodeSolutionReviewDTO();
        dto.setReviewType(type);
        dto.setStatus(review == null ? "PENDING" : review.getStatus());
        dto.setVersion(review == null ? 0 : review.getVersion());
        if (review != null) {
            dto.setReviewerId(review.getReviewerId());
            dto.setCanComplete("PENDING".equals(review.getStatus())
                    && pkg != null
                    && "SUBMITTED".equals(pkg.getStatus())
                    && (UserContext.isAdministrator() || Objects.equals(review.getReviewerId(), UserContext.userIdOrNull())));
            dto.setComment(review.getComment());
            dto.setCompletedBy(review.getCompletedBy());
            dto.setCompletedAt(review.getCompletedAt());
        }
        return dto;
    }

    private NodeSolutionDecisionDTO toDecisionDTO(ProjectNodeSolutionDecisionDO decision, ProjectNodeDO node) {
        NodeSolutionDecisionDTO dto = new NodeSolutionDecisionDTO();
        if (decision != null) {
            dto.setId(decision.getId());
            dto.setResult(decision.getResult());
            dto.setConditions(decision.getConditions());
            dto.setStatus(decision.getStatus());
            dto.setConfirmedBy(decision.getConfirmedBy());
            dto.setConfirmedAt(decision.getConfirmedAt());
            dto.setVersion(decision.getVersion());
        } else {
            dto.setStatus("DRAFT");
            dto.setVersion(0);
        }
        dto.setCanEdit(!NodeStatus.isReadOnly(node.getStatus()));
        return dto;
    }

    private void checkVersion(Integer actual, Integer expected, String message) {
        if (expected != null && !Objects.equals(actual, expected)) throw BusinessException.conflict(message);
    }

    private void requireVersion(Integer actual, Integer expected, String message) {
        if (expected == null || !Objects.equals(actual, expected)) throw BusinessException.conflict(message);
    }

    private String upper(String value) {
        String text = trim(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        String text = value == null ? null : value.trim();
        return text == null || text.isEmpty() ? null : text;
    }
}
