package com.brad.pms.security;

import com.brad.pms.common.enums.NodeStatus;
import com.brad.pms.common.enums.ProjectStatus;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectTaskDO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectPermissionPolicyTest {

    @Test
    void exposesTheCompleteProjectLifecycleContract() {
        assertEquals(ProjectStatus.ACTIVE.getCode(), ProjectStatus.normalize(0));
        assertEquals("进行中", ProjectStatus.labelOf(0));
        assertEquals("进行中", ProjectStatus.labelOf(ProjectStatus.ACTIVE.getCode()));
        assertEquals("已完成", ProjectStatus.labelOf(ProjectStatus.COMPLETED.getCode()));
        assertEquals("已终止", ProjectStatus.labelOf(ProjectStatus.TERMINATED.getCode()));
        assertEquals("已删除", ProjectStatus.labelOf(ProjectStatus.DELETED.getCode()));
        assertEquals("未开始", NodeStatus.labelOf(NodeStatus.NOT_STARTED.getCode()));
        assertEquals("已终止", NodeStatus.labelOf(NodeStatus.TERMINATED.getCode()));
    }

    @Test
    void creatorAndProjectManagerHaveProjectLevelControl() {
        ProjectDO project = project(10L, 20L, ProjectStatus.ACTIVE.getCode());

        assertTrue(ProjectPermissionPolicy.isProjectManagerOrCreator(project, 10L));
        assertTrue(ProjectPermissionPolicy.isProjectManagerOrCreator(project, 20L));
        assertFalse(ProjectPermissionPolicy.isProjectManagerOrCreator(project, 30L));
        assertTrue(ProjectPermissionPolicy.canManageProject(project, 20L));
        assertFalse(ProjectPermissionPolicy.canManageProject(
                project(10L, 20L, ProjectStatus.COMPLETED.getCode()), 20L));
        assertTrue(ProjectPermissionPolicy.canRollbackNode(
                project(10L, 20L, ProjectStatus.COMPLETED.getCode()),
                node(100L, 10L, 30L, NodeStatus.COMPLETED.getCode()), 20L));
    }

    @Test
    void legacyZeroStatusProjectsBehaveAsActiveProjects() {
        ProjectDO legacy = project(10L, 20L, 0);

        assertTrue(ProjectPermissionPolicy.canManageProject(legacy, 10L));
        assertTrue(ProjectPermissionPolicy.canManageProject(legacy, 20L));
        assertTrue(ProjectPermissionPolicy.canTerminateProject(legacy, 20L));
        assertFalse(ProjectPermissionPolicy.canManageProject(
                project(10L, 20L, ProjectStatus.DELETED.getCode()), 20L));
    }

    @Test
    void readAndOperationalLifecycleStatesAreDistinct() {
        assertTrue(ProjectPermissionPolicy.isProjectReadable(
                project(10L, 20L, ProjectStatus.ACTIVE.getCode())));
        assertTrue(ProjectPermissionPolicy.isProjectReadable(
                project(10L, 20L, ProjectStatus.TERMINATED.getCode())));
        assertFalse(ProjectPermissionPolicy.isProjectReadable(
                project(10L, 20L, ProjectStatus.DELETED.getCode())));
        assertTrue(ProjectPermissionPolicy.isProjectOperational(
                project(10L, 20L, ProjectStatus.ACTIVE.getCode())));
        assertFalse(ProjectPermissionPolicy.isProjectOperational(
                project(10L, 20L, ProjectStatus.TERMINATED.getCode())));
        assertTrue(ProjectPermissionPolicy.isProjectRestorable(
                project(10L, 20L, ProjectStatus.TERMINATED.getCode())));
        assertTrue(ProjectPermissionPolicy.isProjectRestorable(
                project(10L, 20L, ProjectStatus.DELETED.getCode())));
    }

    @Test
    void nodeOwnerCanOperateOpenNodeButCannotControlProjectLifecycle() {
        ProjectDO project = project(10L, 20L, ProjectStatus.ACTIVE.getCode());
        ProjectNodeDO node = node(100L, 10L, 30L, NodeStatus.IN_PROGRESS.getCode());

        assertTrue(ProjectPermissionPolicy.canManageNode(project, node, 30L));
        assertTrue(ProjectPermissionPolicy.canCompleteNode(project, node, 30L));
        assertFalse(ProjectPermissionPolicy.canRollbackNode(project, node, 30L));
        assertFalse(ProjectPermissionPolicy.canTerminateProject(project, 30L));
        assertFalse(ProjectPermissionPolicy.canManageNode(
                project, node(100L, 10L, 30L, NodeStatus.COMPLETED.getCode()), 30L));
    }

    @Test
    void taskAssigneeCanEditOwnOpenTaskButCannotReassignOrDeleteIt() {
        ProjectDO project = project(10L, 20L, ProjectStatus.ACTIVE.getCode());
        ProjectNodeDO node = node(100L, 10L, 30L, NodeStatus.IN_PROGRESS.getCode());
        ProjectTaskDO task = new ProjectTaskDO();
        task.setProjectId(10L);
        task.setNodeId(100L);
        task.setAssigneeId(40L);

        assertTrue(ProjectPermissionPolicy.canEditTaskContent(project, node, task, 40L));
        assertFalse(ProjectPermissionPolicy.canManageTask(project, node, task, 40L));
        assertFalse(ProjectPermissionPolicy.canEditTaskContent(
                project, node(100L, 10L, 30L, NodeStatus.COMPLETED.getCode()), task, 40L));
        task.setAssigneeId(20L);
        assertTrue(ProjectPermissionPolicy.canManageTask(project, node, task, 20L));
        assertTrue(ProjectPermissionPolicy.canEditTaskContent(project, node, task, 20L));
    }

    @Test
    void systemAdministratorHasFullControlWithinOpenProject() {
        ProjectDO project = project(10L, 20L, ProjectStatus.ACTIVE.getCode());
        ProjectNodeDO node = node(100L, 10L, 30L, NodeStatus.IN_PROGRESS.getCode());
        ProjectTaskDO task = new ProjectTaskDO();
        task.setProjectId(10L);
        task.setNodeId(100L);
        task.setAssigneeId(40L);

        assertTrue(ProjectPermissionPolicy.canManageProject(project, 99L, true));
        assertTrue(ProjectPermissionPolicy.canManageNode(project, node, 99L, true));
        assertTrue(ProjectPermissionPolicy.canCompleteNode(project, node, 99L, true));
        assertTrue(ProjectPermissionPolicy.canManageTask(project, node, task, 99L, true));
        assertTrue(ProjectPermissionPolicy.canEditTaskContent(project, node, task, 99L, true));
        assertTrue(ProjectPermissionPolicy.canTerminateProject(project, 99L, true));
        assertTrue(ProjectPermissionPolicy.canRollbackNode(
                project,
                node(100L, 10L, 30L, NodeStatus.COMPLETED.getCode()),
                99L,
                true));
    }

    private ProjectDO project(Long creatorId, Long managerId, Integer status) {
        ProjectDO project = new ProjectDO();
        project.setCreatedBy(creatorId);
        project.setProjectManagerId(managerId);
        project.setStatus(status);
        return project;
    }

    private ProjectNodeDO node(Long id, Long projectId, Long ownerId, Integer status) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(id);
        node.setProjectId(projectId);
        node.setOwnerId(ownerId);
        node.setStatus(status);
        return node;
    }
}
