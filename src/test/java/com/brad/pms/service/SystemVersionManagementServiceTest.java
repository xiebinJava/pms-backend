package com.brad.pms.service;

import com.brad.pms.common.enums.SystemStatus;
import com.brad.pms.common.enums.SystemVersionStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.SystemSaveCmd;
import com.brad.pms.dto.request.SystemVersionSaveCmd;
import com.brad.pms.dto.request.SystemVersionStatusUpdateCmd;
import com.brad.pms.entity.SystemDO;
import com.brad.pms.entity.SystemVersionDO;
import com.brad.pms.mapper.SystemMapper;
import com.brad.pms.mapper.SystemVersionHistoryMapper;
import com.brad.pms.mapper.SystemVersionMapper;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import com.brad.pms.security.AuthorizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemVersionManagementServiceTest {
    @Mock SystemMapper systemMapper;
    @Mock SystemVersionMapper versionMapper;
    @Mock SystemVersionHistoryMapper historyMapper;
    @Mock ProjectNodeIterationPlanMapper iterationPlanMapper;
    @Mock UserService userService;
    @Mock AuthorizationService authorizationService;
    @Mock OperationLogService operationLogService;

    @InjectMocks SystemVersionManagementService service;

    @AfterEach
    void clearContext() {
        com.brad.pms.security.UserContext.clear();
    }

    @Test
    void transitionPolicyAllowsOnlyTheDeclaredForwardAndRecoveryEdges() {
        assertThat(SystemVersionStatus.PLANNED.canTransitionTo(SystemVersionStatus.DEVELOPING)).isTrue();
        assertThat(SystemVersionStatus.DEVELOPING.canTransitionTo(SystemVersionStatus.RELEASED)).isTrue();
        assertThat(SystemVersionStatus.RELEASED.canTransitionTo(SystemVersionStatus.ARCHIVED)).isTrue();
        assertThat(SystemVersionStatus.PLANNED.canTransitionTo(SystemVersionStatus.RELEASED)).isFalse();
        assertThat(SystemVersionStatus.RELEASED.canTransitionTo(SystemVersionStatus.DEVELOPING)).isFalse();
        assertThat(SystemVersionStatus.ARCHIVED.canTransitionTo(SystemVersionStatus.PLANNED)).isFalse();
    }

    @Test
    void createsNewVersionsInPlannedStatus() {
        SystemDO system = new SystemDO();
        system.setId(1L);
        system.setStatus(SystemStatus.ACTIVE.name());
        when(systemMapper.selectByIdForUpdate(1L)).thenReturn(system);
        when(versionMapper.insert(any(SystemVersionDO.class))).thenAnswer(invocation -> {
            SystemVersionDO version = invocation.getArgument(0);
            version.setId(11L);
            return 1;
        });
        when(historyMapper.insert(any(com.brad.pms.entity.SystemVersionHistoryDO.class))).thenReturn(1);

        Long id = service.createVersion(save(1L, null));

        assertThat(id).isEqualTo(11L);
        var created = org.mockito.ArgumentCaptor.forClass(SystemVersionDO.class);
        verify(versionMapper).insert(created.capture());
        assertThat(created.getValue().getStatus()).isEqualTo(SystemVersionStatus.PLANNED.name());
    }

    @Test
    void createsSystemsWithoutAUserProvidedCode() {
        SystemSaveCmd cmd = new SystemSaveCmd();
        cmd.setName("支付系统");
        when(systemMapper.insert(any(SystemDO.class))).thenAnswer(invocation -> {
            SystemDO system = invocation.getArgument(0);
            system.setId(21L);
            return 1;
        });

        Long id = service.createSystem(cmd);

        assertThat(id).isEqualTo(21L);
        var created = org.mockito.ArgumentCaptor.forClass(SystemDO.class);
        verify(systemMapper).insert(created.capture());
        assertThat(created.getValue().getName()).isEqualTo("支付系统");
    }

    @Test
    void rejectsAStaleVersionBeforeEditingTheVersion() {
        SystemVersionDO version = version(10L, SystemVersionStatus.PLANNED, 4);
        when(versionMapper.selectByIdForUpdate(10L)).thenReturn(version);

        SystemVersionSaveCmd cmd = save(1L, 3);

        assertThatThrownBy(() -> service.updateVersion(10L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("系统版本已被其他人修改")
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(BusinessException.ResponseCode.CONFLICT);
        verify(versionMapper, never()).updateById(any(SystemVersionDO.class));
    }

    @Test
    void rejectsEditingReleasedAndArchivedVersions() {
        SystemVersionDO version = version(10L, SystemVersionStatus.RELEASED, 0);
        when(versionMapper.selectByIdForUpdate(10L)).thenReturn(version);

        assertThatThrownBy(() -> service.updateVersion(10L, save(1L, 0)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可编辑");
        verify(versionMapper, never()).updateById(any(SystemVersionDO.class));

        version.setStatus(SystemVersionStatus.ARCHIVED.name());
        assertThatThrownBy(() -> service.updateVersion(10L, save(1L, 0)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可编辑");
    }

    @Test
    void rejectsAnInvalidStatusTransitionWithoutWritingHistory() {
        SystemVersionDO version = version(10L, SystemVersionStatus.PLANNED, 0);
        when(versionMapper.selectByIdForUpdate(10L)).thenReturn(version);
        SystemVersionStatusUpdateCmd cmd = status("RELEASED", 0, "提前发布");

        assertThatThrownBy(() -> service.changeVersionStatus(10L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态不允许");
        verify(versionMapper, never()).updateById(any(SystemVersionDO.class));
        verify(historyMapper, never()).insert(any(com.brad.pms.entity.SystemVersionHistoryDO.class));
    }

    @Test
    void publishingSetsReleasedAtAndAppendsHistory() {
        SystemVersionDO version = version(10L, SystemVersionStatus.DEVELOPING, 0);
        when(versionMapper.selectByIdForUpdate(10L)).thenReturn(version);
        when(versionMapper.updateById(version)).thenReturn(1);
        when(historyMapper.insert(any(com.brad.pms.entity.SystemVersionHistoryDO.class))).thenReturn(1);
        SystemDO system = new SystemDO();
        system.setId(1L);
        system.setName("支付系统");
        when(systemMapper.selectById(1L)).thenReturn(system);

        SystemVersionStatusUpdateCmd cmd = status("RELEASED", 0, "验收通过");
        var result = service.changeVersionStatus(10L, cmd);

        assertThat(version.getStatus()).isEqualTo(SystemVersionStatus.RELEASED.name());
        assertThat(version.getReleasedAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(SystemVersionStatus.RELEASED.name());
        verify(historyMapper).insert(any(com.brad.pms.entity.SystemVersionHistoryDO.class));
        verify(operationLogService).record(any(com.brad.pms.audit.AuditEvent.class));
    }

    @Test
    void refusesToCreateAVersionForAnInactiveSystem() {
        SystemDO system = new SystemDO();
        system.setId(1L);
        system.setStatus(SystemStatus.INACTIVE.name());
        when(systemMapper.selectByIdForUpdate(1L)).thenReturn(system);

        assertThatThrownBy(() -> service.createVersion(save(1L, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已停用");
        verify(versionMapper, never()).insert(any(SystemVersionDO.class));
    }

    @Test
    void deletesAnUnreferencedVersionAndItsCascadedHistory() {
        SystemVersionDO version = version(10L, SystemVersionStatus.PLANNED, 0);
        when(versionMapper.selectByIdForUpdate(10L)).thenReturn(version);
        when(iterationPlanMapper.selectCount(any())).thenReturn(0L);
        when(versionMapper.deleteById(10L)).thenReturn(1);

        service.deleteVersion(10L);

        verify(versionMapper).deleteById(10L);
        verify(historyMapper, never()).delete(any());
    }

    @Test
    void refusesToDeleteAVersionReferencedByAnIterationPlan() {
        SystemVersionDO version = version(10L, SystemVersionStatus.PLANNED, 0);
        when(versionMapper.selectByIdForUpdate(10L)).thenReturn(version);
        when(iterationPlanMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteVersion(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被迭代计划使用");
        verify(versionMapper, never()).deleteById(10L);
    }

    private static SystemVersionDO version(Long id, SystemVersionStatus status, int lockVersion) {
        SystemVersionDO version = new SystemVersionDO();
        version.setId(id);
        version.setSystemId(1L);
        version.setVersionNo("1.0.0");
        version.setVersionName("首版");
        version.setStatus(status.name());
        version.setVersion(lockVersion);
        return version;
    }

    private static SystemVersionSaveCmd save(Long systemId, Integer version) {
        SystemVersionSaveCmd cmd = new SystemVersionSaveCmd();
        cmd.setSystemId(systemId);
        cmd.setVersionNo("1.0.1");
        cmd.setVersionName("修订版");
        cmd.setVersion(version);
        return cmd;
    }

    private static SystemVersionStatusUpdateCmd status(String status, Integer version, String reason) {
        SystemVersionStatusUpdateCmd cmd = new SystemVersionStatusUpdateCmd();
        cmd.setStatus(status);
        cmd.setVersion(version);
        cmd.setReason(reason);
        return cmd;
    }
}
