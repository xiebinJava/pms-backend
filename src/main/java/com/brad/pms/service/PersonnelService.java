package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.common.enums.AssignmentType;
import com.brad.pms.common.enums.UserStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.UserDisableCmd;
import com.brad.pms.dto.request.UserPositionCmd;
import com.brad.pms.dto.response.PersonnelDTO;
import com.brad.pms.entity.*;
import com.brad.pms.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PersonnelService {
    private final UserMapper userMapper;
    private final UserPositionMapper userPositionMapper;
    private final OrgUnitMapper orgUnitMapper;
    private final PositionMapper positionMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final AuthService authService;
    private final OperationLogService operationLogService;

    public List<PersonnelDTO> list(String keyword) {
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(w -> w.like(UserDO::getNameZh, normalizedKeyword)
                    .or().like(UserDO::getUsername, normalizedKeyword));
        }
        wrapper.orderByAsc(UserDO::getNameZh, UserDO::getUsername);
        List<UserDO> users = userMapper.selectList(wrapper);
        return users.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public void changePrimaryPosition(Long userId, UserPositionCmd cmd) {
        UserDO user = userMapper.selectForUpdate(userId);
        if (user == null) throw BusinessException.error("用户不存在");
        OrgUnitDO org = requireOrg(cmd.getOrgUnitId());
        if (cmd.getManagerUserId() != null) requireUser(cmd.getManagerUserId());
        if (cmd.getPositionId() != null && positionMapper.selectById(cmd.getPositionId()) == null) throw BusinessException.error("岗位不存在");
        UserPositionDO old = userPositionMapper.findActivePrimary(userId);
        if (old != null) {
            old.setIsPrimary(false);
            old.setStatus("ENDED");
            old.setEndDate(LocalDate.now().minusDays(1));
            userPositionMapper.updateById(old);
        }
        UserPositionDO next = buildPosition(userId, org.getId(), cmd, AssignmentType.PRIMARY.name(), true);
        userPositionMapper.insert(next);
        operationLogService.record("USER_PRIMARY_POSITION_CHANGED", "USER", userId,
                old == null ? null : Map.of("orgUnitId", old.getOrgUnitId()), Map.of("orgUnitId", org.getId()));
    }

    @Transactional
    public void addPartTimePosition(Long userId, UserPositionCmd cmd) {
        requireUser(userId);
        OrgUnitDO org = requireOrg(cmd.getOrgUnitId());
        if (userPositionMapper.findActiveByUserAndOrg(userId, org.getId()) != null) {
            throw BusinessException.error("该员工已经归属此组织");
        }
        UserPositionDO next = buildPosition(userId, org.getId(), cmd, AssignmentType.PART_TIME.name(), false);
        userPositionMapper.insert(next);
        operationLogService.record("USER_PART_TIME_POSITION_ADDED", "USER", userId, null, Map.of("orgUnitId", org.getId()));
    }

    @Transactional
    public void removePartTimePosition(Long userId, Long positionId) {
        requireUser(userId);
        UserPositionDO position = userPositionMapper.selectById(positionId);
        if (position == null || !Objects.equals(position.getUserId(), userId)
                || Boolean.TRUE.equals(position.getIsPrimary()) || !"ACTIVE".equals(position.getStatus())) {
            throw BusinessException.error("兼职归属不存在或不可移除");
        }
        position.setStatus("ENDED");
        position.setEndDate(LocalDate.now());
        userPositionMapper.updateById(position);
        operationLogService.record("USER_PART_TIME_POSITION_REMOVED", "USER", userId,
                Map.of("positionId", positionId, "orgUnitId", position.getOrgUnitId()), null);
    }

    @Transactional
    public void disable(Long userId, UserDisableCmd cmd) {
        UserDO user = requireUser(userId);
        if (user.getSystemRole() != null && user.getSystemRole() == 1
                && userMapper.selectCount(new LambdaQueryWrapper<UserDO>().eq(UserDO::getSystemRole, 1).eq(UserDO::getStatus, UserStatus.ACTIVE.name())) <= 1) {
            throw BusinessException.forbidden("不能停用最后一名系统管理员");
        }
        user.setStatus(UserStatus.DISABLED.name());
        userMapper.updateById(user);
        List<UserPositionDO> active = userPositionMapper.findActiveByUserId(userId);
        for (UserPositionDO position : active) {
            position.setStatus("ENDED");
            position.setIsPrimary(false);
            position.setEndDate(LocalDate.now());
            userPositionMapper.updateById(position);
        }
        authService.revokeAllSessions(userId, "USER_DISABLED");
        operationLogService.record("USER_DISABLED", "USER", userId,
                Map.of("status", "ACTIVE"), Map.of("status", "DISABLED", "reason", cmd.getReason()));
    }

    private UserPositionDO buildPosition(Long userId, Long orgUnitId, UserPositionCmd cmd, String assignmentType, boolean primary) {
        UserPositionDO next = new UserPositionDO();
        next.setUserId(userId);
        next.setOrgUnitId(orgUnitId);
        next.setPositionId(cmd.getPositionId());
        next.setManagerUserId(cmd.getManagerUserId());
        next.setAssignmentType(assignmentType);
        next.setIsPrimary(primary);
        next.setStartDate(cmd.getStartDate() == null ? LocalDate.now() : cmd.getStartDate());
        next.setStatus("ACTIVE");
        return next;
    }

    private UserDO requireUser(Long id) {
        UserDO user = userMapper.selectById(id);
        if (user == null) throw BusinessException.error("用户不存在");
        return user;
    }

    private OrgUnitDO requireOrg(Long id) {
        OrgUnitDO org = id == null ? null : orgUnitMapper.selectById(id);
        if (org == null || !"ACTIVE".equals(org.getStatus())) throw BusinessException.error("组织不存在或已停用");
        return org;
    }

    private PersonnelDTO toDto(UserDO user) {
        PersonnelDTO dto = new PersonnelDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNameZh(user.getNameZh());
        dto.setDisplayName(com.brad.pms.convertor.Convertors.userDisplayName(user));
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setStatus(user.getStatus());
        List<UserPositionDO> positions = userPositionMapper.findActiveByUserId(user.getId());
        UserPositionDO primary = positions.stream().filter(p -> Boolean.TRUE.equals(p.getIsPrimary())).findFirst().orElse(null);
        if (primary != null) {
            dto.setPrimaryOrgUnitId(primary.getOrgUnitId());
            OrgUnitDO org = orgUnitMapper.selectById(primary.getOrgUnitId());
            dto.setPrimaryOrgName(org == null ? null : org.getName());
            if (primary.getPositionId() != null) {
                PositionDO position = positionMapper.selectById(primary.getPositionId());
                dto.setPrimaryPositionName(position == null ? null : position.getName());
            }
        }
        for (UserPositionDO position : positions) {
            if (!Boolean.TRUE.equals(position.getIsPrimary())) {
                OrgUnitDO org = orgUnitMapper.selectById(position.getOrgUnitId());
                if (org != null) {
                    dto.getPartTimePositionIds().add(position.getId());
                    dto.getPartTimeOrgUnitIds().add(org.getId());
                    dto.getPartTimeOrgNames().add(org.getName());
                }
            }
        }
        for (UserRoleDO grant : userRoleMapper.findLiveByUserId(user.getId())) {
            RoleDO role = roleMapper.selectById(grant.getRoleId());
            if (role != null) dto.getRoles().add(role.getName());
        }
        return dto;
    }
}
