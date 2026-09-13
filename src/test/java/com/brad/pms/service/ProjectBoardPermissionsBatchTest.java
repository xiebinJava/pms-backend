package com.brad.pms.service;

import com.brad.pms.entity.ProjectDO;
import com.brad.pms.mapper.*;
import com.brad.pms.security.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProjectBoardPermissionsBatchTest {
    @AfterEach void cleanup() { UserContext.clear(); }

    @Test
    void resolvesPermissionsOnceForManyProjectsAndPreservesResourceRules() {
        var user = new LoginUser(7L, "reader", "Reader", 0);
        UserContext.set(user);
        var scope = mock(DataScopeResolver.class);
        var authorization = mock(AuthorizationService.class);
        when(authorization.has(PermissionCode.PROJECT_WRITE)).thenReturn(true);
        when(authorization.has(PermissionCode.PROJECT_MANAGE)).thenReturn(true);
        when(authorization.has(PermissionCode.PROJECT_COMMENT_WRITE)).thenReturn(true);
        when(scope.resolveOrgUnitIds(user, PermissionCode.PROJECT_WRITE)).thenReturn(List.of(11L));
        when(scope.resolveOrgUnitIds(user, PermissionCode.PROJECT_MANAGE)).thenReturn(List.of(12L));
        var service = new ProjectPermissionService(mock(ProjectMapper.class), mock(ProjectMemberMapper.class),
                mock(ProjectNodeMapper.class), scope, authorization, mock(WorkflowTemplateService.class));
        var writable = project(1L, 11L, 1);
        var manageable = project(2L, 12L, 1);
        var outsider = project(3L, 13L, 1);
        var creator = project(4L, 13L, 1);
        creator.setCreatedBy(7L);
        var terminal = project(5L, 12L, 3);
        var result = service.projectPermissionsBatch(List.of(writable, manageable, outsider, creator, terminal));
        assertThat(result.get(1L).isCanManageProject()).isTrue();
        assertThat(result.get(1L).isCanManageMembers()).isFalse();
        assertThat(result.get(2L).isCanManageProject()).isFalse();
        assertThat(result.get(2L).isCanManageMembers()).isTrue();
        assertThat(result.get(3L).isCanManageProject()).isFalse();
        assertThat(result.get(3L).isCanWriteComment()).isTrue();
        assertThat(result.get(4L).isCanManageMembers()).isTrue();
        assertThat(result.get(5L).isCanRestoreProject()).isTrue();
        assertThat(result.get(5L).isCanManageMembers()).isFalse();
        assertThat(result.get(5L).isCanWriteComment()).isFalse();
        verify(authorization).has(PermissionCode.PROJECT_WRITE);
        verify(authorization).has(PermissionCode.PROJECT_MANAGE);
        verify(authorization).has(PermissionCode.PROJECT_COMMENT_WRITE);
        verify(scope).resolveOrgUnitIds(user, PermissionCode.PROJECT_WRITE);
        verify(scope).resolveOrgUnitIds(user, PermissionCode.PROJECT_MANAGE);
        clearInvocations(scope, authorization);
        assertThat(service.projectPermissionsBatch(List.of())).isEmpty();
        verifyNoInteractions(scope, authorization);
    }

    private static ProjectDO project(Long id, Long orgId, int status) {
        var project = new ProjectDO();
        project.setId(id);
        project.setOrgUnitId(orgId);
        project.setStatus(status);
        return project;
    }
}
