package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
import com.brad.pms.common.enums.MemberRole;
import com.brad.pms.common.enums.UserStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.response.ProjectMemberDTO;
import com.brad.pms.entity.ProjectMemberDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final ProjectMemberMapper memberMapper;
    private final UserService userService;
    private final ProjectPermissionService permissionService;
    private final OperationLogService operationLogService;

    public List<ProjectMemberDTO> list(Long projectId) {
        permissionService.requireProject(projectId);
        List<ProjectMemberDO> members = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMemberDO>()
                        .eq(ProjectMemberDO::getProjectId, projectId)
                        .orderByAsc(ProjectMemberDO::getRole));
        Map<Long, UserDO> userMap = userService.listByIds(
                        members.stream().map(ProjectMemberDO::getUserId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(UserDO::getId, u -> u));
        return members.stream()
                .map(m -> Convertors.toMember(m, userMap.get(m.getUserId())))
                .collect(Collectors.toList());
    }

    public ProjectMemberDO add(Long projectId, Long userId, int role) {
        permissionService.requireProjectManageable(projectId, "维护项目成员");
        if (userId == null) {
            throw BusinessException.error("用户不能为空");
        }
        if (findMember(projectId, userId) != null) {
            throw BusinessException.error("该用户已在项目中");
        }
        return insertMember(projectId, userId, role);
    }

    /**
     * Adds an active account to the project when a person picker assigns them.
     * Callers must already have authorized the assignment itself.
     */
    public ProjectMemberDO ensureMember(Long projectId, Long userId) {
        if (userId == null) return null;
        ProjectMemberDO existing = findMember(projectId, userId);
        if (existing != null) return existing;
        return insertMember(projectId, userId, MemberRole.MEMBER.getCode());
    }

    public void ensureMembers(Long projectId, Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return;
        userIds.stream().filter(Objects::nonNull).distinct().forEach(id -> ensureMember(projectId, id));
    }

    private ProjectMemberDO findMember(Long projectId, Long userId) {
        return memberMapper.selectOne(new LambdaQueryWrapper<ProjectMemberDO>()
                .eq(ProjectMemberDO::getProjectId, projectId)
                .eq(ProjectMemberDO::getUserId, userId)
                .last("LIMIT 1"));
    }

    private ProjectMemberDO insertMember(Long projectId, Long userId, int role) {
        requireActiveUser(userId);
        ProjectMemberDO member = new ProjectMemberDO();
        member.setProjectId(projectId);
        member.setUserId(userId);
        member.setRole(role);
        memberMapper.insert(member);
        operationLogService.record(AuditEvent.success(
                AuditAction.PROJECT_MEMBER_ADDED.name(), AuditResourceType.PROJECT_MEMBER.name(), member.getId(), projectId,
                null, null, java.util.Map.of("userId", userId, "role", role)));
        return member;
    }

    private void requireActiveUser(Long userId) {
        UserDO user = userService.listByIds(List.of(userId)).stream().findFirst().orElse(null);
        if (user == null) throw BusinessException.notFound("账号不存在");
        if (!UserStatus.ACTIVE.name().equals(user.getStatus())) {
            throw BusinessException.error("只能选择已激活的账号");
        }
    }

    public void remove(Long projectId, Long memberId) {
        permissionService.requireProjectManageable(projectId, "维护项目成员");
        ProjectMemberDO member = memberMapper.selectById(memberId);
        if (member == null || !member.getProjectId().equals(projectId)) {
            throw BusinessException.error("成员不存在");
        }
        if (member.getRole() == 0) {
            throw BusinessException.error("项目负责人不可移除");
        }
        memberMapper.deleteById(memberId);
        operationLogService.record(AuditEvent.success(
                AuditAction.PROJECT_MEMBER_REMOVED.name(), AuditResourceType.PROJECT_MEMBER.name(), memberId, projectId,
                null, java.util.Map.of("userId", member.getUserId(), "role", member.getRole()), null));
    }

    public void replace(Long projectId, Long ownerId, List<Long> userIds) {
        replace(projectId, ownerId, userIds, null);
    }

    /**
     * Replaces the editable member set. When {@code baselineUserIds} is present, only
     * people in that snapshot can be removed, so a concurrent assignment can still
     * auto-join someone who was not on the form yet.
     */
    public void replace(Long projectId, Long ownerId, List<Long> userIds, Collection<Long> baselineUserIds) {
        permissionService.requireProjectManageable(projectId, "维护项目成员");
        List<Long> selected = userIds == null ? Collections.emptyList() : userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ownerId != null && !selected.contains(ownerId)) selected.add(ownerId);
        Collection<Long> removable = baselineUserIds == null ? null : baselineUserIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<ProjectMemberDO> existing = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMemberDO>().eq(ProjectMemberDO::getProjectId, projectId));
        existing.stream()
                .filter(member -> !selected.contains(member.getUserId()))
                .filter(member -> removable == null || removable.contains(member.getUserId()))
                .forEach(member -> {
                    memberMapper.deleteById(member.getId());
                    operationLogService.record(AuditEvent.success(
                            AuditAction.PROJECT_MEMBER_REMOVED.name(), AuditResourceType.PROJECT_MEMBER.name(), member.getId(), projectId,
                            null, java.util.Map.of("userId", member.getUserId(), "role", member.getRole()), null));
                });

        for (Long userId : selected) {
            ProjectMemberDO member = existing.stream()
                    .filter(item -> Objects.equals(item.getUserId(), userId))
                    .findFirst()
                    .orElse(null);
            if (member == null) {
                insertMember(projectId, userId, Objects.equals(userId, ownerId) ? 0 : 2);
            } else if (Objects.equals(userId, ownerId) && !Objects.equals(member.getRole(), 0)) {
                int previousRole = member.getRole();
                member.setRole(0);
                memberMapper.updateById(member);
                operationLogService.record(AuditEvent.success(
                        AuditAction.PROJECT_MEMBER_ROLE_CHANGED.name(), AuditResourceType.PROJECT_MEMBER.name(), member.getId(), projectId,
                        null, java.util.Map.of("userId", userId, "role", previousRole),
                        java.util.Map.of("userId", userId, "role", member.getRole())));
            }
        }
    }
}
