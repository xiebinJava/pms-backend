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
import com.brad.pms.security.ProjectPermissionPolicy;
import com.brad.pms.security.UserContext;
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

    public ProjectDO requireProject(Long projectId) {
        ProjectDO project = projectMapper.selectById(projectId);
        if (project == null) throw BusinessException.error("项目不存在");
        return project;
    }

    public ProjectNodeDO requireNode(Long projectId, Long nodeId) {
        ProjectNodeDO node = nodeMapper.selectById(nodeId);
        if (node == null || !Objects.equals(node.getProjectId(), projectId)) {
            throw BusinessException.error("节点不存在");
        }
        return node;
    }

    public void requireProjectMember(Long projectId, Long userId) {
        if (userId == null) {
            throw BusinessException.error("项目经理必须是项目成员");
        }
        Integer count = memberMapper.selectCount(new LambdaQueryWrapper<com.brad.pms.entity.ProjectMemberDO>()
                .eq(com.brad.pms.entity.ProjectMemberDO::getProjectId, projectId)
                .eq(com.brad.pms.entity.ProjectMemberDO::getUserId, userId));
        if (count == null || count == 0) {
            throw BusinessException.error("项目经理必须是项目成员");
        }
    }

    public void requireProjectManager(Long projectId, String action) {
        ProjectDO project = requireProject(projectId);
        if (!ProjectPermissionPolicy.isProjectManagerOrCreator(project, UserContext.userId())) {
            throw BusinessException.forbidden("仅项目创建人或项目经理可以" + action);
        }
    }

    public ProjectDO requireManageableProject(Long projectId, String action) {
        ProjectDO project = requireProject(projectId);
        if (!ProjectPermissionPolicy.canManageProject(project, UserContext.userId())) {
            if (!ProjectPermissionPolicy.isProjectOpen(project)) {
                throw BusinessException.forbidden("项目当前状态不允许" + action);
            }
            throw BusinessException.forbidden("仅项目创建人或项目经理可以" + action);
        }
        return project;
    }

    public ProjectNodeDO requireManageableNode(Long projectId, Long nodeId, String action) {
        ProjectDO project = requireProject(projectId);
        ProjectNodeDO node = requireNode(projectId, nodeId);
        if (!ProjectPermissionPolicy.canManageNode(project, node, UserContext.userId())) {
            if (NodeStatus.isReadOnly(node.getStatus())) {
                throw BusinessException.forbidden("节点已锁定，回滚后才可以" + action);
            }
            throw BusinessException.forbidden("仅项目创建人、项目经理或节点负责人可以" + action);
        }
        return node;
    }

    public ProjectNodeDO requireCompletableNode(Long projectId, Long nodeId) {
        ProjectDO project = requireProject(projectId);
        ProjectNodeDO node = requireNode(projectId, nodeId);
        if (!ProjectPermissionPolicy.canCompleteNode(project, node, UserContext.userId())) {
            throw BusinessException.forbidden("仅当前节点负责人或项目负责人可以完成进行中的节点");
        }
        return node;
    }

    public ProjectNodeDO requireRollbackableNode(Long projectId, Long nodeId) {
        ProjectDO project = requireProject(projectId);
        ProjectNodeDO node = requireNode(projectId, nodeId);
        if (!ProjectPermissionPolicy.canRollbackNode(project, node, UserContext.userId())) {
            throw BusinessException.forbidden("仅项目创建人或项目经理可以回滚已完成节点");
        }
        return node;
    }

    public ProjectPermissionsDTO projectPermissions(ProjectDO project) {
        Long userId = UserContext.userId();
        boolean manager = ProjectPermissionPolicy.isProjectManagerOrCreator(project, userId);
        boolean active = ProjectPermissionPolicy.isProjectOpen(project);
        ProjectPermissionsDTO dto = new ProjectPermissionsDTO();
        dto.setCanManageProject(active && manager);
        dto.setCanManageMembers(active && manager);
        dto.setCanSetProjectManager(active && manager);
        dto.setCanAssignNodeOwner(active && manager);
        dto.setCanTerminateProject(ProjectPermissionPolicy.canTerminateProject(project, userId));
        dto.setCanRestoreProject(manager && Objects.equals(project.getStatus(), ProjectStatus.TERMINATED.getCode()));
        dto.setCanDeleteProject(active && manager);
        return dto;
    }

    public NodePermissionsDTO nodePermissions(ProjectDO project, ProjectNodeDO node) {
        Long userId = UserContext.userId();
        NodePermissionsDTO dto = new NodePermissionsDTO();
        dto.setCanEdit(ProjectPermissionPolicy.canManageNode(project, node, userId));
        dto.setCanManageTasks(ProjectPermissionPolicy.canManageNode(project, node, userId));
        dto.setCanComplete(ProjectPermissionPolicy.canCompleteNode(project, node, userId));
        dto.setCanRollback(ProjectPermissionPolicy.canRollbackNode(project, node, userId));
        dto.setReadOnly(!ProjectPermissionPolicy.isProjectOpen(project)
                || NodeStatus.isReadOnly(node.getStatus()));
        return dto;
    }

    public TaskPermissionsDTO taskPermissions(ProjectDO project, ProjectNodeDO node, ProjectTaskDO task) {
        Long userId = UserContext.userId();
        boolean manager = ProjectPermissionPolicy.canManageTask(project, node, task, userId);
        boolean assignee = ProjectPermissionPolicy.canEditTaskContent(project, node, task, userId);
        TaskPermissionsDTO dto = new TaskPermissionsDTO();
        if (node == null) {
            dto.setReadOnly(true);
            return dto;
        }
        dto.setCanEdit(manager || assignee);
        dto.setCanMove(manager || assignee);
        dto.setCanDelete(manager);
        dto.setReadOnly(!ProjectPermissionPolicy.isProjectOpen(project)
                || NodeStatus.isReadOnly(node.getStatus()));
        return dto;
    }
}
