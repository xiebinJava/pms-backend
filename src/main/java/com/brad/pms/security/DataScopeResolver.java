package com.brad.pms.security;

import com.brad.pms.common.enums.DataScopeType;
import com.brad.pms.entity.RoleDO;
import com.brad.pms.entity.UserPositionDO;
import com.brad.pms.entity.UserRoleDO;
import com.brad.pms.mapper.OrgUnitMapper;
import com.brad.pms.mapper.PermissionMapper;
import com.brad.pms.mapper.RoleMapper;
import com.brad.pms.mapper.RoleOrgScopeMapper;
import com.brad.pms.mapper.UserPositionMapper;
import com.brad.pms.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/** Resolves organization IDs for a permission without interpolating SQL input. */
@Service
@RequiredArgsConstructor
public class DataScopeResolver {
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final RoleOrgScopeMapper roleOrgScopeMapper;
    private final UserPositionMapper userPositionMapper;
    private final OrgUnitMapper orgUnitMapper;
    private final PermissionMapper permissionMapper;

    /** Resolves organization IDs; an empty list means no organization scope. */
    public List<Long> resolveOrgUnitIds(LoginUser user, String permissionCode) {
        if (user == null) return List.of();
        if (UserContext.isAdministrator() || user.getSystemRole() != null && user.getSystemRole() == 1) {
            return List.of();
        }
        if (permissionCode != null && permissionMapper.findLiveByUserId(user.getId()).stream()
                .noneMatch(permission -> permissionCode.equals(permission.getCode()))) return List.of();
        Set<Long> permissionRoleIds = permissionCode == null ? Set.of() : new HashSet<>(permissionMapper.findLiveRoleIdsByUserAndPermission(user.getId(), permissionCode));
        Set<Long> result = new LinkedHashSet<>();
        List<UserPositionDO> positions = userPositionMapper.findActiveByUserId(user.getId());
        for (UserRoleDO grant : userRoleMapper.findLiveByUserId(user.getId())) {
            if (permissionCode != null && !permissionRoleIds.contains(grant.getRoleId())) continue;
            RoleDO role = roleMapper.selectById(grant.getRoleId());
            if (role == null || !Boolean.TRUE.equals(role.getEnabled())) continue;
            DataScopeType scope;
            try {
                scope = DataScopeType.valueOf(role.getDataScopeType());
            } catch (Exception ignored) {
                scope = DataScopeType.SELF;
            }
            if (scope == DataScopeType.ALL) return List.of();
            if (scope == DataScopeType.CUSTOM_ORGS) {
                result.addAll(roleOrgScopeMapper.findOrgUnitIds(role.getId()));
                continue;
            }
            Set<Long> ownOrgIds = new LinkedHashSet<>();
            for (UserPositionDO position : positions) {
                if (position.getOrgUnitId() != null) ownOrgIds.add(position.getOrgUnitId());
            }
            if (grant.getScopeOrgUnitId() != null) ownOrgIds.add(grant.getScopeOrgUnitId());
            if (scope == DataScopeType.SELF) {
                // SELF is enforced by the resource's owner/member rule; it
                // must not be widened to the user's organization.
            } else if (scope == DataScopeType.ORG) {
                result.addAll(ownOrgIds);
            } else if (scope == DataScopeType.ORG_AND_DESCENDANTS) {
                for (Long orgId : ownOrgIds) {
                    var org = orgUnitMapper.selectById(orgId);
                    if (org != null) result.addAll(orgUnitMapper.findDescendantIds(org.getPath(), org.getId()));
                }
            } else if (scope == DataScopeType.SELF_AND_SUBORDINATES) {
                result.addAll(ownOrgIds);
                collectReportOrgIds(user.getId(), result, new HashSet<>());
            }
        }
        return new ArrayList<>(result);
    }

    public boolean hasAllCompanyScope(LoginUser user, String permissionCode) {
        if (user == null) return false;
        if (UserContext.isAdministrator() || user.getSystemRole() != null && user.getSystemRole() == 1) return true;
        if (permissionCode != null && permissionMapper.findLiveByUserId(user.getId()).stream()
                .noneMatch(permission -> permissionCode.equals(permission.getCode()))) return false;
        Set<Long> permissionRoleIds = permissionCode == null ? Set.of() : new HashSet<>(permissionMapper.findLiveRoleIdsByUserAndPermission(user.getId(), permissionCode));
        for (UserRoleDO grant : userRoleMapper.findLiveByUserId(user.getId())) {
            if (permissionCode != null && !permissionRoleIds.contains(grant.getRoleId())) continue;
            RoleDO role = roleMapper.selectById(grant.getRoleId());
            if (role == null || !Boolean.TRUE.equals(role.getEnabled())) continue;
            try {
                if (DataScopeType.ALL == DataScopeType.valueOf(role.getDataScopeType())) return true;
            } catch (Exception ignored) {
                // Treat malformed scopes as least privilege.
            }
        }
        return false;
    }

    private void collectReportOrgIds(Long managerId, Set<Long> result, Set<Long> visitedUsers) {
        if (!visitedUsers.add(managerId)) return;
        for (UserPositionDO position : userPositionMapper.findActiveByManagerUserId(managerId)) {
            if (position.getOrgUnitId() != null) result.add(position.getOrgUnitId());
            if (position.getUserId() != null) collectReportOrgIds(position.getUserId(), result, visitedUsers);
        }
    }
}
