package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.security.ProjectPermissionPolicy;
import com.brad.pms.security.UserContext;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.request.TaskCreateCmd;
import com.brad.pms.dto.request.TaskMoveCmd;
import com.brad.pms.dto.request.TaskUpdateCmd;
import com.brad.pms.dto.response.ProjectTaskDTO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final ProjectTaskMapper taskMapper;
    private final UserService userService;
    private final ProjectPermissionService permissionService;

    public List<ProjectTaskDTO> listByProject(Long projectId, Long nodeId) {
        ProjectDO project = permissionService.requireProject(projectId);
        LambdaQueryWrapper<ProjectTaskDO> query = new LambdaQueryWrapper<ProjectTaskDO>()
                .eq(ProjectTaskDO::getProjectId, projectId);
        if (nodeId != null) query.eq(ProjectTaskDO::getNodeId, nodeId);
        query.orderByAsc(ProjectTaskDO::getSort).orderByDesc(ProjectTaskDO::getCreatedAt);
        List<ProjectTaskDO> tasks = taskMapper.selectList(query);
        Map<Long, UserDO> userMap = userService.listByIds(
                        tasks.stream().map(ProjectTaskDO::getAssigneeId)
                                .filter(java.util.Objects::nonNull)
                                .collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(UserDO::getId, u -> u));
        return tasks.stream()
                .map(t -> toDTO(t, project,
                        t.getNodeId() == null ? null : permissionService.requireNode(projectId, t.getNodeId()),
                        t.getAssigneeId() == null ? null : userMap.get(t.getAssigneeId())))
                .collect(Collectors.toList());
    }

    public ProjectTaskDTO create(TaskCreateCmd cmd) {
        if (cmd.getNodeId() == null) {
            throw BusinessException.error("任务必须归属一个项目节点");
        }
        ProjectDO project = permissionService.requireProject(cmd.getProjectId());
        ProjectNodeDO node = permissionService.requireManageableNode(cmd.getProjectId(), cmd.getNodeId(), "创建任务");
        if (cmd.getAssigneeId() != null) {
            permissionService.requireProjectMember(cmd.getProjectId(), cmd.getAssigneeId());
        }
        ProjectTaskDO task = new ProjectTaskDO();
        task.setProjectId(cmd.getProjectId());
        task.setNodeId(cmd.getNodeId());
        task.setParentId(cmd.getParentId());
        task.setTitle(cmd.getTitle());
        task.setDescription(cmd.getDescription());
        task.setDeliverable(cmd.getDeliverable());
        task.setStatus(cmd.getStatus());
        task.setPriority(cmd.getPriority());
        task.setAssigneeId(cmd.getAssigneeId());
        task.setMilestoneId(cmd.getMilestoneId());
        task.setSort(cmd.getSort());
        task.setDueDate(cmd.getDueDate());
        taskMapper.insert(task);
        return toDTO(task, project, node, loadAssignee(task.getAssigneeId()));
    }

    public ProjectTaskDTO update(Long id, TaskUpdateCmd cmd) {
        ProjectTaskDO task = requireTask(id);
        ProjectDO project = permissionService.requireProject(task.getProjectId());
        ProjectNodeDO node = permissionService.requireNode(task.getProjectId(), task.getNodeId());
        Long userId = UserContext.userId();
        boolean manager = ProjectPermissionPolicy.canManageTask(project, node, task, userId);
        boolean assignee = ProjectPermissionPolicy.canEditTaskContent(project, node, task, userId);
        if (!manager && !assignee) {
            throw BusinessException.forbidden("仅项目创建人、项目经理、节点负责人或任务负责人可以编辑任务");
        }
        if (!manager && (cmd.getPriority() != null || cmd.getAssigneeId() != null
                || cmd.getMilestoneId() != null || cmd.getSort() != null)) {
            throw BusinessException.forbidden("任务负责人只能修改任务内容、状态和截止日期");
        }
        if (StringUtils.hasText(cmd.getTitle())) task.setTitle(cmd.getTitle());
        if (cmd.getDescription() != null) task.setDescription(cmd.getDescription());
        if (cmd.getDeliverable() != null) task.setDeliverable(cmd.getDeliverable());
        if (cmd.getStatus() != null) task.setStatus(cmd.getStatus());
        if (manager && cmd.getPriority() != null) task.setPriority(cmd.getPriority());
        if (manager && cmd.getAssigneeId() != null) {
            permissionService.requireProjectMember(task.getProjectId(), cmd.getAssigneeId());
            task.setAssigneeId(cmd.getAssigneeId());
        }
        if (cmd.getMilestoneId() != null) task.setMilestoneId(cmd.getMilestoneId());
        if (cmd.getSort() != null) task.setSort(cmd.getSort());
        if (cmd.getDueDate() != null) task.setDueDate(cmd.getDueDate());
        taskMapper.updateById(task);
        return toDTO(task, project, node, loadAssignee(task.getAssigneeId()));
    }

    public ProjectTaskDTO move(Long id, TaskMoveCmd cmd) {
        ProjectTaskDO task = requireTask(id);
        ProjectDO project = permissionService.requireProject(task.getProjectId());
        ProjectNodeDO node = permissionService.requireNode(task.getProjectId(), task.getNodeId());
        Long userId = UserContext.userId();
        if (!ProjectPermissionPolicy.canManageTask(project, node, task, userId)
                && !ProjectPermissionPolicy.canEditTaskContent(project, node, task, userId)) {
            throw BusinessException.forbidden("当前用户没有移动该任务的权限");
        }
        task.setStatus(cmd.getStatus());
        taskMapper.updateById(task);
        return toDTO(task, project, node, loadAssignee(task.getAssigneeId()));
    }

    public void delete(Long id) {
        ProjectTaskDO task = requireTask(id);
        ProjectDO project = permissionService.requireProject(task.getProjectId());
        ProjectNodeDO node = permissionService.requireNode(task.getProjectId(), task.getNodeId());
        if (!ProjectPermissionPolicy.canManageTask(project, node, task, UserContext.userId())) {
            throw BusinessException.forbidden("仅项目创建人、项目经理或节点负责人可以删除任务");
        }
        taskMapper.deleteById(id);
    }

    private ProjectTaskDO requireTask(Long id) {
        ProjectTaskDO task = taskMapper.selectById(id);
        if (task == null) {
            throw BusinessException.error("任务不存在");
        }
        return task;
    }

    private UserDO loadAssignee(Long assigneeId) {
        return assigneeId == null ? null
                : userService.listByIds(List.of(assigneeId)).stream().findFirst().orElse(null);
    }

    private ProjectTaskDTO toDTO(ProjectTaskDO task, ProjectDO project, ProjectNodeDO node, UserDO assignee) {
        ProjectTaskDTO dto = Convertors.toTask(task, assignee);
        dto.setPermissions(permissionService.taskPermissions(project, node, task));
        return dto;
    }
}
