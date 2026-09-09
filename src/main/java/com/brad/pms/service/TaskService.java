package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
import com.brad.pms.common.enums.TaskStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.security.ProjectPermissionPolicy;
import com.brad.pms.security.UserContext;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.request.TaskCreateCmd;
import com.brad.pms.dto.request.TaskMoveCmd;
import com.brad.pms.dto.request.TaskUpdateCmd;
import com.brad.pms.dto.response.ProjectCommentDTO;
import com.brad.pms.dto.response.ProjectTaskDTO;
import com.brad.pms.dto.response.TaskDetailDTO;
import com.brad.pms.entity.ProjectCommentDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeRequirementDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.entity.ProjectTaskRequirementDO;
import com.brad.pms.mapper.ProjectCommentMapper;
import com.brad.pms.mapper.ProjectNodeRequirementMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.mapper.ProjectTaskRequirementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final ProjectTaskMapper taskMapper;
    private final ProjectTaskRequirementMapper taskRequirementMapper;
    private final ProjectNodeRequirementMapper requirementMapper;
    private final ProjectCommentMapper commentMapper;
    private final UserService userService;
    private final ProjectPermissionService permissionService;
    private final TaskAttachmentService attachmentService;
    private final NotificationService notificationService;
    private final OperationLogService operationLogService;

    public List<ProjectTaskDTO> listByProject(Long projectId, Long nodeId) {
        ProjectDO project = permissionService.requireProject(projectId);
        LambdaQueryWrapper<ProjectTaskDO> query = new LambdaQueryWrapper<ProjectTaskDO>()
                .eq(ProjectTaskDO::getProjectId, projectId);
        if (nodeId != null) query.eq(ProjectTaskDO::getNodeId, nodeId);
        query.orderByAsc(ProjectTaskDO::getSort).orderByDesc(ProjectTaskDO::getCreatedAt);
        List<ProjectTaskDO> tasks = taskMapper.selectList(query);
        Map<Long, Long> subtaskCounts = tasks.stream()
                .filter(task -> task.getParentId() != null)
                .collect(Collectors.groupingBy(ProjectTaskDO::getParentId, Collectors.counting()));
        Map<Long, UserDO> userMap = userService.listByIds(
                        tasks.stream().map(ProjectTaskDO::getAssigneeId)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(UserDO::getId, u -> u));
        return tasks.stream()
                .filter(task -> task.getParentId() == null)
                .map(t -> {
                    ProjectTaskDTO dto = toDTO(t, project,
                            t.getNodeId() == null ? null : permissionService.requireNode(projectId, t.getNodeId()),
                            t.getAssigneeId() == null ? null : userMap.get(t.getAssigneeId()));
                    dto.setSubtaskCount(subtaskCounts.getOrDefault(t.getId(), 0L).intValue());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public TaskDetailDTO getDetail(Long id) {
        ProjectTaskDO task = requireTask(id);
        ProjectDO project = permissionService.requireProject(task.getProjectId());
        ProjectNodeDO node = task.getNodeId() == null ? null : permissionService.requireNode(task.getProjectId(), task.getNodeId());
        List<ProjectTaskDO> children = taskMapper.selectList(new LambdaQueryWrapper<ProjectTaskDO>()
                .eq(ProjectTaskDO::getParentId, id)
                .orderByAsc(ProjectTaskDO::getSort)
                .orderByDesc(ProjectTaskDO::getCreatedAt));
        Map<Long, UserDO> userMap = userService.listByIds(
                        Stream.concat(
                                        Stream.of(task.getAssigneeId()),
                                        children.stream().map(ProjectTaskDO::getAssigneeId))
                                .filter(Objects::nonNull)
                                .distinct()
                                .collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(UserDO::getId, u -> u));
        TaskDetailDTO dto = new TaskDetailDTO();
        BeanUtils.copyProperties(
                toDTO(task, project, node, task.getAssigneeId() == null ? null : userMap.get(task.getAssigneeId())),
                dto);
        dto.setSubtaskCount(children.size());
        dto.setSubtasks(children.stream()
                .map(child -> toDTO(child, project, node,
                        child.getAssigneeId() == null ? null : userMap.get(child.getAssigneeId())))
                .collect(Collectors.toList()));
        dto.setComments(toComments(commentMapper.selectList(new LambdaQueryWrapper<ProjectCommentDO>()
                .eq(ProjectCommentDO::getTaskId, id)
                .orderByDesc(ProjectCommentDO::getCreatedAt)), project));
        dto.setAttachments(attachmentService.listByTask(id));
        return dto;
    }

    @Transactional
    public ProjectTaskDTO create(TaskCreateCmd cmd) {
        if (cmd.getNodeId() == null) {
            throw BusinessException.error("任务必须归属一个项目节点");
        }
        ProjectDO project = permissionService.requireProject(cmd.getProjectId());
        ProjectNodeDO node = permissionService.requireManageableNode(cmd.getProjectId(), cmd.getNodeId(), "创建任务");
        ProjectNodeRequirementDO requirement = resolveRequirement(cmd.getRequirementId(), cmd.getProjectId(), cmd.getNodeId());
        if (cmd.getAssigneeId() != null) {
            permissionService.requireProjectMember(cmd.getProjectId(), cmd.getAssigneeId());
        }
        Long parentId = cmd.getParentId();
        if (parentId != null) {
            ProjectTaskDO parent = requireTask(parentId);
            if (!Objects.equals(parent.getProjectId(), cmd.getProjectId())) {
                throw BusinessException.error("子任务必须属于同一个项目");
            }
            if (parent.getParentId() != null) {
                throw BusinessException.error("子任务不能再拆分子任务");
            }
            if (Objects.equals(parent.getStatus(), TaskStatus.DONE.getCode())) {
                throw BusinessException.forbidden("父任务已完成，不能新增子任务，请先回退父任务");
            }
            if (parent.getNodeId() != null && !Objects.equals(parent.getNodeId(), cmd.getNodeId())) {
                throw BusinessException.error("子任务必须归属父任务所在节点");
            }
        }
        ProjectTaskDO task = new ProjectTaskDO();
        task.setProjectId(cmd.getProjectId());
        task.setNodeId(cmd.getNodeId());
        task.setParentId(parentId);
        task.setTitle(cmd.getTitle());
        task.setDescription(cmd.getDescription());
        task.setDeliverable(cmd.getDeliverable());
        task.setStatus(cmd.getStatus());
        task.setPriority(cmd.getPriority());
        task.setAssigneeId(cmd.getAssigneeId());
        task.setSort(cmd.getSort());
        task.setDueDate(cmd.getDueDate());
        taskMapper.insert(task);
        if (requirement != null) replaceTaskRequirementLink(task, requirement);
        if (task.getAssigneeId() != null) {
            notificationService.notifyTaskAssigned(task.getProjectId(), task.getId(), task.getTitle(), task.getAssigneeId());
        }
        operationLogService.record(AuditEvent.success(
                AuditAction.TASK_CREATED.name(), AuditResourceType.TASK.name(), task.getId(), task.getProjectId(),
                null, null, taskAuditSnapshot(task)));
        ProjectTaskDTO dto = toDTO(task, project, node, loadAssignee(task.getAssigneeId()));
        if (requirement != null) {
            dto.setRequirementId(requirement.getId());
            dto.setRequirementCode(requirement.getCode());
        }
        return dto;
    }

    @Transactional
    public ProjectTaskDTO update(Long id, TaskUpdateCmd cmd) {
        ProjectTaskDO task = requireTask(id);
        ProjectDO project = permissionService.requireProject(task.getProjectId());
        ProjectNodeDO node = permissionService.requireNode(task.getProjectId(), task.getNodeId());
        String previousDescription = task.getDescription();
        String previousDeliverable = task.getDeliverable();
        Integer previousPriority = task.getPriority();
        Integer previousStatus = task.getStatus();
        java.util.Map<String, Object> before = taskContentAuditSnapshot(task, false, false);
        Long userId = UserContext.userId();
        boolean administrator = UserContext.isAdministrator();
        boolean projectControl = permissionService.canWriteProject(project);
        boolean manager = ProjectPermissionPolicy.canManageTask(project, node, task, userId, administrator, projectControl);
        boolean assignee = ProjectPermissionPolicy.canEditTaskContent(project, node, task, userId, administrator, projectControl);
        if (!manager && !assignee) {
            throw BusinessException.forbidden("仅项目创建人、项目经理、节点负责人或任务负责人可以编辑任务");
        }
        if (!manager && (cmd.getPriority() != null || cmd.getAssigneeId() != null
                || cmd.getSort() != null
                || cmd.getRequirementId() != null || Boolean.TRUE.equals(cmd.getClearRequirement()))) {
            throw BusinessException.forbidden("任务负责人只能修改任务内容、状态和截止日期");
        }
        ProjectNodeRequirementDO requirement = cmd.getRequirementId() == null
                ? null
                : resolveRequirement(cmd.getRequirementId(), task.getProjectId(), task.getNodeId());
        ensureChildStatusAllowed(task, cmd.getStatus());
        if (StringUtils.hasText(cmd.getTitle())) task.setTitle(cmd.getTitle());
        if (cmd.getDescription() != null) task.setDescription(cmd.getDescription());
        if (cmd.getDeliverable() != null) task.setDeliverable(cmd.getDeliverable());
        if (cmd.getStatus() != null) task.setStatus(cmd.getStatus());
        if (manager && cmd.getPriority() != null) task.setPriority(cmd.getPriority());
        Long previousAssignee = task.getAssigneeId();
        if (manager && cmd.getAssigneeId() != null) {
            permissionService.requireProjectMember(task.getProjectId(), cmd.getAssigneeId());
            task.setAssigneeId(cmd.getAssigneeId());
        }
        if (cmd.getSort() != null) task.setSort(cmd.getSort());
        if (Boolean.TRUE.equals(cmd.getClearDueDate())) task.setDueDate(null);
        else if (cmd.getDueDate() != null) task.setDueDate(cmd.getDueDate());
        taskMapper.updateById(task);
        if (manager && (cmd.getRequirementId() != null || Boolean.TRUE.equals(cmd.getClearRequirement()))) {
            replaceTaskRequirementLink(task, requirement);
        }
        completeSubtasksIfCompleted(previousStatus, task);
        if (task.getAssigneeId() != null && !Objects.equals(previousAssignee, task.getAssigneeId())) {
            notificationService.notifyTaskAssigned(task.getProjectId(), task.getId(), task.getTitle(), task.getAssigneeId());
        }
        boolean descriptionChanged = !Objects.equals(previousDescription, task.getDescription());
        boolean deliverableChanged = !Objects.equals(previousDeliverable, task.getDeliverable());
        java.util.Map<String, Object> after = taskContentAuditSnapshot(task, descriptionChanged, deliverableChanged);
        if (!before.equals(after)) {
            operationLogService.record(AuditEvent.success(
                    AuditAction.TASK_UPDATED.name(), AuditResourceType.TASK.name(), id, task.getProjectId(),
                    null, before, after));
        }
        if (!Objects.equals(previousAssignee, task.getAssigneeId())) {
            operationLogService.record(AuditEvent.success(
                    AuditAction.TASK_ASSIGNEE_CHANGED.name(), AuditResourceType.TASK.name(), id, task.getProjectId(),
                    null, personSnapshot(previousAssignee), personSnapshot(task.getAssigneeId())));
        }
        if (!Objects.equals(previousPriority, task.getPriority())) {
            operationLogService.record(AuditEvent.success(
                    AuditAction.TASK_PRIORITY_CHANGED.name(), AuditResourceType.TASK.name(), id, task.getProjectId(),
                    null, java.util.Map.of("priority", String.valueOf(previousPriority)),
                    java.util.Map.of("priority", String.valueOf(task.getPriority()))));
        }
        if (!Objects.equals(previousStatus, task.getStatus())) {
            operationLogService.record(AuditEvent.success(
                    AuditAction.TASK_STATUS_CHANGED.name(), AuditResourceType.TASK.name(), id, task.getProjectId(),
                    null, java.util.Map.of("status", String.valueOf(previousStatus)),
                    java.util.Map.of("status", String.valueOf(task.getStatus()))));
        }
        return toDTO(task, project, node, loadAssignee(task.getAssigneeId()));
    }

    private void completeDirectSubtasks(ProjectTaskDO parent, LocalDate completionDate) {
        List<ProjectTaskDO> children = taskMapper.selectList(new LambdaQueryWrapper<ProjectTaskDO>()
                .eq(ProjectTaskDO::getParentId, parent.getId()));
        for (ProjectTaskDO child : children) {
            Integer previousStatus = child.getStatus();
            LocalDate previousDueDate = child.getDueDate();
            if (!Objects.equals(child.getStatus(), TaskStatus.DONE.getCode())) {
                child.setStatus(TaskStatus.DONE.getCode());
            }
            if (child.getDueDate() == null) child.setDueDate(completionDate);
            if (Objects.equals(previousStatus, child.getStatus())
                    && Objects.equals(previousDueDate, child.getDueDate())) continue;

            taskMapper.updateById(child);
            if (!Objects.equals(previousStatus, child.getStatus())) {
                operationLogService.record(AuditEvent.success(
                        AuditAction.TASK_STATUS_CHANGED.name(), AuditResourceType.TASK.name(), child.getId(), child.getProjectId(),
                        null, java.util.Map.of("status", String.valueOf(previousStatus)),
                        java.util.Map.of("status", String.valueOf(child.getStatus()))));
            }
            if (!Objects.equals(previousDueDate, child.getDueDate())) {
                operationLogService.record(AuditEvent.success(
                        AuditAction.TASK_UPDATED.name(), AuditResourceType.TASK.name(), child.getId(), child.getProjectId(),
                        null, java.util.Map.of("dueDate", String.valueOf(previousDueDate)),
                        java.util.Map.of("dueDate", String.valueOf(child.getDueDate()))));
            }
        }
    }

    private void completeSubtasksIfCompleted(Integer previousStatus, ProjectTaskDO task) {
        if (!Objects.equals(previousStatus, TaskStatus.DONE.getCode())
                && Objects.equals(task.getStatus(), TaskStatus.DONE.getCode())) {
            completeDirectSubtasks(task, LocalDate.now(ZoneId.of("Asia/Shanghai")));
        }
    }

    private void ensureChildStatusAllowed(ProjectTaskDO task, Integer targetStatus) {
        if (targetStatus == null || Objects.equals(targetStatus, TaskStatus.DONE.getCode())
                || task.getParentId() == null) return;
        ProjectTaskDO parent = requireTask(task.getParentId());
        if (Objects.equals(parent.getStatus(), TaskStatus.DONE.getCode())) {
            throw BusinessException.forbidden("父任务已完成，子任务不能回退");
        }
    }

    @Transactional
    public ProjectTaskDTO move(Long id, TaskMoveCmd cmd) {
        ProjectTaskDO task = requireTask(id);
        ProjectDO project = permissionService.requireProject(task.getProjectId());
        ProjectNodeDO node = permissionService.requireNode(task.getProjectId(), task.getNodeId());
        Long userId = UserContext.userId();
        boolean administrator = UserContext.isAdministrator();
        if (!ProjectPermissionPolicy.canManageTask(project, node, task, userId, administrator,
                permissionService.canWriteProject(project))) {
            throw BusinessException.forbidden("当前用户没有移动该任务的权限");
        }
        Integer previousStatus = task.getStatus();
        ensureChildStatusAllowed(task, cmd.getStatus());
        task.setStatus(cmd.getStatus());
        taskMapper.updateById(task);
        completeSubtasksIfCompleted(previousStatus, task);
        operationLogService.record(AuditEvent.success(
                AuditAction.TASK_MOVED.name(), AuditResourceType.TASK.name(), id, task.getProjectId(),
                null, java.util.Map.of("status", String.valueOf(previousStatus)),
                java.util.Map.of("status", String.valueOf(task.getStatus()))));
        return toDTO(task, project, node, loadAssignee(task.getAssigneeId()));
    }

    @Transactional
    public void delete(Long id) {
        ProjectTaskDO task = requireTask(id);
        ProjectDO project = permissionService.requireProject(task.getProjectId());
        ProjectNodeDO node = permissionService.requireNode(task.getProjectId(), task.getNodeId());
        if (!ProjectPermissionPolicy.canManageTask(project, node, task, UserContext.userId(), UserContext.isAdministrator(),
                permissionService.canWriteProject(project))) {
            throw BusinessException.forbidden("仅项目创建人、项目经理或节点负责人可以删除任务");
        }
        java.util.Map<String, Object> before = taskAuditSnapshot(task);
        List<ProjectTaskDO> children = taskMapper.selectList(new LambdaQueryWrapper<ProjectTaskDO>()
                .eq(ProjectTaskDO::getParentId, id));
        for (ProjectTaskDO child : children) {
            deleteTaskRequirementLink(child.getId());
            attachmentService.deleteAllForTask(child.getId());
            taskMapper.deleteById(child.getId());
        }
        deleteTaskRequirementLink(id);
        attachmentService.deleteAllForTask(id);
        taskMapper.deleteById(id);
        operationLogService.record(AuditEvent.success(
                AuditAction.TASK_DELETED.name(), AuditResourceType.TASK.name(), id, task.getProjectId(),
                null, before, null));
    }

    private java.util.Map<String, Object> taskAuditSnapshot(ProjectTaskDO task) {
        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("title", task.getTitle());
        snapshot.put("status", task.getStatus());
        snapshot.put("dueDate", task.getDueDate());
        snapshot.put("assigneeId", task.getAssigneeId());
        snapshot.put("priority", task.getPriority());
        snapshot.put("nodeId", task.getNodeId());
        return snapshot;
    }

    private java.util.Map<String, Object> taskContentAuditSnapshot(ProjectTaskDO task,
                                                                    boolean descriptionChanged,
                                                                    boolean deliverableChanged) {
        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("title", task.getTitle());
        snapshot.put("descriptionChanged", descriptionChanged);
        snapshot.put("deliverableChanged", deliverableChanged);
        snapshot.put("status", task.getStatus());
        snapshot.put("dueDate", task.getDueDate());
        return snapshot;
    }

    private java.util.Map<String, Object> personSnapshot(Long userId) {
        return java.util.Map.of("userId", userId == null ? "UNASSIGNED" : userId);
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

    private ProjectNodeRequirementDO resolveRequirement(Long requirementId, Long projectId, Long nodeId) {
        if (requirementId == null) return null;
        ProjectNodeRequirementDO requirement = requirementMapper.selectById(requirementId);
        if (requirement == null || !Objects.equals(requirement.getProjectId(), projectId)
                || !Objects.equals(requirement.getNodeId(), nodeId)) {
            throw BusinessException.error("关联需求必须属于当前节点");
        }
        if (!Objects.equals(requirement.getStatus(), 1)) {
            throw BusinessException.error("需求必须先确认后才能创建关联任务");
        }
        return requirement;
    }

    private void replaceTaskRequirementLink(ProjectTaskDO task, ProjectNodeRequirementDO requirement) {
        deleteTaskRequirementLink(task.getId());
        if (requirement == null) return;
        ProjectTaskRequirementDO link = new ProjectTaskRequirementDO();
        link.setProjectId(task.getProjectId());
        link.setNodeId(task.getNodeId());
        link.setTaskId(task.getId());
        link.setRequirementId(requirement.getId());
        taskRequirementMapper.insert(link);
    }

    private void deleteTaskRequirementLink(Long taskId) {
        if (taskId == null) return;
        taskRequirementMapper.delete(new LambdaQueryWrapper<ProjectTaskRequirementDO>()
                .eq(ProjectTaskRequirementDO::getTaskId, taskId));
    }

    private ProjectTaskDTO toDTO(ProjectTaskDO task, ProjectDO project, ProjectNodeDO node, UserDO assignee) {
        ProjectTaskDTO dto = Convertors.toTask(task, assignee);
        ProjectTaskRequirementDO link = taskRequirementMapper.selectOne(new LambdaQueryWrapper<ProjectTaskRequirementDO>()
                .eq(ProjectTaskRequirementDO::getTaskId, task.getId()));
        if (link != null) {
            ProjectNodeRequirementDO requirement = requirementMapper.selectById(link.getRequirementId());
            dto.setRequirementId(link.getRequirementId());
            dto.setRequirementCode(requirement == null ? null : requirement.getCode());
        }
        dto.setPermissions(permissionService.taskPermissions(project, node, task));
        return dto;
    }

    private List<ProjectCommentDTO> toComments(List<ProjectCommentDO> comments, ProjectDO project) {
        if (comments.isEmpty()) return List.of();
        Map<Long, UserDO> userMap = userService.listByIds(
                        comments.stream().map(ProjectCommentDO::getUserId).filter(Objects::nonNull).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(UserDO::getId, u -> u, (left, right) -> left));
        return comments.stream()
                .map(comment -> {
                    ProjectCommentDTO dto = Convertors.toComment(comment, userMap.get(comment.getUserId()));
                    dto.setCanDelete(permissionService.canDeleteComment(project, comment.getUserId()));
                    return dto;
                })
                .collect(Collectors.toList());
    }
}
