package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
    private final OperationLogService operationLogService;

    public List<RoleDTO> list() {
        return roleMapper.selectList(new LambdaQueryWrapper<RoleDO>().orderByAsc(RoleDO::getCode)).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public RoleDTO create(RoleSaveCmd cmd) {
        if (cmd.getCode() == null || !cmd.getCode().matches("^[A-Z][A-Z0-9_:-]{1,59}$")) throw BusinessException.error("角色编码格式不正确");
        if (roleMapper.findByCode(cmd.getCode()) != null) throw BusinessException.error("角色编码已存在");
        RoleDO role = new RoleDO();
        role.setCode(cmd.getCode());
        role.setName(cmd.getName());
        role.setBuiltin(false);
        role.setDataScopeType(validateScope(cmd.getDataScopeType()));
        role.setEnabled(!Boolean.FALSE.equals(cmd.getEnabled()));
        roleMapper.insert(role);
        saveBindings(role, cmd);
        operationLogService.record("ROLE_CREATED", "ROLE", role.getId(), null, Map.of("code", role.getCode(), "name", role.getName()));
        return toDto(role);
    }

    @Transactional
    public RoleDTO update(Long id, RoleSaveCmd cmd) {
        RoleDO role = require(id);
        if (Boolean.TRUE.equals(role.getBuiltin())) {
            if (Boolean.FALSE.equals(cmd.getEnabled())) throw BusinessException.forbidden("内置角色不能停用");
            // Built-in role permissions and data scopes are migration-owned. This
            // prevents an accidental empty form submission from stripping the
            // only access path for a protected role.
            role.setName(cmd.getName());
            roleMapper.updateById(role);
            operationLogService.record("ROLE_UPDATED", "ROLE", id, null, Map.of("name", role.getName(), "builtin", true));
            return toDto(role);
        }
        role.setName(cmd.getName());
        role.setDataScopeType(validateScope(cmd.getDataScopeType()));
        role.setEnabled(!Boolean.FALSE.equals(cmd.getEnabled()));
        roleMapper.updateById(role);
        saveBindings(role, cmd);
        operationLogService.record("ROLE_UPDATED", "ROLE", id, null, Map.of("name", role.getName(), "dataScopeType", role.getDataScopeType()));
        return toDto(role);
    }

    @Transactional
    public void delete(Long id) {
        RoleDO role = require(id);
        if (Boolean.TRUE.equals(role.getBuiltin())) throw BusinessException.forbidden("内置角色不能删除");
        if (userRoleMapper.selectCount(new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getRoleId, id).eq(UserRoleDO::getStatus, "ACTIVE")) > 0) throw BusinessException.error("角色仍被人员使用，不能删除");
        roleMapper.deleteById(id);
        operationLogService.record("ROLE_DELETED", "ROLE", id, Map.of("code", role.getCode()), null);
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
        operationLogService.record("USER_ROLE_ASSIGNED", "USER", userId, null, Map.of("roleCode", role.getCode()));
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
        operationLogService.record("USER_ROLE_UNASSIGNED", "USER", userId, Map.of("roleCode", role.getCode()), null);
    }

    private void saveBindings(RoleDO role, RoleSaveCmd cmd) {
        rolePermissionMapper.deleteByRoleId(role.getId());
        for (String code : Optional.ofNullable(cmd.getPermissionCodes()).orElse(List.of())) {
            PermissionDO permission = permissionMapper.selectOne(new LambdaQueryWrapper<PermissionDO>().eq(PermissionDO::getCode, code));
            if (permission != null) rolePermissionMapper.insert(role.getId(), permission.getId());
        }
        roleOrgScopeMapper.deleteByRoleId(role.getId());
        if (DataScopeType.CUSTOM_ORGS.name().equals(role.getDataScopeType())) {
            for (Long orgId : Optional.ofNullable(cmd.getCustomOrgUnitIds()).orElse(List.of())) roleOrgScopeMapper.insert(role.getId(), orgId);
        }
    }

    private RoleDO require(Long id) {
        RoleDO role = roleMapper.selectById(id);
        if (role == null) throw BusinessException.error("角色不存在");
        return role;
    }

    private String validateScope(String scope) {
        try { return DataScopeType.valueOf(scope).name(); }
        catch (Exception e) { throw BusinessException.error("数据范围不正确"); }
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
