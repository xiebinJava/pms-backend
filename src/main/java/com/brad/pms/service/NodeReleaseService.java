package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
import com.brad.pms.common.enums.NodeStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.request.NodeReleaseUpdateCmd;
import com.brad.pms.dto.response.NodeReleaseDTO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeReleaseBaselineDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectNodeReleaseBaselineMapper;
import com.brad.pms.security.UserContext;
import com.brad.pms.workflow.WorkflowComponentKey;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class NodeReleaseService {

    private final ProjectNodeReleaseBaselineMapper baselineMapper;
    private final ProjectPermissionService permissionService;
    private final OperationLogService operationLogService;
    private final UserService userService;

    public NodeReleaseDTO get(Long projectId, Long nodeId) {
        permissionService.requireProjectReadable(projectId);
        ProjectNodeDO node = requireReleaseNode(permissionService.requireNode(projectId, nodeId));
        return toDTO(node, findBaseline(projectId, nodeId));
    }

    @Transactional
    public NodeReleaseDTO save(Long projectId, Long nodeId, NodeReleaseUpdateCmd cmd) {
        ProjectNodeDO node = requireReleaseNode(permissionService.requireManageableNode(
                projectId, nodeId, "保存发布决策与运营交接"));
        validatePayload(cmd);

        ProjectNodeReleaseBaselineDO baseline = findBaseline(projectId, nodeId);
        if (baseline == null) {
            baseline = newBaseline(projectId, nodeId);
            try {
                baselineMapper.insert(baseline);
            } catch (DuplicateKeyException ex) {
                throw BusinessException.conflict("发布决策与运营交接已被其他人创建，请刷新后重试");
            }
        } else {
            if (NodeStatus.isReadOnly(node.getStatus())) {
                throw BusinessException.conflict("发布决策与运营交接已锁定，请回滚后再编辑");
            }
            if (cmd.getVersion() == null || !Objects.equals(cmd.getVersion(), baseline.getVersion())) {
                throw BusinessException.conflict("发布决策与运营交接已被其他人修改，请刷新后重试");
            }
        }

        apply(baseline, cmd);
        if (baselineMapper.updateById(baseline) != 1) {
            throw BusinessException.conflict("发布决策与运营交接已被其他人修改，请刷新后重试");
        }
        NodeReleaseDTO result = toDTO(node, baseline);
        operationLogService.record(AuditEvent.success(
                AuditAction.NODE_RELEASE_DRAFT_SAVED.name(), AuditResourceType.PROJECT_NODE.name(),
                nodeId, projectId, null, null, result));
        return result;
    }

    /** Lifecycle completion guard; the project release node requires an owner and handover notes. */
    public void requireCompleted(Long projectId, Long nodeId) {
        permissionService.requireProjectReadable(projectId);
        ProjectNodeDO node = requireReleaseNode(permissionService.requireNode(projectId, nodeId));
        validateComplete(toDTO(node, findBaseline(projectId, nodeId)));
    }

    private void validatePayload(NodeReleaseUpdateCmd cmd) {
        if (cmd == null) throw BusinessException.error("发布决策与运营交接内容不能为空");
        if (cmd.getHandoverOwnerId() != null) userService.requireActiveUser(cmd.getHandoverOwnerId());
        cmd.setHandoverNotes(trim(cmd.getHandoverNotes()));
    }

    private void validateComplete(NodeReleaseDTO current) {
        if (current.getHandoverOwnerId() == null) throw BusinessException.error("请选择交接人");
        if (trim(current.getHandoverNotes()) == null) throw BusinessException.error("请填写交接说明");
    }

    private void apply(ProjectNodeReleaseBaselineDO baseline, NodeReleaseUpdateCmd cmd) {
        baseline.setHandoverOwnerId(cmd.getHandoverOwnerId());
        baseline.setHandoverNotes(cmd.getHandoverNotes());
    }

    private NodeReleaseDTO toDTO(ProjectNodeDO node, ProjectNodeReleaseBaselineDO baseline) {
        NodeReleaseDTO dto = new NodeReleaseDTO();
        dto.setProjectId(node.getProjectId());
        dto.setNodeId(node.getId());
        dto.setVersion(baseline == null ? null : baseline.getVersion());
        Long handoverOwnerId = baseline == null ? null : baseline.getHandoverOwnerId();
        UserDO handoverOwner = findUser(handoverOwnerId);
        dto.setHandoverOwnerId(handoverOwnerId);
        dto.setHandoverOwnerName(Convertors.userDisplayName(handoverOwner));
        dto.setHandoverOwnerUsername(handoverOwner == null ? null : handoverOwner.getUsername());
        dto.setHandoverNotes(baseline == null ? null : baseline.getHandoverNotes());
        dto.setCanEdit(!NodeStatus.isReadOnly(node.getStatus()));
        return dto;
    }

    private ProjectNodeReleaseBaselineDO findBaseline(Long projectId, Long nodeId) {
        return baselineMapper.selectOne(new LambdaQueryWrapper<ProjectNodeReleaseBaselineDO>()
                .eq(ProjectNodeReleaseBaselineDO::getProjectId, projectId)
                .eq(ProjectNodeReleaseBaselineDO::getNodeId, nodeId));
    }

    private ProjectNodeReleaseBaselineDO newBaseline(Long projectId, Long nodeId) {
        ProjectNodeReleaseBaselineDO baseline = new ProjectNodeReleaseBaselineDO();
        baseline.setProjectId(projectId);
        baseline.setNodeId(nodeId);
        baseline.setVersion(0);
        baseline.setCreatedBy(UserContext.userIdOrNull());
        return baseline;
    }

    private ProjectNodeDO requireReleaseNode(ProjectNodeDO node) {
        permissionService.requireNodeComponent(node, WorkflowComponentKey.RELEASE_HANDOVER,
                "仅配置了发布交接组件的节点支持发布工作台");
        return node;
    }

    private UserDO findUser(Long userId) {
        if (userId == null) return null;
        List<UserDO> users = userService.listByIds(List.of(userId));
        return users == null ? null : users.stream().findFirst().orElse(null);
    }

    private String trim(String value) {
        String text = value == null ? null : value.trim();
        return text == null || text.isEmpty() ? null : text;
    }
}
