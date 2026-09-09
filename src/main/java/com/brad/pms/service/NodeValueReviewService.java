package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
import com.brad.pms.common.enums.NodeStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodeValueReviewUpdateCmd;
import com.brad.pms.dto.response.NodeValueReviewDTO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeValueReviewDO;
import com.brad.pms.mapper.ProjectNodeValueReviewMapper;
import com.brad.pms.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NodeValueReviewService {

    public static final String REVIEW_NODE_KEY = "review";
    private static final Set<String> RESULT_STATUSES = Set.of("PENDING", "ACHIEVED", "PARTIAL", "NOT_ACHIEVED");

    private final ProjectNodeValueReviewMapper reviewMapper;
    private final ProjectPermissionService permissionService;
    private final OperationLogService operationLogService;

    public NodeValueReviewDTO get(Long projectId, Long nodeId) {
        permissionService.requireProjectReadable(projectId);
        ProjectNodeDO node = requireReviewNode(permissionService.requireNode(projectId, nodeId));
        return toDTO(node, findReview(projectId, nodeId));
    }

    @Transactional
    public NodeValueReviewDTO save(Long projectId, Long nodeId, NodeValueReviewUpdateCmd cmd) {
        ProjectNodeDO node = requireReviewNode(permissionService.requireManageableNode(
                projectId, nodeId, "保存价值验证与项目复盘"));
        validatePayload(cmd);

        ProjectNodeValueReviewDO review = findReview(projectId, nodeId);
        if (review == null) {
            review = newReview(projectId, nodeId);
            try {
                reviewMapper.insert(review);
            } catch (DuplicateKeyException ex) {
                throw BusinessException.conflict("价值验证与项目复盘已被其他人创建，请刷新后重试");
            }
        } else {
            if (NodeStatus.isReadOnly(node.getStatus())) {
                throw BusinessException.conflict("价值验证与项目复盘已锁定，请回滚后再编辑");
            }
            if (cmd.getVersion() == null || !Objects.equals(cmd.getVersion(), review.getVersion())) {
                throw BusinessException.conflict("价值验证与项目复盘已被其他人修改，请刷新后重试");
            }
        }

        apply(review, cmd);
        if (reviewMapper.updateById(review) != 1) {
            throw BusinessException.conflict("价值验证与项目复盘已被其他人修改，请刷新后重试");
        }
        NodeValueReviewDTO result = toDTO(node, review);
        operationLogService.record(AuditEvent.success(
                AuditAction.NODE_VALUE_REVIEW_DRAFT_SAVED.name(), AuditResourceType.PROJECT_NODE.name(),
                nodeId, projectId, null, null, result));
        return result;
    }

    /** Lifecycle completion guard; the project owner records the conclusion manually. */
    public void requireCompleted(Long projectId, Long nodeId) {
        permissionService.requireProjectReadable(projectId);
        ProjectNodeDO node = requireReviewNode(permissionService.requireNode(projectId, nodeId));
        validateComplete(toDTO(node, findReview(projectId, nodeId)));
    }

    private void validatePayload(NodeValueReviewUpdateCmd cmd) {
        if (cmd == null) throw BusinessException.error("价值验证与项目复盘内容不能为空");
        String resultStatus = normalize(cmd.getResultStatus(), "PENDING");
        if (!RESULT_STATUSES.contains(resultStatus)) throw BusinessException.error("价值验证结论不合法");
        cmd.setResultStatus(resultStatus);
        cmd.setActualResult(trim(cmd.getActualResult()));
        cmd.setRetrospectiveConclusion(trim(cmd.getRetrospectiveConclusion()));
        cmd.setFollowUpActions(trim(cmd.getFollowUpActions()));
    }

    private void validateComplete(NodeValueReviewDTO current) {
        if (current == null || "PENDING".equals(normalize(current.getResultStatus(), "PENDING"))) {
            throw BusinessException.error("请先填写价值验证结论");
        }
        if (trim(current.getActualResult()) == null) {
            throw BusinessException.error("请填写实际价值结果");
        }
        if (trim(current.getRetrospectiveConclusion()) == null) {
            throw BusinessException.error("请填写项目复盘结论");
        }
    }

    private void apply(ProjectNodeValueReviewDO review, NodeValueReviewUpdateCmd cmd) {
        review.setResultStatus(cmd.getResultStatus());
        review.setActualResult(cmd.getActualResult());
        review.setRetrospectiveConclusion(cmd.getRetrospectiveConclusion());
        review.setFollowUpActions(cmd.getFollowUpActions());
    }

    private NodeValueReviewDTO toDTO(ProjectNodeDO node, ProjectNodeValueReviewDO review) {
        NodeValueReviewDTO dto = new NodeValueReviewDTO();
        dto.setProjectId(node.getProjectId());
        dto.setNodeId(node.getId());
        dto.setVersion(review == null ? null : review.getVersion());
        dto.setResultStatus(review == null ? "PENDING" : normalize(review.getResultStatus(), "PENDING"));
        dto.setActualResult(review == null ? null : review.getActualResult());
        dto.setRetrospectiveConclusion(review == null ? null : review.getRetrospectiveConclusion());
        dto.setFollowUpActions(review == null ? null : review.getFollowUpActions());
        dto.setCanEdit(!NodeStatus.isReadOnly(node.getStatus()));
        return dto;
    }

    private ProjectNodeValueReviewDO findReview(Long projectId, Long nodeId) {
        return reviewMapper.selectOne(new LambdaQueryWrapper<ProjectNodeValueReviewDO>()
                .eq(ProjectNodeValueReviewDO::getProjectId, projectId)
                .eq(ProjectNodeValueReviewDO::getNodeId, nodeId));
    }

    private ProjectNodeValueReviewDO newReview(Long projectId, Long nodeId) {
        ProjectNodeValueReviewDO review = new ProjectNodeValueReviewDO();
        review.setProjectId(projectId);
        review.setNodeId(nodeId);
        review.setResultStatus("PENDING");
        review.setVersion(0);
        review.setCreatedBy(UserContext.userIdOrNull());
        return review;
    }

    private ProjectNodeDO requireReviewNode(ProjectNodeDO node) {
        if (node == null || !REVIEW_NODE_KEY.equals(node.getNodeKey())) {
            throw BusinessException.error("仅价值验证与项目复盘节点支持价值复盘工作台");
        }
        return node;
    }

    private String normalize(String value, String fallback) {
        String text = trim(value);
        return text == null ? fallback : text.toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        String text = value == null ? null : value.trim();
        return text == null || text.isEmpty() ? null : text;
    }
}
