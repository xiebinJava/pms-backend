package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
import com.brad.pms.entity.ProjectFollowerDO;
import com.brad.pms.mapper.ProjectFollowerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FollowerService {

    private final ProjectFollowerMapper followerMapper;
    private final UserService userService;
    private final ProjectPermissionService permissionService;
    private final OperationLogService operationLogService;

    public List<Long> listUserIds(Long projectId) {
        permissionService.requireProject(projectId);
        return followerMapper.selectList(new LambdaQueryWrapper<ProjectFollowerDO>()
                        .eq(ProjectFollowerDO::getProjectId, projectId)
                        .orderByAsc(ProjectFollowerDO::getId))
                .stream()
                .map(ProjectFollowerDO::getUserId)
                .collect(Collectors.toList());
    }

    public List<com.brad.pms.dto.response.UserDTO> list(Long projectId) {
        List<Long> userIds = listUserIds(projectId);
        if (userIds.isEmpty()) return Collections.emptyList();
        return userService.listByIds(userIds).stream()
                .map(com.brad.pms.convertor.Convertors::toUser)
                .collect(Collectors.toList());
    }

    @Transactional
    public void replace(Long projectId, List<Long> userIds) {
        permissionService.requireProjectManageable(projectId, "维护项目关注人");
        List<Long> selected = userIds == null ? Collections.emptyList() : userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<Long> previous = followerMapper.selectList(new LambdaQueryWrapper<ProjectFollowerDO>()
                        .eq(ProjectFollowerDO::getProjectId, projectId)
                        .orderByAsc(ProjectFollowerDO::getId))
                .stream().map(ProjectFollowerDO::getUserId).collect(Collectors.toList());
        followerMapper.delete(new LambdaQueryWrapper<ProjectFollowerDO>()
                .eq(ProjectFollowerDO::getProjectId, projectId));
        selected.forEach(userId -> {
            ProjectFollowerDO follower = new ProjectFollowerDO();
            follower.setProjectId(projectId);
            follower.setUserId(userId);
            followerMapper.insert(follower);
        });
        if (!previous.equals(selected)) {
            operationLogService.record(AuditEvent.success(
                    AuditAction.PROJECT_FOLLOWER_CHANGED.name(), AuditResourceType.PROJECT_FOLLOWER.name(), null, projectId,
                    null, java.util.Map.of("userIds", previous), java.util.Map.of("userIds", selected)));
        }
    }
}
