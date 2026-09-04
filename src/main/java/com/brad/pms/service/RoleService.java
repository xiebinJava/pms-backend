package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
import com.brad.pms.common.enums.DataScopeType;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.RoleSaveCmd;
import com.brad.pms.dto.response.RoleDTO;
import com.brad.pms.entity.PermissionDO;
import com.brad.pms.entity.RoleDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.entity.UserRoleDO;
import com.brad.pms.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final RoleOrgScopeMapper roleOrgScopeMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserMapper userMapper;
    private final OrgUnitMapper orgUnitMapper;
    private final OperationLogService operationLogService;

    public List<RoleDTO> list() {
        return roleMapper.selectList(new LambdaQueryWrapper<RoleDO>().orderByAsc(RoleDO::getCode)).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public RoleDTO create(RoleSaveCmd cmd) {
        String normalizedCode = normalizeCode(cmd.getCode());
        if (cmd.getName() == null || cmd.getName().isBlank()) throw BusinessException.error("角色名称不能为空");
        if (roleMapper.findByCode(normalizedCode) != null) throw BusinessException.error("角色编码已存在");
        RoleDO role = new RoleDO();
        role.setCode(normalizedCode);
        role.setName(cmd.getName());
        role.setBuiltin(false);
        role.setDataScopeType(validateScope(cmd.getDataScopeType()));
        role.setEnabled(!Boolean.FALSE.equals(cmd.getEnabled()));
        roleMapper.insert(role);
        saveBindings(role, cmd);
        operationLogService.record(AuditEvent.success(
                AuditAction.ROLE_CREATED.name(), AuditResourceType.ROLE.name(), role.getId(), null, null,
                null, roleAuditSnapshot(role)));
        return toDto(role);
    }

    @Transactional
    public RoleDTO update(Long id, RoleSaveCmd cmd) {
        RoleDO role = require(id);
        Map<String, Object> before = roleAuditSnapshot(role);
        if (cmd.getName() == null || cmd.getName().isBlank()) throw BusinessException.error("角色名称不能为空");
        if (Boolean.TRUE.equals(role.getBuiltin())) {
            if (Boolean.FALSE.equals(cmd.getEnabled())) throw BusinessException.forbidden("内置角色不能停用");
            // Built-in role permissions and data scopes are migration-owned. This
            // prevents an accidental empty form submission from stripping the
            // only access path for a protected role.
            role.setName(cmd.getName());
            roleMapper.updateById(role);
            operationLogService.record(AuditEvent.success(
                    AuditAction.ROLE_UPDATED.name(), AuditResourceType.ROLE.name(), id, null, null,
                    before, roleAuditSnapshot(role)));
            return toDto(role);
        }
        role.setName(cmd.getName());
        role.setDataScopeType(validateScope(cmd.getDataScopeType()));
        role.setEnabled(!Boolean.FALSE.equals(cmd.getEnabled()));
        roleMapper.updateById(role);
        saveBindings(role, cmd);
        Map<String, Object> after = roleAuditSnapshot(role);
        if (!Objects.equals(before.get("permissionCodes"), after.get("permissionCodes"))) {
            operationLogService.record(AuditEvent.success(
                    AuditAction.ROLE_PERMISSION_CHANGED.name(), AuditResourceType.ROLE.name(), id, null, null,
                    Map.of("permissionCodes", before.get("permissionCodes")),
                    Map.of("permissionCodes", after.get("permissionCodes"))));
        }
        if (!Objects.equals(before.get("dataScopeType"), after.get("dataScopeType"))
                || !Objects.equals(before.get("customOrgUnitIds"), after.get("customOrgUnitIds"))) {
            operationLogService.record(AuditEvent.success(
                    AuditAction.ROLE_SCOPE_CHANGED.name(), AuditResourceType.ROLE.name(), id, null, null,
                    Map.of("dataScopeType", before.get("dataScopeType"), "customOrgUnitIds", before.get("customOrgUnitIds")),
                    Map.of("dataScopeType", after.get("dataScopeType"), "customOrgUnitIds", after.get("customOrgUnitIds"))));
        }
        if (!Objects.equals(before.get("name"), after.get("name"))
                || !Objects.equals(before.get("enabled"), after.get("enabled"))) {
            operationLogService.record(AuditEvent.success(
                    AuditAction.ROLE_UPDATED.name(), AuditResourceType.ROLE.name(), id, null, null, before, after));
        }
        return toDto(role);
    }

    @Transactional
    public void delete(Long id) {
        RoleDO role = require(id);
        Map<String, Object> before = roleAuditSnapshot(role);
        if (Boolean.TRUE.equals(role.getBuiltin())) throw BusinessException.forbidden("内置角色不能删除");
        if (userRoleMapper.selectCount(new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getRoleId, id).eq(UserRoleDO::getStatus, "ACTIVE")) > 0) throw BusinessException.error("角色仍被人员使用，不能删除");
        roleMapper.deleteById(id);
        operationLogService.record(AuditEvent.success(
                AuditAction.ROLE_DELETED.name(), AuditResourceType.ROLE.name(), id, null, null, before, null));
    }

    @Transactional
    public void assign(Long userId, Long roleId) {
        UserDO user = userMapper.selectById(userId);
        RoleDO role = require(roleId);
        if (user == null) throw BusinessException.error("用户不存在");
        if (!Boolean.TRUE.equals(role.getEnabled())) throw BusinessException.error("角色已停用");
        if (userRoleMapper.findLiveByUserAndRole(userId, role.getCode()).stream().findAny().isPresent()) return;
        UserRoleDO grant = new UserRoleDO();
        grant.setUserId(userId);
        grant.setRoleId(roleId);
        grant.setStartAt(LocalDateTime.now());
        grant.setStatus("ACTIVE");
        userRoleMapper.insert(grant);
        operationLogService.record(AuditEvent.success(
                AuditAction.USER_ROLE_ASSIGNED.name(), AuditResourceType.USER.name(), userId, null, null,
                null, Map.of("roleId", roleId, "roleCode", role.getCode())));
    }

    @Transactional
    public void unassign(Long userId, Long roleId) {
        UserDO user = userMapper.selectById(userId);
        RoleDO role = require(roleId);
        if (user == null) throw BusinessException.error("用户不存在");
        if ("SUPER_ADMIN".equals(role.getCode()) && userRoleMapper.countLiveByRoleCode("SUPER_ADMIN") <= 1) {
            throw BusinessException.forbidden("不能移除最后一名系统管理员");
        }
        for (UserRoleDO grant : userRoleMapper.findLiveByUserAndRole(userId, role.getCode())) {
            grant.setStatus("ENDED");
            grant.setEndAt(LocalDateTime.now());
            userRoleMapper.updateById(grant);
        }
        operationLogService.record(AuditEvent.success(
                AuditAction.USER_ROLE_UNASSIGNED.name(), AuditResourceType.USER.name(), userId, null, null,
                Map.of("roleId", roleId, "roleCode", role.getCode()), null));
    }

    private void saveBindings(RoleDO role, RoleSaveCmd cmd) {
        List<String> permissionCodes = Optional.ofNullable(cmd.getPermissionCodes()).orElse(List.of()).stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(code -> !code.isBlank())
                .distinct()
                .collect(Collectors.toList());
        if (permissionCodes.isEmpty()) throw BusinessException.error("角色至少需要绑定一个权限点");
        rolePermissionMapper.deleteByRoleId(role.getId());
        for (String code : permissionCodes) {
            PermissionDO permission = permissionMapper.selectOne(new LambdaQueryWrapper<PermissionDO>().eq(PermissionDO::getCode, code));
            if (permission == null) throw BusinessException.error("权限点不存在: " + code);
            rolePermissionMapper.insert(role.getId(), permission.getId());
        }
        roleOrgScopeMapper.deleteByRoleId(role.getId());
        if (DataScopeType.CUSTOM_ORGS.name().equals(role.getDataScopeType())) {
            List<Long> orgIds = Optional.ofNullable(cmd.getCustomOrgUnitIds()).orElse(List.of());
            if (orgIds.isEmpty()) throw BusinessException.error("自定义组织范围不能为空");
            for (Long orgId : orgIds) {
                var org = orgUnitMapper.selectById(orgId);
                if (org == null || !"ACTIVE".equals(org.getStatus())) {
                    throw BusinessException.error("自定义组织不存在或已停用: " + orgId);
                }
                roleOrgScopeMapper.insert(role.getId(), orgId);
            }
        }
    }

    private RoleDO require(Long id) {
        RoleDO role = roleMapper.selectById(id);
        if (role == null) throw BusinessException.error("角色不存在");
        return role;
    }

    private Map<String, Object> roleAuditSnapshot(RoleDO role) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", role.getCode());
        snapshot.put("name", role.getName());
        snapshot.put("builtin", role.getBuiltin());
        snapshot.put("enabled", role.getEnabled());
        snapshot.put("dataScopeType", role.getDataScopeType());
        snapshot.put("permissionCodes", permissionMapper.findByRoleId(role.getId()).stream()
                .map(PermissionDO::getCode).sorted().collect(Collectors.toList()));
        snapshot.put("customOrgUnitIds", new ArrayList<>(roleOrgScopeMapper.findOrgUnitIds(role.getId())));
        return snapshot;
    }

    private String validateScope(String scope) {
        try { return DataScopeType.valueOf(scope).name(); }
        catch (Exception e) { throw BusinessException.error("数据范围不正确"); }
    }

    private String normalizeCode(String code) {
        if (code == null) throw BusinessException.error("角色编码格式不正确");
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("^[A-Z][A-Z0-9_:-]{1,59}$")) throw BusinessException.error("角色编码格式不正确");
        return normalized;
    }

    private RoleDTO toDto(RoleDO role) {
        RoleDTO dto = new RoleDTO();
        dto.setId(role.getId());
        dto.setCode(role.getCode());
        dto.setName(role.getName());
        dto.setBuiltin(role.getBuiltin());
        dto.setDataScopeType(role.getDataScopeType());
        dto.setEnabled(role.getEnabled());
        List<PermissionDO> permissions = permissionMapper.findByRoleId(role.getId());
        dto.setPermissionCodes(permissions.stream().map(PermissionDO::getCode).collect(Collectors.toList()));
        dto.setCustomOrgUnitIds(roleOrgScopeMapper.findOrgUnitIds(role.getId()));
        return dto;
    }
}
