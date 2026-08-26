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
        return userId != null && (Objects.equals(project.getCreatedBy(), userId)
                || Objects.equals(project.getProjectManagerId(), userId));
    }

    public static boolean isProjectOpen(ProjectDO project) {
        return project != null && ProjectStatus.normalize(project.getStatus()) == ProjectStatus.ACTIVE.getCode();
    }

    public static boolean isNodeOpen(ProjectNodeDO node) {
        return node != null && !NodeStatus.isReadOnly(node.getStatus());
    }

    public static boolean canManageProject(ProjectDO project, Long userId) {
        return isProjectOpen(project) && isProjectManagerOrCreator(project, userId);
    }

    public static boolean canManageNode(ProjectDO project, ProjectNodeDO node, Long userId) {
        return isProjectOpen(project) && isNodeOpen(node) && userId != null
                && (isProjectManagerOrCreator(project, userId) || Objects.equals(node.getOwnerId(), userId));
    }

    public static boolean canCompleteNode(ProjectDO project, ProjectNodeDO node, Long userId) {
        return canManageNode(project, node, userId)
                && Objects.equals(node.getStatus(), NodeStatus.IN_PROGRESS.getCode());
    }

    public static boolean canRollbackNode(ProjectDO project, ProjectNodeDO node, Long userId) {
        return project != null && ProjectStatus.normalize(project.getStatus()) != ProjectStatus.TERMINATED.getCode()
                && Objects.equals(node.getStatus(), NodeStatus.COMPLETED.getCode())
                && isProjectManagerOrCreator(project, userId);
    }

    public static boolean canTerminateProject(ProjectDO project, Long userId) {
        return isProjectOpen(project) && isProjectManagerOrCreator(project, userId);
    }

    public static boolean canEditTaskContent(ProjectDO project, ProjectNodeDO node,
                                              ProjectTaskDO task, Long userId) {
        return isProjectOpen(project) && isNodeOpen(node) && userId != null
                && Objects.equals(task.getAssigneeId(), userId);
    }

    public static boolean canManageTask(ProjectDO project, ProjectNodeDO node,
                                        ProjectTaskDO task, Long userId) {
        return canManageTaskScope(project, node, userId);
    }

    private static boolean canManageTaskScope(ProjectDO project, ProjectNodeDO node, Long userId) {
        return isProjectOpen(project) && isNodeOpen(node) && userId != null
                && (isProjectManagerOrCreator(project, userId) || Objects.equals(node.getOwnerId(), userId));
    }
}
