package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.common.enums.NodeStatus;
import com.brad.pms.common.enums.ProjectStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.response.NodePermissionsDTO;
import com.brad.pms.dto.response.ProjectPermissionsDTO;
import com.brad.pms.dto.response.TaskPermissionsDTO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectMemberMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.security.AuthorizationService;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.ProjectPermissionPolicy;
import com.brad.pms.security.UserContext;
import com.brad.pms.security.DataScopeResolver;
import com.brad.pms.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 所有项目协作写操作的统一鉴权入口。
 */
@Service
@RequiredArgsConstructor
public class ProjectPermissionService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final ProjectNodeMapper nodeMapper;
    private final DataScopeResolver dataScopeResolver;
    private final AuthorizationService authorizationService;

    /** Backward-compatible name used by read paths; it now means readable. */
    public ProjectDO requireProject(Long projectId) {
        return requireProjectReadable(projectId);
    }

    public ProjectDO requireProjectReadable(Long projectId) {
        ProjectDO project = projectMapper.selectById(projectId);
        if (!ProjectPermissionPolicy.isProjectReadable(project)) throw BusinessException.notFound("项目不存在");
        // Internal seed/recalculation flows do not run in an HTTP user context;
        // controller-level authentication still protects every external path.
        if (UserContext.get() == null) return project;
        if (!authorizationService.has(PermissionCode.PROJECT_READ)) throw BusinessException.forbidden("无权查看此项目");
        return project;
    }

    /** Loads a terminated or soft-deleted project for an authorized restore flow. */
    public ProjectDO requireProjectForRestore(Long projectId) {
        ProjectDO project = projectMapper.selectIncludingDeleted(projectId);
        if (project == null) throw BusinessException.notFound("项目不存在");
        if (!authorizationService.has(PermissionCode.PROJECT_READ)
                || !ProjectPermissionPolicy.isProjectRestorable(project)
                || !canManageProject(project)) {
            throw BusinessException.forbidden("当前用户没有恢复此项目的权限");
        }
        return project;
    }

    /** Validates the organization selected for a new project. */
    public Long requireProjectCreateOrgUnit(Long requestedOrgUnitId) {
        if (!authorizationService.has(PermissionCode.PROJECT_CREATE)) {
            throw BusinessException.forbidden("当前用户没有创建项目的权限");
        }
        LoginUser current = UserContext.get();
        if (current == null || UserContext.isAdministrator()) return requestedOrgUnitId;
        boolean allCompany = dataScopeResolver.hasAllCompanyScope(current, PermissionCode.PROJECT_CREATE);
        java.util.List<Long> allowed = dataScopeResolver.resolveProjectCreateOrgUnitIds(current, PermissionCode.PROJECT_CREATE);
        Long selected = requestedOrgUnitId;
        if (selected == null) {
            Long primaryOrgUnitId = dataScopeResolver.resolveActivePrimaryOrgUnitId(current);
            if (primaryOrgUnitId != null && (allCompany || allowed.contains(primaryOrgUnitId))) {
                selected = primaryOrgUnitId;
            } else if (allowed.size() == 1) {
                selected = allowed.get(0);
            }
        }
        if (selected == null) throw BusinessException.error("当前用户没有可用于创建项目的主属组织");
        if (!allCompany && !allowed.contains(selected)) {
            throw BusinessException.forbidden("项目组织不在当前用户的创建范围内");
        }
        return selected;
    }

    /** Requires an active project and routine project-write authority. */
    public ProjectDO requireProjectWritable(Long projectId, String action) {
        ProjectDO project = requireProjectReadable(projectId);
        if (!ProjectPermissionPolicy.isProjectOperational(project)) {
            throw BusinessException.forbidden("项目当前状态不允许" + action);
        }
        if (!canWriteProject(project)) {
            throw BusinessException.forbidden("当前用户没有" + action + "的权限");
        }
        return project;
    }

    /** Requires an active project and project-management authority. */
    public ProjectDO requireProjectManageable(Long projectId, String action) {
        ProjectDO project = requireProjectReadable(projectId);
        if (!ProjectPermissionPolicy.isProjectOperational(project)) {
            throw BusinessException.forbidden("项目当前状态不允许" + action);
        }
        if (!canManageProject(project)) {
            throw BusinessException.forbidden("当前用户没有" + action + "的权限");
        }
        return project;
    }

    public ProjectDO requireProjectCommentWritable(Long projectId) {
        ProjectDO project = requireProjectReadable(projectId);
        if (!ProjectPermissionPolicy.isProjectOperational(project)) {
            throw BusinessException.forbidden("项目当前状态不允许发表评论");
        }
        if (!authorizationService.has(PermissionCode.PROJECT_COMMENT_WRITE)) {
            throw BusinessException.forbidden("当前用户没有发表评论的权限");
        }
        return project;
    }

    public ProjectNodeDO requireNode(Long projectId, Long nodeId) {
        ProjectNodeDO node = nodeMapper.selectById(nodeId);
        if (node == null || !Objects.equals(node.getProjectId(), projectId)) {
            throw BusinessException.error("节点不存在");
        }
        return node;
    }

    /** Requires an open project and an unlocked node for a designated reviewer action. */
    public ProjectNodeDO requireReviewableNode(Long projectId, Long nodeId, String action) {
        ProjectDO project = requireProjectReadable(projectId);
        if (!ProjectPermissionPolicy.isProjectOperational(project)) {
            throw BusinessException.forbidden("项目当前状态不允许" + action);
        }
        ProjectNodeDO node = requireNode(projectId, nodeId);
        if (NodeStatus.isReadOnly(node.getStatus())) {
            throw BusinessException.forbidden("节点已锁定，回滚后才可以" + action);
        }
        return node;
    }

    public void requireProjectMember(Long projectId, Long userId) {
        if (userId == null) {
            throw BusinessException.error("项目经理必须是项目成员");
        }
        Long count = memberMapper.selectCount(new LambdaQueryWrapper<com.brad.pms.entity.ProjectMemberDO>()
                .eq(com.brad.pms.entity.ProjectMemberDO::getProjectId, projectId)
                .eq(com.brad.pms.entity.ProjectMemberDO::getUserId, userId));
        if (count == null || count == 0) {
            throw BusinessException.error("项目经理必须是项目成员");
        }
    }

    public void requireProjectManager(Long projectId, String action) {
        requireProjectManageable(projectId, action);
    }

    /** Compatibility alias for routine project-write call sites. */
    public ProjectDO requireManageableProject(Long projectId, String action) {
        return requireProjectWritable(projectId, action);
    }

    public ProjectNodeDO requireManageableNode(Long projectId, Long nodeId, String action) {
        ProjectDO project = requireProjectReadable(projectId);
        ProjectNodeDO node = requireNode(projectId, nodeId);
        if (!ProjectPermissionPolicy.canManageNode(project, node, UserContext.userId(), UserContext.isAdministrator(), canWriteProject(project))) {
            if (NodeStatus.isReadOnly(node.getStatus())) {
                throw BusinessException.forbidden("节点已锁定，回滚后才可以" + action);
            }
            throw BusinessException.forbidden("仅项目创建人、项目经理或节点负责人可以" + action);
        }
        return node;
    }

    public ProjectNodeDO requireCompletableNode(Long projectId, Long nodeId) {
        ProjectDO project = requireProjectReadable(projectId);
        ProjectNodeDO node = requireNode(projectId, nodeId);
        if (!ProjectPermissionPolicy.canCompleteNode(project, node, UserContext.userId(), UserContext.isAdministrator(), canWriteProject(project))) {
            throw BusinessException.forbidden("仅当前节点负责人或项目负责人可以完成进行中的节点");
        }
        return node;
    }

    public ProjectNodeDO requireRollbackableNode(Long projectId, Long nodeId) {
        ProjectDO project = requireProjectReadable(projectId);
        int projectStatus = ProjectStatus.normalize(project.getStatus());
        if (projectStatus != ProjectStatus.ACTIVE.getCode()
                && projectStatus != ProjectStatus.COMPLETED.getCode()) {
            throw BusinessException.forbidden("项目当前状态不允许回滚节点");
        }
        if (!canManageProject(project)) {
            throw BusinessException.forbidden("当前用户没有回滚节点的权限");
        }
        ProjectNodeDO node = requireNode(projectId, nodeId);
        if (!ProjectPermissionPolicy.canRollbackNode(project, node, UserContext.userId(), UserContext.isAdministrator(), canManageProject(project))) {
            throw BusinessException.forbidden("仅项目创建人或项目经理可以回滚已完成节点");
        }
        return node;
    }

    public ProjectPermissionsDTO projectPermissions(ProjectDO project) {
        Long userId = UserContext.userIdOrNull();
        boolean writable = canWriteProject(project);
        boolean manageable = canManageProject(project);
        boolean active = ProjectPermissionPolicy.isProjectOperational(project);
        ProjectPermissionsDTO dto = new ProjectPermissionsDTO();
        dto.setCanManageProject(active && writable);
        dto.setCanManageMembers(active && manageable);
        dto.setCanSetProjectManager(active && manageable);
        dto.setCanAssignNodeOwner(active && manageable);
        dto.setCanTerminateProject(ProjectPermissionPolicy.canTerminateProject(project, userId, UserContext.isAdministrator(), manageable));
        dto.setCanRestoreProject(ProjectPermissionPolicy.isProjectRestorable(project) && manageable);
        dto.setCanDeleteProject(active && manageable);
        dto.setCanWriteComment(ProjectPermissionPolicy.isProjectOperational(project)
                && ProjectPermissionPolicy.isProjectReadable(project)
                && authorizationService.has(PermissionCode.PROJECT_COMMENT_WRITE));
        return dto;
    }

    public NodePermissionsDTO nodePermissions(ProjectDO project, ProjectNodeDO node) {
        Long userId = UserContext.userIdOrNull();
        NodePermissionsDTO dto = new NodePermissionsDTO();
        boolean administrator = UserContext.isAdministrator();
        boolean writable = canWriteProject(project);
        boolean manageable = canManageProject(project);
        dto.setCanEdit(ProjectPermissionPolicy.canManageNode(project, node, userId, administrator, writable));
        dto.setCanManageTasks(ProjectPermissionPolicy.canManageNode(project, node, userId, administrator, writable));
        dto.setCanComplete(ProjectPermissionPolicy.canCompleteNode(project, node, userId, administrator, writable));
        dto.setCanRollback(ProjectPermissionPolicy.canRollbackNode(project, node, userId, administrator, manageable));
        dto.setReadOnly(!ProjectPermissionPolicy.isProjectOpen(project)
                || NodeStatus.isReadOnly(node.getStatus()));
        return dto;
    }

    public TaskPermissionsDTO taskPermissions(ProjectDO project, ProjectNodeDO node, ProjectTaskDO task) {
        Long userId = UserContext.userIdOrNull();
        boolean administrator = UserContext.isAdministrator();
        boolean manager = ProjectPermissionPolicy.canManageTask(project, node, task, userId, administrator, canWriteProject(project));
        boolean assignee = ProjectPermissionPolicy.canEditTaskContent(project, node, task, userId, administrator, canWriteProject(project));
        TaskPermissionsDTO dto = new TaskPermissionsDTO();
        if (node == null) {
            dto.setReadOnly(true);
            return dto;
        }
        dto.setCanEdit(manager || assignee);
        dto.setCanMove(manager);
        dto.setCanDelete(manager);
        dto.setReadOnly(!ProjectPermissionPolicy.isProjectOpen(project)
                || NodeStatus.isReadOnly(node.getStatus()));
        return dto;
    }

    public boolean canWriteProject(ProjectDO project) {
        return project != null && (ProjectPermissionPolicy.hasProjectControl(project, UserContext.userIdOrNull(), UserContext.isAdministrator())
                || hasScopedPermission(project, PermissionCode.PROJECT_WRITE));
    }

    public boolean canWriteProjectOrg(ProjectDO project, Long orgUnitId) {
        if (project == null || orgUnitId == null) return false;
        Long userId = UserContext.userIdOrNull();
        if (UserContext.isAdministrator() || ProjectPermissionPolicy.isProjectManagerOrCreator(project, userId)) return true;
        LoginUser current = UserContext.get();
        return current != null && authorizationService.has(PermissionCode.PROJECT_WRITE)
                && (dataScopeResolver.hasAllCompanyScope(current, PermissionCode.PROJECT_WRITE)
                || dataScopeResolver.resolveOrgUnitIds(current, PermissionCode.PROJECT_WRITE).contains(orgUnitId));
    }

    public boolean canManageProject(ProjectDO project) {
        return project != null && (ProjectPermissionPolicy.hasProjectControl(project, UserContext.userIdOrNull(), UserContext.isAdministrator())
                || hasScopedPermission(project, PermissionCode.PROJECT_MANAGE));
    }

    public boolean canDeleteComment(ProjectDO project, Long authorId) {
        if (project == null || !ProjectPermissionPolicy.isProjectOperational(project)
                || !ProjectPermissionPolicy.isProjectReadable(project)) return false;
        Long currentUserId = UserContext.userIdOrNull();
        if (Objects.equals(currentUserId, authorId)
                && authorizationService.has(PermissionCode.PROJECT_COMMENT_WRITE)) return true;
        return canManageProject(project);
    }

    private boolean hasScopedPermission(ProjectDO project, String permissionCode) {
        LoginUser current = UserContext.get();
        if (current == null || !authorizationService.has(permissionCode)) return false;
        if (UserContext.isAdministrator() || dataScopeResolver.hasAllCompanyScope(current, permissionCode)) return true;
        return project.getOrgUnitId() != null
                && dataScopeResolver.resolveOrgUnitIds(current, permissionCode).contains(project.getOrgUnitId());
    }
}
