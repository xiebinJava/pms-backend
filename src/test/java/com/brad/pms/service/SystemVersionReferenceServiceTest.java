package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodeIterationPlanCmd;
import com.brad.pms.entity.SystemDO;
import com.brad.pms.entity.SystemVersionDO;
import com.brad.pms.mapper.SystemMapper;
import com.brad.pms.mapper.SystemVersionMapper;
import com.brad.pms.security.AuthorizationService;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemVersionReferenceServiceTest {

    @Mock SystemVersionMapper versionMapper;
    @Mock SystemMapper systemMapper;
    @Mock AuthorizationService authorizationService;
    @InjectMocks SystemVersionReferenceService service;

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void allowsNewBindingsOnlyToNonTerminalStatusesInOneBatch() {
        UserContext.set(new LoginUser(7L, "manager", "项目经理"));
        when(versionMapper.selectBatchByIdsForUpdate(anyCollection())).thenReturn(
                List.of(version(11L, "PLANNED"), version(12L, "DEVELOPING")));

        service.validateForSave(List.of(
                new SystemVersionReferenceService.Binding(11L, null),
                new SystemVersionReferenceService.Binding(12L, null)));

        verify(authorizationService).require("system-version:read");
        verify(versionMapper).selectBatchByIdsForUpdate(anyCollection());
    }

    @Test
    void rejectsReleasedAndArchivedOnlyWhenTheReferenceChanges() {
        when(versionMapper.selectBatchByIdsForUpdate(anyCollection())).thenReturn(List.of(version(11L, "RELEASED")));

        assertThatThrownBy(() -> service.validateForSave(11L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("RELEASED");

        when(versionMapper.selectBatchByIdsForUpdate(anyCollection())).thenReturn(List.of(version(12L, "ARCHIVED")));
        assertThatThrownBy(() -> service.validateForSave(12L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ARCHIVED");

        service.validateForSave(11L, 11L);
        service.validateForSave(null, 11L);
        verify(versionMapper, org.mockito.Mockito.times(2)).selectBatchByIdsForUpdate(anyCollection());
    }

    @Test
    void failsForMissingReferencesAndUsesTheLockedBatchQuery() {
        when(versionMapper.selectBatchByIdsForUpdate(anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> service.validateForSave(99L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("系统版本不存在");
        verify(versionMapper).selectBatchByIdsForUpdate(anyCollection());
    }

    @Test
    void preservesAnOmittedReferenceButTreatsExplicitNullAsClear() {
        NodeIterationPlanCmd omitted = new NodeIterationPlanCmd();
        assertThat(service.resolveForSave(omitted, 11L)).isEqualTo(11L);

        NodeIterationPlanCmd clear = new NodeIterationPlanCmd();
        clear.setSystemVersionId(null);
        assertThat(service.resolveForSave(clear, 11L)).isNull();
        verify(versionMapper, never()).selectBatchByIdsForUpdate(anyCollection());
    }

    @Test
    void loadsVersionAndSystemLabelsWithBatchReads() {
        when(versionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(version(11L, "PLANNED")));
        SystemDO system = new SystemDO();
        system.setId(3L);
        system.setName("支付系统");
        when(systemMapper.selectBatchIds(anyCollection())).thenReturn(List.of(system));

        Map<Long, SystemVersionReferenceService.Reference> result = service.load(List.of(11L));

        assertThat(result.get(11L).versionNo()).isEqualTo("1.0");
        assertThat(result.get(11L).versionName()).isEqualTo("首版");
        assertThat(result.get(11L).systemName()).isEqualTo("支付系统");
        verify(versionMapper).selectBatchIds(anyCollection());
        verify(systemMapper).selectBatchIds(anyCollection());
    }

    @Test
    void rejectsAVersionFromAnotherSystemEvenWhenItIsUnchanged() {
        when(versionMapper.selectByIdForUpdate(11L)).thenReturn(version(11L, "RELEASED"));
        assertThatThrownBy(() -> service.validateSystem(11L, 4L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("所属系统");
        service.validateSystem(11L, 3L);
    }

    private static SystemVersionDO version(Long id, String status) {
        SystemVersionDO version = new SystemVersionDO();
        version.setId(id);
        version.setSystemId(3L);
        version.setVersionNo("1.0");
        version.setVersionName("首版");
        version.setStatus(status);
        return version;
    }
}
