package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    public List<Long> listUserIds(Long projectId) {
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
        permissionService.requireManageableProject(projectId, "维护项目关注人");
        List<Long> selected = userIds == null ? Collections.emptyList() : userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        followerMapper.delete(new LambdaQueryWrapper<ProjectFollowerDO>()
                .eq(ProjectFollowerDO::getProjectId, projectId));
        selected.forEach(userId -> {
            ProjectFollowerDO follower = new ProjectFollowerDO();
            follower.setProjectId(projectId);
            follower.setUserId(userId);
            followerMapper.insert(follower);
        });
    }
}
