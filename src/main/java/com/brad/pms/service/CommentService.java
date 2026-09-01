package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.request.CommentCreateCmd;
import com.brad.pms.dto.response.ProjectCommentDTO;
import com.brad.pms.entity.ProjectCommentDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectCommentMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.security.UserContext;
import com.brad.pms.security.ProjectPermissionPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final ProjectCommentMapper commentMapper;
    private final ProjectTaskMapper taskMapper;
    private final UserService userService;
    private final ProjectPermissionService permissionService;
    private final NotificationService notificationService;

    public List<ProjectCommentDTO> listByProject(Long projectId, Long taskId) {
        permissionService.requireProject(projectId);
        if (taskId != null) requireTaskInProject(projectId, taskId);
        LambdaQueryWrapper<ProjectCommentDO> query = new LambdaQueryWrapper<ProjectCommentDO>()
                .eq(ProjectCommentDO::getProjectId, projectId);
        if (taskId != null) query.eq(ProjectCommentDO::getTaskId, taskId);
        else query.isNull(ProjectCommentDO::getTaskId);
        List<ProjectCommentDO> comments = commentMapper.selectList(
                query.orderByDesc(ProjectCommentDO::getCreatedAt));
        Map<Long, UserDO> userMap = userService.listByIds(
                        comments.stream().map(ProjectCommentDO::getUserId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(UserDO::getId, u -> u));
        return comments.stream()
                .map(c -> Convertors.toComment(c, userMap.get(c.getUserId())))
                .collect(Collectors.toList());
    }

    public ProjectCommentDTO add(Long projectId, CommentCreateCmd cmd) {
        ProjectDO project = permissionService.requireProject(projectId);
        ProjectTaskDO task = cmd.getTaskId() == null ? null : requireTaskInProject(projectId, cmd.getTaskId());
        ProjectCommentDO comment = new ProjectCommentDO();
        comment.setProjectId(projectId);
        comment.setTaskId(cmd.getTaskId());
        comment.setContent(cmd.getContent());
        comment.setUserId(UserContext.userId());
        commentMapper.insert(comment);
        notificationService.notifyComment(projectId, cmd.getTaskId(), cmd.getContent(),
                task == null ? null : task.getAssigneeId(),
                project.getProjectManagerId());
        UserDO author = userService.listByIds(List.of(comment.getUserId())).stream().findFirst().orElse(null);
        return Convertors.toComment(comment, author);
    }

    private ProjectTaskDO requireTaskInProject(Long projectId, Long taskId) {
        ProjectTaskDO task = taskMapper.selectById(taskId);
        if (task == null || !java.util.Objects.equals(task.getProjectId(), projectId)) {
            throw BusinessException.error("任务不属于当前项目");
        }
        return task;
    }

    public void delete(Long id) {
        ProjectCommentDO comment = commentMapper.selectById(id);
        if (comment == null) {
            throw BusinessException.error("评论不存在");
        }
        var project = permissionService.requireProject(comment.getProjectId());
        Long userId = UserContext.userId();
        if (!java.util.Objects.equals(comment.getUserId(), userId)
                && !ProjectPermissionPolicy.hasProjectControl(project, userId, UserContext.isAdministrator())) {
            throw BusinessException.forbidden("只能删除自己的评论，或由项目创建人/项目经理删除");
        }
        commentMapper.deleteById(id);
    }
}
