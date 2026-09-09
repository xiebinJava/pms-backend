package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodeReleaseUpdateCmd;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeReleaseBaselineDO;
import com.brad.pms.mapper.ProjectNodeReleaseBaselineMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeReleaseServiceTest {

    @Mock ProjectNodeReleaseBaselineMapper baselineMapper;
    @Mock ProjectPermissionService permissionService;
    @Mock OperationLogService operationLogService;

    @InjectMocks NodeReleaseService service;

    @Test
    void completionRequiresReleaseInformationAndHandoverFieldsButNotManualChecks() {
        ProjectNodeDO node = node("release");
        when(permissionService.requireProjectReadable(1L)).thenReturn(null);
        when(permissionService.requireNode(1L, 10L)).thenReturn(node);
        when(baselineMapper.selectOne(any())).thenReturn(completeBaseline());

        service.requireCompleted(1L, 10L);

        ProjectNodeReleaseBaselineDO incomplete = completeBaseline();
        incomplete.setMonitoringConfirmed(false);
        when(baselineMapper.selectOne(any())).thenReturn(incomplete);
        service.requireCompleted(1L, 10L);
    }

    @Test
    void completionRequiresApprovedDecision() {
        ProjectNodeDO node = node("release");
        when(permissionService.requireProjectReadable(1L)).thenReturn(null);
        when(permissionService.requireNode(1L, 10L)).thenReturn(node);
        ProjectNodeReleaseBaselineDO baseline = completeBaseline();
        baseline.setDecisionResult("PENDING");
        when(baselineMapper.selectOne(any())).thenReturn(baseline);

        assertThatThrownBy(() -> service.requireCompleted(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("发布决策");
    }

    @Test
    void saveRejectsReversedReleaseWindowBeforeWriting() {
        ProjectNodeDO node = node("release");
        when(permissionService.requireManageableNode(1L, 10L, "保存发布决策与运营交接")).thenReturn(node);
        NodeReleaseUpdateCmd cmd = new NodeReleaseUpdateCmd();
        cmd.setReleaseWindowStart(LocalDateTime.of(2026, 9, 26, 22, 0));
        cmd.setReleaseWindowEnd(LocalDateTime.of(2026, 9, 26, 20, 0));

        assertThatThrownBy(() -> service.save(1L, 10L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("发布窗口");
        verify(baselineMapper, never()).insert(any(ProjectNodeReleaseBaselineDO.class));
        verify(baselineMapper, never()).updateById(any(ProjectNodeReleaseBaselineDO.class));
    }

    @Test
    void saveRejectsStaleDraftVersion() {
        ProjectNodeDO node = node("release");
        when(permissionService.requireManageableNode(1L, 10L, "保存发布决策与运营交接")).thenReturn(node);
        ProjectNodeReleaseBaselineDO current = completeBaseline();
        current.setVersion(3);
        when(baselineMapper.selectOne(any())).thenReturn(current);
        NodeReleaseUpdateCmd cmd = new NodeReleaseUpdateCmd();
        cmd.setVersion(2);

        assertThatThrownBy(() -> service.save(1L, 10L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("修改");
        verify(baselineMapper, never()).updateById(any(ProjectNodeReleaseBaselineDO.class));
    }

    private ProjectNodeDO node(String key) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setNodeKey(key);
        node.setStatus(1);
        return node;
    }

    private ProjectNodeReleaseBaselineDO completeBaseline() {
        ProjectNodeReleaseBaselineDO baseline = new ProjectNodeReleaseBaselineDO();
        baseline.setProjectId(1L);
        baseline.setNodeId(10L);
        baseline.setVersion(1);
        baseline.setReleaseVersion("v2.6.0");
        baseline.setReleaseWindowStart(LocalDateTime.of(2026, 9, 26, 20, 0));
        baseline.setReleaseWindowEnd(LocalDateTime.of(2026, 9, 26, 22, 0));
        baseline.setReleaseType("GRAY");
        baseline.setPackageReady(true);
        baseline.setConfigConfirmed(true);
        baseline.setRollbackReady(true);
        baseline.setMonitoringConfirmed(true);
        baseline.setOnCallConfirmed(true);
        baseline.setDecisionResult("APPROVED");
        baseline.setHandoverNotes("运维已接收发布说明");
        baseline.setObservationItems("观察订单错误率");
        baseline.setEmergencyContact("400-000-0000");
        return baseline;
    }
}
