package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.request.NodePlanResourceRiskUpdateCmd;
import com.brad.pms.dto.request.NodeIterationPlanCmd;
import com.brad.pms.dto.request.NodeResourceCmd;
import com.brad.pms.dto.request.NodeRiskCmd;
import com.brad.pms.dto.response.NodePlanResourceRiskDTO;
import com.brad.pms.dto.response.NodeResourceDTO;
import com.brad.pms.dto.response.NodeRiskDTO;
import com.brad.pms.dto.response.NodeIterationPlanDTO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodePlanBaselineDO;
import com.brad.pms.entity.ProjectNodeResourceDO;
import com.brad.pms.entity.ProjectNodeRiskDO;
import com.brad.pms.entity.ProjectNodeIterationPlanDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeSolutionDecisionDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectNodePlanBaselineMapper;
import com.brad.pms.mapper.ProjectNodeResourceMapper;
import com.brad.pms.mapper.ProjectNodeRiskMapper;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeSolutionDecisionMapper;
import com.brad.pms.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NodePlanResourceRiskService {

    private static final String PLAN_NODE_KEY = "plan";
    private static final Set<String> RESOURCE_STATUSES = Set.of("PENDING", "CONFIRMED");
    private static final Set<String> RISK_LEVELS = Set.of("HIGH", "MEDIUM", "LOW");
    private static final Set<String> RISK_STATUSES = Set.of("OPEN", "MITIGATED");

    private final ProjectNodePlanBaselineMapper baselineMapper;
    private final ProjectNodeResourceMapper resourceMapper;
    private final ProjectNodeRiskMapper riskMapper;
    private final ProjectNodeIterationPlanMapper iterationPlanMapper;
    private final ProjectNodeDevelopmentStoryMapper storyMapper;
    private final ProjectNodeSolutionDecisionMapper decisionMapper;
    private final ProjectNodeMapper nodeMapper;
    private final MemberService memberService;
    private final ProjectPermissionService permissionService;
    private final UserService userService;
    private final OperationLogService operationLogService;

    public NodePlanResourceRiskDTO get(Long projectId, Long nodeId) {
        permissionService.requireProjectReadable(projectId);
        ProjectNodeDO node = requirePlanNode(permissionService.requireNode(projectId, nodeId));
        return toDTO(node, findBaseline(projectId, nodeId));
    }

    @Transactional
    public NodePlanResourceRiskDTO saveDraft(Long projectId, Long nodeId, NodePlanResourceRiskUpdateCmd cmd) {
        ProjectNodeDO node = requirePlanNode(permissionService.requireManageableNode(
                projectId, nodeId, "保存计划、资源与风险基线"));
        ProjectNodeSolutionDecisionDO solutionDecision = confirmedSolutionDecision(projectId);
        validatePayload(cmd);
        validateProjectOwners(projectId, cmd);

        ProjectNodePlanBaselineDO baseline = findBaseline(projectId, nodeId);
        if (baseline == null) {
            baseline = newBaseline(projectId, nodeId);
            try {
                baselineMapper.insert(baseline);
            } catch (DuplicateKeyException ex) {
                throw BusinessException.conflict("计划、资源与风险基线已被其他人创建，请刷新后重试");
            }
        } else if (cmd.getVersion() == null || !Objects.equals(cmd.getVersion(), baseline.getVersion())) {
            throw BusinessException.conflict("计划、资源与风险基线已被其他人修改，请刷新后重试");
        }

        baseline.setStatus(0);
        baseline.setConfirmedBy(null);
        baseline.setConfirmedAt(null);
        baseline.setSolutionDecisionVersion(solutionDecision.getVersion());
        if (baselineMapper.updateById(baseline) != 1) {
            throw BusinessException.conflict("计划、资源与风险基线已被其他人修改，请刷新后重试");
        }
        replaceResources(projectId, nodeId, cmd.getResources());
        replaceRisks(projectId, nodeId, cmd.getRisks());
        replaceIterationPlans(projectId, nodeId, cmd.getIterationPlans());
        NodePlanResourceRiskDTO result = toDTO(node, baseline);
        operationLogService.record(AuditEvent.success(
                AuditAction.NODE_PLAN_RESOURCE_RISK_DRAFT_SAVED.name(), AuditResourceType.PROJECT_NODE.name(),
                nodeId, projectId, null, null, result));
        return result;
    }

    @Transactional
    public NodePlanResourceRiskDTO confirm(Long projectId, Long nodeId) {
        ProjectNodeDO node = requirePlanNode(permissionService.requireManageableNode(
                projectId, nodeId, "确认计划、资源与风险基线"));
        ProjectNodePlanBaselineDO baseline = findBaseline(projectId, nodeId);
        if (baseline == null) throw BusinessException.error("请先保存计划、资源与风险基线");
        if (Integer.valueOf(1).equals(baseline.getStatus())) {
            throw BusinessException.conflict("计划、资源与风险基线已确认");
        }
        ProjectNodeSolutionDecisionDO solutionDecision = confirmedSolutionDecision(projectId);
        NodePlanResourceRiskDTO current = toDTO(node, baseline);
        if (current.isSourceDecisionChanged()) {
            throw BusinessException.error("方案决策已变更，请重新完成计划、资源与风险基线");
        }
        validateComplete(current);
        baseline.setStatus(1);
        baseline.setConfirmedBy(UserContext.userIdOrNull());
        baseline.setConfirmedAt(LocalDateTime.now());
        baseline.setSolutionDecisionVersion(solutionDecision.getVersion());
        if (baselineMapper.updateById(baseline) != 1) {
            throw BusinessException.conflict("计划、资源与风险基线已被其他人修改，请刷新后重试");
        }
        NodePlanResourceRiskDTO result = toDTO(node, baseline);
        operationLogService.record(AuditEvent.success(
                AuditAction.NODE_PLAN_RESOURCE_RISK_CONFIRMED.name(), AuditResourceType.PROJECT_NODE.name(),
                nodeId, projectId, null, java.util.Map.of("status", "DRAFT"), java.util.Map.of("status", "CONFIRMED")));
        return result;
    }

    /** Lifecycle completion guard; the UI state is not a substitute for this check. */
    public void requireConfirmed(Long projectId, Long nodeId) {
        permissionService.requireProjectReadable(projectId);
        ProjectNodeDO node = requirePlanNode(permissionService.requireNode(projectId, nodeId));
        ProjectNodePlanBaselineDO baseline = findBaseline(projectId, nodeId);
        if (baseline == null || !Integer.valueOf(1).equals(baseline.getStatus())) {
            throw BusinessException.error("请先确认计划、资源与风险基线");
        }
        ProjectNodeSolutionDecisionDO solutionDecision = confirmedSolutionDecision(projectId);
        if (!Objects.equals(baseline.getSolutionDecisionVersion(), solutionDecision.getVersion())) {
            throw BusinessException.error("方案决策已变更，请重新完成计划、资源与风险基线");
        }
    }

    private void validatePayload(NodePlanResourceRiskUpdateCmd cmd) {
        if (cmd == null) throw BusinessException.error("计划、资源与风险基线内容不能为空");
        List<NodeResourceCmd> resources = safe(cmd.getResources());
        for (NodeResourceCmd item : resources) {
            item.setRole(trim(item.getRole()));
            item.setFocus(trim(item.getFocus()));
            item.setStatus(normalize(item.getStatus(), "PENDING"));
            if (item.getRole() == null) throw BusinessException.error("资源角色不能为空");
            if (!RESOURCE_STATUSES.contains(item.getStatus())) throw BusinessException.error("资源状态不合法");
        }
        List<NodeRiskCmd> risks = safe(cmd.getRisks());
        for (NodeRiskCmd item : risks) {
            item.setTitle(trim(item.getTitle()));
            item.setLevel(upper(item.getLevel()));
            item.setResponse(trim(item.getResponse()));
            item.setStatus(normalize(item.getStatus(), "OPEN"));
            if (item.getTitle() == null) throw BusinessException.error("风险名称不能为空");
            if (!RISK_LEVELS.contains(item.getLevel())) throw BusinessException.error("风险等级不合法");
            if (!RISK_STATUSES.contains(item.getStatus())) throw BusinessException.error("风险状态不合法");
        }
        List<NodeIterationPlanCmd> iterationPlans = safe(cmd.getIterationPlans());
        for (NodeIterationPlanCmd item : iterationPlans) {
            item.setName(trim(item.getName()));
            item.setGoal(trim(item.getGoal()));
            item.setStatus(normalize(item.getStatus(), "PLANNED"));
            if (!Set.of("PLANNED", "IN_PROGRESS", "DONE").contains(item.getStatus())) {
                throw BusinessException.error("迭代计划状态不合法");
            }
            if (item.getStartDate() != null && item.getDueDate() != null
                    && item.getStartDate().isAfter(item.getDueDate())) {
                throw BusinessException.error("迭代计划开始日期不能晚于结束日期");
            }
        }
        cmd.setResources(resources);
        cmd.setRisks(risks);
        cmd.setIterationPlans(iterationPlans);
    }

    private void validateProjectOwners(Long projectId, NodePlanResourceRiskUpdateCmd cmd) {
        Set<Long> ownerIds = new HashSet<>();
        cmd.getResources().stream().map(NodeResourceCmd::getOwnerId).filter(Objects::nonNull).forEach(ownerIds::add);
        cmd.getRisks().stream().map(NodeRiskCmd::getOwnerId).filter(Objects::nonNull).forEach(ownerIds::add);
        cmd.getIterationPlans().stream().map(NodeIterationPlanCmd::getOwnerId).filter(Objects::nonNull).forEach(ownerIds::add);
        memberService.ensureMembers(projectId, ownerIds);
    }

    private void validateComplete(NodePlanResourceRiskDTO current) {
        if (current.getIterationPlans().isEmpty()
                || current.getIterationPlans().stream().anyMatch(item -> trim(item.getName()) == null
                || item.getOwnerId() == null || item.getStartDate() == null || item.getDueDate() == null)) {
            throw BusinessException.error("请至少填写一条完整迭代计划，并明确负责人和排期");
        }
        if (current.getResources().isEmpty()
                || current.getResources().stream().anyMatch(item -> trim(item.getRole()) == null
                || item.getOwnerId() == null || trim(item.getFocus()) == null)) {
            throw BusinessException.error("请明确所有关键角色负责人和投入重点");
        }
        if (current.getRisks().isEmpty()
                || current.getRisks().stream().anyMatch(item -> trim(item.getTitle()) == null
                || item.getOwnerId() == null || trim(item.getResponse()) == null)) {
            throw BusinessException.error("请登记至少一项风险并填写负责人和应对措施");
        }
    }

    private void replaceResources(Long projectId, Long nodeId, List<NodeResourceCmd> items) {
        resourceMapper.delete(new LambdaQueryWrapper<ProjectNodeResourceDO>()
                .eq(ProjectNodeResourceDO::getProjectId, projectId)
                .eq(ProjectNodeResourceDO::getNodeId, nodeId));
        for (int i = 0; i < items.size(); i++) {
            NodeResourceCmd cmd = items.get(i);
            ProjectNodeResourceDO item = new ProjectNodeResourceDO();
            item.setProjectId(projectId);
            item.setNodeId(nodeId);
            item.setRoleName(cmd.getRole());
            item.setOwnerId(cmd.getOwnerId());
            item.setFocus(cmd.getFocus());
            item.setStatus(cmd.getStatus());
            item.setSort(cmd.getSort() == null ? i : cmd.getSort());
            item.setCreatedBy(UserContext.userIdOrNull());
            resourceMapper.insert(item);
        }
    }

    private void replaceRisks(Long projectId, Long nodeId, List<NodeRiskCmd> items) {
        riskMapper.delete(new LambdaQueryWrapper<ProjectNodeRiskDO>()
                .eq(ProjectNodeRiskDO::getProjectId, projectId)
                .eq(ProjectNodeRiskDO::getNodeId, nodeId));
        for (int i = 0; i < items.size(); i++) {
            NodeRiskCmd cmd = items.get(i);
            ProjectNodeRiskDO item = new ProjectNodeRiskDO();
            item.setProjectId(projectId);
            item.setNodeId(nodeId);
            item.setTitle(cmd.getTitle());
            item.setLevel(cmd.getLevel());
            item.setOwnerId(cmd.getOwnerId());
            item.setResponse(cmd.getResponse());
            item.setStatus(cmd.getStatus());
            item.setSort(cmd.getSort() == null ? i : cmd.getSort());
            item.setCreatedBy(UserContext.userIdOrNull());
            riskMapper.insert(item);
        }
    }

    private void replaceIterationPlans(Long projectId, Long nodeId, List<NodeIterationPlanCmd> items) {
        List<ProjectNodeIterationPlanDO> existing = safe(iterationPlanMapper.selectList(new LambdaQueryWrapper<ProjectNodeIterationPlanDO>()
                .eq(ProjectNodeIterationPlanDO::getProjectId, projectId)
                .eq(ProjectNodeIterationPlanDO::getNodeId, nodeId)));
        Map<Long, ProjectNodeIterationPlanDO> existingById = existing.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(ProjectNodeIterationPlanDO::getId, item -> item));
        Set<Long> retainedIds = new HashSet<>();
        for (NodeIterationPlanCmd cmd : items) {
            ProjectNodeIterationPlanDO item;
            if (cmd.getId() == null) {
                item = new ProjectNodeIterationPlanDO();
                item.setProjectId(projectId);
                item.setNodeId(nodeId);
                item.setCreatedBy(UserContext.userIdOrNull());
            } else {
                item = existingById.get(cmd.getId());
                if (item == null) throw BusinessException.error("迭代计划不存在，请刷新后重试");
                if (!retainedIds.add(item.getId())) throw BusinessException.error("迭代计划不能重复");
            }
            applyIterationPlanFields(item, cmd, cmd.getSort() == null ? retainedIds.size() : cmd.getSort());
            if (item.getId() == null) {
                if (iterationPlanMapper.insert(item) != 1) throw BusinessException.conflict("迭代计划保存失败，请刷新后重试");
                if (item.getId() != null) retainedIds.add(item.getId());
            } else if (iterationPlanMapper.updateById(item) != 1) {
                throw BusinessException.conflict("迭代计划已被其他人修改，请刷新后重试");
            }
        }
        for (ProjectNodeIterationPlanDO item : existing) {
            if (item.getId() == null || retainedIds.contains(item.getId())) continue;
            Long references = storyMapper.selectCount(new LambdaQueryWrapper<ProjectNodeDevelopmentStoryDO>()
                    .eq(ProjectNodeDevelopmentStoryDO::getProjectId, projectId)
                    .eq(ProjectNodeDevelopmentStoryDO::getIterationPlanId, item.getId()));
            if (references != null && references > 0) {
                throw BusinessException.error("迭代计划已被故事使用，请先调整故事关联后再删除");
            }
            iterationPlanMapper.deleteById(item.getId());
        }
    }

    private void applyIterationPlanFields(ProjectNodeIterationPlanDO item, NodeIterationPlanCmd cmd, int sort) {
        item.setName(cmd.getName());
        item.setOwnerId(cmd.getOwnerId());
        item.setGoal(cmd.getGoal());
        item.setStatus(cmd.getStatus());
        item.setStartDate(cmd.getStartDate());
        item.setDueDate(cmd.getDueDate());
        item.setSort(sort);
    }

    private NodePlanResourceRiskDTO toDTO(ProjectNodeDO node, ProjectNodePlanBaselineDO baseline) {
        NodePlanResourceRiskDTO dto = new NodePlanResourceRiskDTO();
        dto.setProjectId(node.getProjectId());
        dto.setNodeId(node.getId());
        dto.setVersion(baseline == null ? null : baseline.getVersion());
        dto.setBaselineStatus(baseline == null || baseline.getStatus() == null ? 0 : baseline.getStatus());
        dto.setConfirmedBy(baseline == null ? null : baseline.getConfirmedBy());
        dto.setConfirmedAt(baseline == null ? null : baseline.getConfirmedAt());
        ProjectNodeSolutionDecisionDO solutionDecision = findSolutionDecision(node.getProjectId());
        dto.setSourceDecisionVersion(solutionDecision == null ? null : solutionDecision.getVersion());
        boolean sourceDecisionChanged = baseline != null
                && !Objects.equals(baseline.getSolutionDecisionVersion(), dto.getSourceDecisionVersion());
        dto.setSourceDecisionChanged(sourceDecisionChanged);
        if (sourceDecisionChanged) {
            dto.setConfirmedBy(null);
            dto.setConfirmedByName(null);
            dto.setConfirmedAt(null);
        }
        dto.setCanEdit(!com.brad.pms.common.enums.NodeStatus.isReadOnly(node.getStatus()));

        List<ProjectNodeResourceDO> resources = resourceMapper.selectList(new LambdaQueryWrapper<ProjectNodeResourceDO>()
                .eq(ProjectNodeResourceDO::getProjectId, node.getProjectId())
                .eq(ProjectNodeResourceDO::getNodeId, node.getId())
                .orderByAsc(ProjectNodeResourceDO::getSort)
                .orderByAsc(ProjectNodeResourceDO::getId));
        List<ProjectNodeRiskDO> risks = riskMapper.selectList(new LambdaQueryWrapper<ProjectNodeRiskDO>()
                .eq(ProjectNodeRiskDO::getProjectId, node.getProjectId())
                .eq(ProjectNodeRiskDO::getNodeId, node.getId())
                .orderByAsc(ProjectNodeRiskDO::getSort)
                .orderByAsc(ProjectNodeRiskDO::getId));
        List<ProjectNodeIterationPlanDO> iterationPlans = safe(iterationPlanMapper.selectList(new LambdaQueryWrapper<ProjectNodeIterationPlanDO>()
                .eq(ProjectNodeIterationPlanDO::getProjectId, node.getProjectId())
                .eq(ProjectNodeIterationPlanDO::getNodeId, node.getId())
                .orderByAsc(ProjectNodeIterationPlanDO::getSort)
                .orderByAsc(ProjectNodeIterationPlanDO::getId)));
        Set<Long> ownerIds = new HashSet<>();
        resources.forEach(item -> addIfPresent(ownerIds, item.getOwnerId()));
        risks.forEach(item -> addIfPresent(ownerIds, item.getOwnerId()));
        iterationPlans.forEach(item -> addIfPresent(ownerIds, item.getOwnerId()));
        addIfPresent(ownerIds, dto.getConfirmedBy());
        List<UserDO> users = ownerIds.isEmpty() ? List.of() : userService.listByIds(new ArrayList<>(ownerIds));
        Map<Long, UserDO> userMap = Convertors.userMap(users == null ? List.of() : users);
        if (dto.getConfirmedBy() != null) dto.setConfirmedByName(Convertors.userDisplayName(userMap.get(dto.getConfirmedBy())));
        dto.setResources(resources.stream().map(item -> toResourceDTO(item, userMap.get(item.getOwnerId()))).collect(Collectors.toList()));
        dto.setRisks(risks.stream().map(item -> toRiskDTO(item, userMap.get(item.getOwnerId()))).collect(Collectors.toList()));
        dto.setIterationPlans(iterationPlans.stream().map(item -> toIterationPlanDTO(item, userMap.get(item.getOwnerId()))).collect(Collectors.toList()));
        return dto;
    }

    private NodeResourceDTO toResourceDTO(ProjectNodeResourceDO item, UserDO owner) {
        NodeResourceDTO dto = new NodeResourceDTO();
        dto.setId(item.getId()); dto.setRole(item.getRoleName()); dto.setOwnerId(item.getOwnerId());
        dto.setOwnerName(Convertors.userDisplayName(owner)); dto.setOwnerAvatar(owner == null ? null : owner.getAvatar());
        dto.setFocus(item.getFocus()); dto.setStatus(item.getStatus()); dto.setSort(item.getSort());
        return dto;
    }

    private NodeRiskDTO toRiskDTO(ProjectNodeRiskDO item, UserDO owner) {
        NodeRiskDTO dto = new NodeRiskDTO();
        dto.setId(item.getId()); dto.setTitle(item.getTitle()); dto.setLevel(item.getLevel()); dto.setOwnerId(item.getOwnerId());
        dto.setOwnerName(Convertors.userDisplayName(owner)); dto.setOwnerAvatar(owner == null ? null : owner.getAvatar());
        dto.setResponse(item.getResponse()); dto.setStatus(item.getStatus()); dto.setSort(item.getSort());
        return dto;
    }

    private NodeIterationPlanDTO toIterationPlanDTO(ProjectNodeIterationPlanDO item, UserDO owner) {
        NodeIterationPlanDTO dto = new NodeIterationPlanDTO();
        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setOwnerId(item.getOwnerId());
        dto.setOwnerName(Convertors.userDisplayName(owner));
        dto.setGoal(item.getGoal());
        dto.setStatus(item.getStatus());
        dto.setStartDate(item.getStartDate());
        dto.setDueDate(item.getDueDate());
        dto.setSort(item.getSort());
        return dto;
    }

    private ProjectNodePlanBaselineDO findBaseline(Long projectId, Long nodeId) {
        return baselineMapper.selectOne(new LambdaQueryWrapper<ProjectNodePlanBaselineDO>()
                .eq(ProjectNodePlanBaselineDO::getProjectId, projectId)
                .eq(ProjectNodePlanBaselineDO::getNodeId, nodeId));
    }

    private ProjectNodeSolutionDecisionDO confirmedSolutionDecision(Long projectId) {
        ProjectNodeSolutionDecisionDO decision = findSolutionDecision(projectId);
        if (decision == null || !"CONFIRMED".equals(decision.getStatus())) {
            throw BusinessException.error("请先确认方案决策");
        }
        return decision;
    }

    private ProjectNodeSolutionDecisionDO findSolutionDecision(Long projectId) {
        ProjectNodeDO designNode = nodeMapper.selectOne(new LambdaQueryWrapper<ProjectNodeDO>()
                .eq(ProjectNodeDO::getProjectId, projectId)
                .eq(ProjectNodeDO::getNodeKey, NodeSolutionDesignService.DESIGN_NODE_KEY));
        if (designNode == null) return null;
        return decisionMapper.selectOne(new LambdaQueryWrapper<ProjectNodeSolutionDecisionDO>()
                .eq(ProjectNodeSolutionDecisionDO::getProjectId, projectId)
                .eq(ProjectNodeSolutionDecisionDO::getNodeId, designNode.getId()));
    }

    private ProjectNodePlanBaselineDO newBaseline(Long projectId, Long nodeId) {
        ProjectNodePlanBaselineDO baseline = new ProjectNodePlanBaselineDO();
        baseline.setProjectId(projectId); baseline.setNodeId(nodeId); baseline.setStatus(0); baseline.setVersion(0);
        return baseline;
    }

    private ProjectNodeDO requirePlanNode(ProjectNodeDO node) {
        if (node == null || !PLAN_NODE_KEY.equals(node.getNodeKey())) {
            throw BusinessException.error("仅计划、资源与风险基线节点支持计划工作台");
        }
        return node;
    }

    private <T> List<T> safe(List<T> items) { return items == null ? new ArrayList<>() : items; }

    private String normalize(String value, String fallback) {
        String text = upper(value);
        return text == null ? fallback : text;
    }

    private String upper(String value) {
        String text = trim(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        String text = value == null ? null : value.trim();
        return text == null || text.isEmpty() ? null : text;
    }

    private void addIfPresent(Set<Long> ids, Long id) { if (id != null) ids.add(id); }
}
