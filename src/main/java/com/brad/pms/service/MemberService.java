package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.response.ProjectMemberDTO;
import com.brad.pms.entity.ProjectMemberDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;
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
        Long exists = memberMapper.selectCount(
                new LambdaQueryWrapper<ProjectMemberDO>()
                        .eq(ProjectMemberDO::getProjectId, projectId)
                        .eq(ProjectMemberDO::getUserId, userId));
        if (exists != null && exists > 0) {
            throw BusinessException.error("该用户已在项目中");
        }
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
        permissionService.requireProjectManageable(projectId, "维护项目成员");
        List<Long> selected = userIds == null ? Collections.emptyList() : userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ownerId != null && !selected.contains(ownerId)) selected.add(ownerId);

        List<ProjectMemberDO> existing = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMemberDO>().eq(ProjectMemberDO::getProjectId, projectId));
        existing.stream()
                .filter(member -> !selected.contains(member.getUserId()))
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
                member = new ProjectMemberDO();
                member.setProjectId(projectId);
                member.setUserId(userId);
                member.setRole(Objects.equals(userId, ownerId) ? 0 : 2);
                memberMapper.insert(member);
                operationLogService.record(AuditEvent.success(
                        AuditAction.PROJECT_MEMBER_ADDED.name(), AuditResourceType.PROJECT_MEMBER.name(), member.getId(), projectId,
                        null, null, java.util.Map.of("userId", userId, "role", member.getRole())));
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
