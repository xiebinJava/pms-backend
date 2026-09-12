package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
import com.brad.pms.common.enums.NodeStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodeReleaseUpdateCmd;
import com.brad.pms.dto.response.NodeReleaseDTO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeReleaseBaselineDO;
import com.brad.pms.mapper.ProjectNodeReleaseBaselineMapper;
import com.brad.pms.security.UserContext;
import com.brad.pms.workflow.WorkflowComponentKey;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NodeReleaseService {

    private static final Set<String> RELEASE_TYPES = Set.of("FULL", "GRAY", "HOTFIX");
    private static final Set<String> DECISION_RESULTS = Set.of("PENDING", "APPROVED", "DEFERRED", "CANCELLED");

    private final ProjectNodeReleaseBaselineMapper baselineMapper;
    private final ProjectPermissionService permissionService;
    private final OperationLogService operationLogService;

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

    /** Lifecycle completion guard; task progress never replaces these manual release controls. */
    public void requireCompleted(Long projectId, Long nodeId) {
        permissionService.requireProjectReadable(projectId);
        ProjectNodeDO node = requireReleaseNode(permissionService.requireNode(projectId, nodeId));
        validateComplete(toDTO(node, findBaseline(projectId, nodeId)));
    }

    private void validatePayload(NodeReleaseUpdateCmd cmd) {
        if (cmd == null) throw BusinessException.error("发布决策与运营交接内容不能为空");
        if (cmd.getReleaseWindowStart() != null && cmd.getReleaseWindowEnd() != null
                && cmd.getReleaseWindowStart().isAfter(cmd.getReleaseWindowEnd())) {
            throw BusinessException.error("发布窗口开始时间不能晚于结束时间");
        }
        String releaseType = normalize(cmd.getReleaseType(), "GRAY");
        if (!RELEASE_TYPES.contains(releaseType)) throw BusinessException.error("发布类型不合法");
        String decisionResult = normalize(cmd.getDecisionResult(), "PENDING");
        if (!DECISION_RESULTS.contains(decisionResult)) throw BusinessException.error("发布决策不合法");
        cmd.setReleaseVersion(trim(cmd.getReleaseVersion()));
        cmd.setReleaseType(releaseType);
        cmd.setDecisionResult(decisionResult);
        cmd.setDecisionNote(trim(cmd.getDecisionNote()));
        cmd.setHandoverNotes(trim(cmd.getHandoverNotes()));
        cmd.setObservationItems(trim(cmd.getObservationItems()));
        cmd.setEmergencyContact(trim(cmd.getEmergencyContact()));
    }

    private void validateComplete(NodeReleaseDTO current) {
        if (trim(current.getReleaseVersion()) == null) throw BusinessException.error("请填写发布版本");
        if (current.getReleaseWindowStart() == null || current.getReleaseWindowEnd() == null) {
            throw BusinessException.error("请填写完整发布窗口");
        }
        if (current.getReleaseWindowStart().isAfter(current.getReleaseWindowEnd())) {
            throw BusinessException.error("发布窗口开始时间不能晚于结束时间");
        }
        if (!RELEASE_TYPES.contains(normalize(current.getReleaseType(), ""))) {
            throw BusinessException.error("请先选择发布类型");
        }
        if (!"APPROVED".equals(normalize(current.getDecisionResult(), "PENDING"))) {
            throw BusinessException.error("请先确认发布决策为同意发布");
        }
        if (trim(current.getHandoverNotes()) == null
                || trim(current.getObservationItems()) == null
                || trim(current.getEmergencyContact()) == null) {
            throw BusinessException.error("请完善运营交接信息");
        }
    }

    private void apply(ProjectNodeReleaseBaselineDO baseline, NodeReleaseUpdateCmd cmd) {
        baseline.setReleaseVersion(cmd.getReleaseVersion());
        baseline.setReleaseWindowStart(cmd.getReleaseWindowStart());
        baseline.setReleaseWindowEnd(cmd.getReleaseWindowEnd());
        baseline.setReleaseType(cmd.getReleaseType());
        baseline.setPackageReady(Boolean.TRUE.equals(cmd.getPackageReady()));
        baseline.setConfigConfirmed(Boolean.TRUE.equals(cmd.getConfigConfirmed()));
        baseline.setRollbackReady(Boolean.TRUE.equals(cmd.getRollbackReady()));
        baseline.setMonitoringConfirmed(Boolean.TRUE.equals(cmd.getMonitoringConfirmed()));
        baseline.setOnCallConfirmed(Boolean.TRUE.equals(cmd.getOnCallConfirmed()));
        baseline.setDecisionResult(cmd.getDecisionResult());
        baseline.setDecisionNote(cmd.getDecisionNote());
        baseline.setHandoverNotes(cmd.getHandoverNotes());
        baseline.setObservationItems(cmd.getObservationItems());
        baseline.setEmergencyContact(cmd.getEmergencyContact());
    }

    private NodeReleaseDTO toDTO(ProjectNodeDO node, ProjectNodeReleaseBaselineDO baseline) {
        NodeReleaseDTO dto = new NodeReleaseDTO();
        dto.setProjectId(node.getProjectId());
        dto.setNodeId(node.getId());
        dto.setVersion(baseline == null ? null : baseline.getVersion());
        dto.setReleaseVersion(baseline == null ? null : baseline.getReleaseVersion());
        dto.setReleaseWindowStart(baseline == null ? null : baseline.getReleaseWindowStart());
        dto.setReleaseWindowEnd(baseline == null ? null : baseline.getReleaseWindowEnd());
        dto.setReleaseType(baseline == null ? "GRAY" : normalize(baseline.getReleaseType(), "GRAY"));
        dto.setPackageReady(baseline != null && Boolean.TRUE.equals(baseline.getPackageReady()));
        dto.setConfigConfirmed(baseline != null && Boolean.TRUE.equals(baseline.getConfigConfirmed()));
        dto.setRollbackReady(baseline != null && Boolean.TRUE.equals(baseline.getRollbackReady()));
        dto.setMonitoringConfirmed(baseline != null && Boolean.TRUE.equals(baseline.getMonitoringConfirmed()));
        dto.setOnCallConfirmed(baseline != null && Boolean.TRUE.equals(baseline.getOnCallConfirmed()));
        dto.setDecisionResult(baseline == null ? "PENDING" : normalize(baseline.getDecisionResult(), "PENDING"));
        dto.setDecisionNote(baseline == null ? null : baseline.getDecisionNote());
        dto.setHandoverNotes(baseline == null ? null : baseline.getHandoverNotes());
        dto.setObservationItems(baseline == null ? null : baseline.getObservationItems());
        dto.setEmergencyContact(baseline == null ? null : baseline.getEmergencyContact());
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
        baseline.setReleaseType("GRAY");
        baseline.setDecisionResult("PENDING");
        baseline.setVersion(0);
        baseline.setCreatedBy(UserContext.userIdOrNull());
        return baseline;
    }

    private ProjectNodeDO requireReleaseNode(ProjectNodeDO node) {
        permissionService.requireNodeComponent(node, WorkflowComponentKey.RELEASE_HANDOVER,
                "仅配置了发布交接组件的节点支持发布工作台");
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
