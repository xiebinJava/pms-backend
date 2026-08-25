package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.request.TaskCreateCmd;
import com.brad.pms.dto.request.TaskMoveCmd;
import com.brad.pms.dto.request.TaskUpdateCmd;
import com.brad.pms.dto.response.ProjectTaskDTO;
import com.brad.pms.entity.ProjectTaskDO;
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

    public List<ProjectTaskDTO> listByProject(Long projectId) {
        List<ProjectTaskDO> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<ProjectTaskDO>()
                        .eq(ProjectTaskDO::getProjectId, projectId)
                        .orderByAsc(ProjectTaskDO::getSort)
                        .orderByDesc(ProjectTaskDO::getCreatedAt));
        Map<Long, UserDO> userMap = userService.listByIds(
                        tasks.stream().map(ProjectTaskDO::getAssigneeId)
                                .filter(java.util.Objects::nonNull)
                                .collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(UserDO::getId, u -> u));
        return tasks.stream()
                .map(t -> Convertors.toTask(t, t.getAssigneeId() == null ? null : userMap.get(t.getAssigneeId())))
                .collect(Collectors.toList());
    }

    public ProjectTaskDTO create(TaskCreateCmd cmd) {
        ProjectTaskDO task = new ProjectTaskDO();
        task.setProjectId(cmd.getProjectId());
        task.setParentId(cmd.getParentId());
        task.setTitle(cmd.getTitle());
        task.setDescription(cmd.getDescription());
        task.setStatus(cmd.getStatus());
        task.setPriority(cmd.getPriority());
        task.setAssigneeId(cmd.getAssigneeId());
        task.setMilestoneId(cmd.getMilestoneId());
        task.setSort(cmd.getSort());
        task.setDueDate(cmd.getDueDate());
        taskMapper.insert(task);
        return get(task.getId());
    }

    public ProjectTaskDTO update(Long id, TaskUpdateCmd cmd) {
        ProjectTaskDO task = requireTask(id);
        if (StringUtils.hasText(cmd.getTitle())) task.setTitle(cmd.getTitle());
        if (cmd.getDescription() != null) task.setDescription(cmd.getDescription());
        if (cmd.getStatus() != null) task.setStatus(cmd.getStatus());
        if (cmd.getPriority() != null) task.setPriority(cmd.getPriority());
        if (cmd.getAssigneeId() != null) task.setAssigneeId(cmd.getAssigneeId());
        if (cmd.getMilestoneId() != null) task.setMilestoneId(cmd.getMilestoneId());
        if (cmd.getSort() != null) task.setSort(cmd.getSort());
        if (cmd.getDueDate() != null) task.setDueDate(cmd.getDueDate());
        taskMapper.updateById(task);
        return get(id);
    }

    public ProjectTaskDTO move(Long id, TaskMoveCmd cmd) {
        ProjectTaskDO task = requireTask(id);
        task.setStatus(cmd.getStatus());
        taskMapper.updateById(task);
        return get(id);
    }

    public void delete(Long id) {
        requireTask(id);
        taskMapper.deleteById(id);
    }

    private ProjectTaskDO requireTask(Long id) {
        ProjectTaskDO task = taskMapper.selectById(id);
        if (task == null) {
            throw BusinessException.error("任务不存在");
        }
        return task;
    }

    private ProjectTaskDTO get(Long id) {
        ProjectTaskDO task = requireTask(id);
        UserDO assignee = task.getAssigneeId() == null ? null
                : userService.listByIds(List.of(task.getAssigneeId())).stream().findFirst().orElse(null);
        return Convertors.toTask(task, assignee);
    }
}
