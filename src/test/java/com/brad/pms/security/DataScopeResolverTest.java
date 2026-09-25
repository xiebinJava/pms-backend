package com.brad.pms.security;

import com.brad.pms.common.enums.DataScopeType;
import com.brad.pms.entity.RoleDO;
import com.brad.pms.entity.UserRoleDO;
import com.brad.pms.entity.UserPositionDO;
import com.brad.pms.mapper.OrgUnitMapper;
import com.brad.pms.mapper.PermissionMapper;
import com.brad.pms.mapper.RoleMapper;
import com.brad.pms.mapper.RoleOrgScopeMapper;
import com.brad.pms.mapper.UserPositionMapper;
import com.brad.pms.mapper.UserRoleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataScopeResolverTest {
    @Mock UserRoleMapper userRoleMapper;
    @Mock RoleMapper roleMapper;
    @Mock RoleOrgScopeMapper roleOrgScopeMapper;
    @Mock UserPositionMapper userPositionMapper;
    @Mock OrgUnitMapper orgUnitMapper;
    @Mock PermissionMapper permissionMapper;

    @AfterEach
    void clearContext() { UserContext.clear(); }

    @Test
    void projectReadScopeIsCompanyWideEvenWhenRoleDefaultIsSelf() {
        LoginUser user = new LoginUser(7L, "member", "成员", 0);
        UserContext.set(user);
        com.brad.pms.entity.PermissionDO permission = new com.brad.pms.entity.PermissionDO(); permission.setCode("project:read");
        when(permissionMapper.findLiveByUserId(7L)).thenReturn(List.of(permission));

        DataScopeResolver resolver = new DataScopeResolver(userRoleMapper, roleMapper, roleOrgScopeMapper, userPositionMapper, orgUnitMapper, permissionMapper);

        assertThat(resolver.resolveOrgUnitIds(user, PermissionCode.PROJECT_READ)).isEmpty();
        assertThat(resolver.hasAllCompanyScope(user, PermissionCode.PROJECT_READ)).isTrue();
    }

    @Test
    void allScopeIsExplicitAndDoesNotRelyOnAnEmptyIdList() {
        LoginUser user = new LoginUser(7L, "admin", "管理员", 0);
        UserContext.set(user);
        RoleDO role = role("PROJECT_ADMIN", DataScopeType.ALL.name());
        UserRoleDO grant = new UserRoleDO(); grant.setRoleId(11L);
        when(userRoleMapper.findLiveByUserId(7L)).thenReturn(List.of(grant));
        when(roleMapper.selectById(11L)).thenReturn(role);
        com.brad.pms.entity.PermissionDO permission = new com.brad.pms.entity.PermissionDO(); permission.setCode(PermissionCode.PROJECT_WRITE);
        when(permissionMapper.findLiveByUserId(7L)).thenReturn(List.of(permission));
        when(permissionMapper.findLiveRoleIdsByUserAndPermission(7L, PermissionCode.PROJECT_WRITE)).thenReturn(List.of(11L));

        DataScopeResolver resolver = new DataScopeResolver(userRoleMapper, roleMapper, roleOrgScopeMapper, userPositionMapper, orgUnitMapper, permissionMapper);

        assertThat(resolver.resolveOrgUnitIds(user, PermissionCode.PROJECT_WRITE)).isEmpty();
        assertThat(resolver.hasAllCompanyScope(user, PermissionCode.PROJECT_WRITE)).isTrue();
    }

    @Test
    void projectCreateSelfScopeUsesOnlyThePrimaryOrganization() {
        LoginUser user = new LoginUser(7L, "member", "成员", 0);
        RoleDO role = role("MEMBER", DataScopeType.SELF.name());
        UserRoleDO grant = new UserRoleDO(); grant.setRoleId(11L);
        when(userRoleMapper.findLiveByUserId(7L)).thenReturn(List.of(grant));
        when(roleMapper.selectById(11L)).thenReturn(role);
        com.brad.pms.entity.PermissionDO permission = new com.brad.pms.entity.PermissionDO(); permission.setCode(PermissionCode.PROJECT_CREATE);
        when(permissionMapper.findLiveByUserId(7L)).thenReturn(List.of(permission));
        when(permissionMapper.findLiveRoleIdsByUserAndPermission(7L, PermissionCode.PROJECT_CREATE)).thenReturn(List.of(11L));
        UserPositionDO primary = new UserPositionDO(); primary.setOrgUnitId(42L); primary.setIsPrimary(true);
        UserPositionDO secondary = new UserPositionDO(); secondary.setOrgUnitId(99L); secondary.setIsPrimary(false);
        when(userPositionMapper.findActiveByUserId(7L)).thenReturn(List.of(primary, secondary));

        DataScopeResolver resolver = new DataScopeResolver(userRoleMapper, roleMapper, roleOrgScopeMapper, userPositionMapper, orgUnitMapper, permissionMapper);

        assertThat(resolver.resolveProjectCreateOrgUnitIds(user, PermissionCode.PROJECT_CREATE)).containsExactly(42L);
    }

    @Test
    void resolverUsesSuppliedUserRoleInsteadOfStaleThreadContextForScopedWrites() {
        UserContext.set(new LoginUser(99L, "admin", "管理员", 1));
        LoginUser member = new LoginUser(7L, "member", "成员", 0);
        RoleDO role = role("MEMBER", DataScopeType.SELF.name());
        UserRoleDO grant = new UserRoleDO(); grant.setRoleId(11L);
        when(userRoleMapper.findLiveByUserId(7L)).thenReturn(List.of(grant));
        when(roleMapper.selectById(11L)).thenReturn(role);
        com.brad.pms.entity.PermissionDO permission = new com.brad.pms.entity.PermissionDO(); permission.setCode(PermissionCode.PROJECT_WRITE);
        when(permissionMapper.findLiveByUserId(7L)).thenReturn(List.of(permission));
        when(permissionMapper.findLiveRoleIdsByUserAndPermission(7L, PermissionCode.PROJECT_WRITE)).thenReturn(List.of(11L));

        DataScopeResolver resolver = new DataScopeResolver(userRoleMapper, roleMapper, roleOrgScopeMapper, userPositionMapper, orgUnitMapper, permissionMapper);

        assertThat(resolver.hasAllCompanyScope(member, PermissionCode.PROJECT_WRITE)).isFalse();
    }

    private RoleDO role(String code, String scope) {
        RoleDO role = new RoleDO(); role.setId(11L); role.setCode(code); role.setDataScopeType(scope); role.setEnabled(true); return role;
    }
}
