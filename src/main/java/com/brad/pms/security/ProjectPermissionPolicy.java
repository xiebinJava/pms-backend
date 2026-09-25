package com.brad.pms.security;

import com.brad.pms.common.enums.NodeStatus;
import com.brad.pms.common.enums.ProjectStatus;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectTaskDO;

import java.util.Objects;

/**
 * PMS 项目协作权限的无状态判定器，服务层和测试共同使用，避免前后端出现不同规则。
 */
public final class ProjectPermissionPolicy {

    private ProjectPermissionPolicy() {
    }

    public static boolean isProjectManagerOrCreator(ProjectDO project, Long userId) {
        return project != null && userId != null && (Objects.equals(project.getCreatedBy(), userId)
                || Objects.equals(project.getProjectManagerId(), userId));
    }

    public static boolean hasProjectControl(ProjectDO project, Long userId, boolean administrator) {
        return administrator || isProjectManagerOrCreator(project, userId);
    }

    public static boolean isProjectOpen(ProjectDO project) {
        return project != null && ProjectStatus.isOpen(project.getStatus());
    }

    public static boolean isProjectReadable(ProjectDO project) {
        return project != null
                && !Boolean.TRUE.equals(project.getDeleted())
                && ProjectStatus.normalize(project.getStatus()) != ProjectStatus.DELETED.getCode();
    }

    public static boolean isProjectOperational(ProjectDO project) {
        return isProjectOpen(project);
    }

    public static boolean isProjectRestorable(ProjectDO project) {
        if (project == null) return false;
        int status = ProjectStatus.normalize(project.getStatus());
        return status == ProjectStatus.TERMINATED.getCode() || status == ProjectStatus.DELETED.getCode();
    }

    public static boolean isNodeOpen(ProjectNodeDO node) {
        return node != null && !NodeStatus.isReadOnly(node.getStatus());
    }

    public static boolean canManageProject(ProjectDO project, Long userId) {
        return canManageProject(project, userId, false);
    }

    public static boolean canManageProject(ProjectDO project, Long userId, boolean administrator) {
        return isProjectOpen(project) && hasProjectControl(project, userId, administrator);
    }

    public static boolean canManageNode(ProjectDO project, ProjectNodeDO node, Long userId) {
        return canManageNode(project, node, userId, false);
    }

    public static boolean canManageNode(ProjectDO project, ProjectNodeDO node, Long userId,
                                        boolean administrator) {
        return canManageNode(project, node, userId, administrator,
                hasProjectControl(project, userId, administrator));
    }

    public static boolean canManageNode(ProjectDO project, ProjectNodeDO node, Long userId,
                                        boolean administrator, boolean projectControl) {
        return isProjectOpen(project) && isNodeOpen(node) && userId != null
                && (projectControl || Objects.equals(node.getOwnerId(), userId));
    }

    public static boolean canCompleteNode(ProjectDO project, ProjectNodeDO node, Long userId) {
        return canCompleteNode(project, node, userId, false);
    }

    public static boolean canCompleteNode(ProjectDO project, ProjectNodeDO node, Long userId,
                                          boolean administrator) {
        return canCompleteNode(project, node, userId, administrator,
                hasProjectControl(project, userId, administrator));
    }

    public static boolean canCompleteNode(ProjectDO project, ProjectNodeDO node, Long userId,
                                          boolean administrator, boolean projectControl) {
        return canManageNode(project, node, userId, administrator, projectControl)
                && Objects.equals(node.getStatus(), NodeStatus.IN_PROGRESS.getCode());
    }

    public static boolean canRollbackNode(ProjectDO project, ProjectNodeDO node, Long userId) {
        return canRollbackNode(project, node, userId, false);
    }

    public static boolean canRollbackNode(ProjectDO project, ProjectNodeDO node, Long userId,
                                          boolean administrator) {
        return canRollbackNode(project, node, userId, administrator,
                hasProjectControl(project, userId, administrator));
    }

    public static boolean canRollbackNode(ProjectDO project, ProjectNodeDO node, Long userId,
                                          boolean administrator, boolean projectControl) {
        return project != null && ProjectStatus.normalize(project.getStatus()) != ProjectStatus.TERMINATED.getCode()
                && Objects.equals(node.getStatus(), NodeStatus.COMPLETED.getCode())
                && projectControl;
    }

    public static boolean canTerminateProject(ProjectDO project, Long userId) {
        return canTerminateProject(project, userId, false);
    }

    public static boolean canTerminateProject(ProjectDO project, Long userId, boolean administrator) {
        return canTerminateProject(project, userId, administrator,
                hasProjectControl(project, userId, administrator));
    }

    public static boolean canTerminateProject(ProjectDO project, Long userId, boolean administrator,
                                              boolean projectControl) {
        return project != null
                && ProjectStatus.normalize(project.getStatus()) == ProjectStatus.ACTIVE.getCode()
                && projectControl;
    }

    public static boolean canEditTaskContent(ProjectDO project, ProjectNodeDO node,
                                              ProjectTaskDO task, Long userId) {
        return canEditTaskContent(project, node, task, userId, false);
    }

    public static boolean canEditTaskContent(ProjectDO project, ProjectNodeDO node,
                                              ProjectTaskDO task, Long userId, boolean administrator) {
        return canEditTaskContent(project, node, task, userId, administrator,
                hasProjectControl(project, userId, administrator));
    }

    public static boolean canEditTaskContent(ProjectDO project, ProjectNodeDO node,
                                              ProjectTaskDO task, Long userId, boolean administrator,
                                              boolean projectControl) {
        return isProjectOpen(project) && isNodeOpen(node) && userId != null
                && (projectControl || Objects.equals(task.getAssigneeId(), userId));
    }

    public static boolean canManageTask(ProjectDO project, ProjectNodeDO node,
                                        ProjectTaskDO task, Long userId) {
        return canManageTask(project, node, task, userId, false);
    }

    public static boolean canManageTask(ProjectDO project, ProjectNodeDO node,
                                        ProjectTaskDO task, Long userId, boolean administrator) {
        return canManageTask(project, node, task, userId, administrator,
                hasProjectControl(project, userId, administrator));
    }

    public static boolean canManageTask(ProjectDO project, ProjectNodeDO node,
                                        ProjectTaskDO task, Long userId, boolean administrator,
                                        boolean projectControl) {
        return canManageTaskScope(project, node, userId, projectControl);
    }

    private static boolean canManageTaskScope(ProjectDO project, ProjectNodeDO node, Long userId,
                                              boolean projectControl) {
        return isProjectOpen(project) && isNodeOpen(node) && userId != null
                && (projectControl || Objects.equals(node.getOwnerId(), userId));
    }
}
