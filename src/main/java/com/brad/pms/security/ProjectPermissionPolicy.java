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
        return isProjectOpen(project) && isNodeOpen(node) && userId != null
                && (hasProjectControl(project, userId, administrator) || Objects.equals(node.getOwnerId(), userId));
    }

    public static boolean canCompleteNode(ProjectDO project, ProjectNodeDO node, Long userId) {
        return canCompleteNode(project, node, userId, false);
    }

    public static boolean canCompleteNode(ProjectDO project, ProjectNodeDO node, Long userId,
                                          boolean administrator) {
        return canManageNode(project, node, userId, administrator)
                && Objects.equals(node.getStatus(), NodeStatus.IN_PROGRESS.getCode());
    }

    public static boolean canRollbackNode(ProjectDO project, ProjectNodeDO node, Long userId) {
        return canRollbackNode(project, node, userId, false);
    }

    public static boolean canRollbackNode(ProjectDO project, ProjectNodeDO node, Long userId,
                                          boolean administrator) {
        return project != null && ProjectStatus.normalize(project.getStatus()) != ProjectStatus.TERMINATED.getCode()
                && Objects.equals(node.getStatus(), NodeStatus.COMPLETED.getCode())
                && hasProjectControl(project, userId, administrator);
    }

    public static boolean canTerminateProject(ProjectDO project, Long userId) {
        return canTerminateProject(project, userId, false);
    }

    public static boolean canTerminateProject(ProjectDO project, Long userId, boolean administrator) {
        return project != null
                && ProjectStatus.normalize(project.getStatus()) == ProjectStatus.ACTIVE.getCode()
                && hasProjectControl(project, userId, administrator);
    }

    public static boolean canEditTaskContent(ProjectDO project, ProjectNodeDO node,
                                              ProjectTaskDO task, Long userId) {
        return canEditTaskContent(project, node, task, userId, false);
    }

    public static boolean canEditTaskContent(ProjectDO project, ProjectNodeDO node,
                                              ProjectTaskDO task, Long userId, boolean administrator) {
        return isProjectOpen(project) && isNodeOpen(node) && userId != null
                && (administrator || Objects.equals(task.getAssigneeId(), userId));
    }

    public static boolean canManageTask(ProjectDO project, ProjectNodeDO node,
                                        ProjectTaskDO task, Long userId) {
        return canManageTask(project, node, task, userId, false);
    }

    public static boolean canManageTask(ProjectDO project, ProjectNodeDO node,
                                        ProjectTaskDO task, Long userId, boolean administrator) {
        return canManageTaskScope(project, node, userId, administrator);
    }

    private static boolean canManageTaskScope(ProjectDO project, ProjectNodeDO node, Long userId,
                                              boolean administrator) {
        return isProjectOpen(project) && isNodeOpen(node) && userId != null
                && (hasProjectControl(project, userId, administrator) || Objects.equals(node.getOwnerId(), userId));
    }
}
