package com.brad.pms.service;

import com.brad.pms.common.enums.NodeStatus;
import com.brad.pms.common.enums.ProjectStatus;
import com.brad.pms.common.enums.TaskStatus;
import com.brad.pms.dto.response.ProjectActionItemDTO;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.dto.response.ProjectReadinessDTO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeRiskDO;
import com.brad.pms.entity.ProjectTaskDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectAttentionServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 17);

    @Test
    void reportsCurrentNodeConfigurationAsCriticalAndFutureNodeConfigurationAsWarning() {
        ProjectDTO project = project();
        project.setProjectManagerId(null);

        ProjectReadinessDTO readiness = ProjectAttentionService.buildForProject(
                project,
                List.of(node(11L, NodeStatus.IN_PROGRESS.getCode(), null, null, null, 0),
                        node(12L, NodeStatus.NOT_STARTED.getCode(), null, null, null, 1)),
                List.of(),
                List.of(),
                TODAY);

        assertEquals(3, readiness.getCriticalCount());
        assertEquals(2, readiness.getWarningCount());
        assertTrue(types(readiness).contains("PROJECT_MANAGER_MISSING"));
        assertTrue(types(readiness).contains("CURRENT_NODE_OWNER_MISSING"));
        assertTrue(types(readiness).contains("CURRENT_NODE_SCHEDULE_MISSING"));
        assertTrue(types(readiness).contains("FUTURE_NODE_OWNER_MISSING"));
        assertTrue(types(readiness).contains("FUTURE_NODE_SCHEDULE_MISSING"));
        assertTrue(types(readiness).stream().noneMatch(type -> type.endsWith("_TASK_MISSING")));
    }

    @Test
    void derivesTaskScheduleActionsAndIgnoresCompletedTasks() {
        ProjectDTO project = project();
        List<ProjectTaskDO> tasks = List.of(
                task(101L, "逾期任务", TaskStatus.TODO.getCode(), LocalDate.of(2026, 9, 14)),
                task(102L, "今日任务", TaskStatus.DOING.getCode(), TODAY),
                task(103L, "即将到期任务", TaskStatus.TODO.getCode(), LocalDate.of(2026, 9, 20)),
                task(104L, "无日期任务", TaskStatus.TODO.getCode(), null),
                task(105L, "已完成旧任务", TaskStatus.DONE.getCode(), LocalDate.of(2026, 9, 10)));

        ProjectReadinessDTO readiness = ProjectAttentionService.buildForProject(
                project,
                List.of(node(11L, NodeStatus.IN_PROGRESS.getCode(), 8L,
                        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 0)),
                tasks,
                List.of(),
                TODAY);

        assertEquals(1, countType(readiness, "TASK_OVERDUE"));
        assertEquals(1, countType(readiness, "TASK_DUE_TODAY"));
        assertEquals(1, countType(readiness, "TASK_DUE_SOON"));
        assertTrue(readiness.getItems().stream().noneMatch(item -> "已完成旧任务".equals(item.getTaskName())));
        assertTrue(readiness.getItems().stream().noneMatch(item -> "无日期任务".equals(item.getTaskName())));
    }

    @Test
    void ordersOverdueBeforeCurrentNodeIssuesAndFutureWarnings() {
        ProjectDTO project = project();
        List<ProjectTaskDO> tasks = List.of(
                task(201L, "逾期 1 天", TaskStatus.TODO.getCode(), LocalDate.of(2026, 9, 16)),
                task(202L, "逾期 3 天", TaskStatus.TODO.getCode(), LocalDate.of(2026, 9, 14)));

        ProjectReadinessDTO readiness = ProjectAttentionService.buildForProject(
                project,
                List.of(node(11L, NodeStatus.IN_PROGRESS.getCode(), null, null, null, 0),
                        node(12L, NodeStatus.NOT_STARTED.getCode(), null, null, null, 1)),
                tasks,
                List.of(),
                TODAY);

        assertEquals("TASK_OVERDUE", readiness.getItems().get(0).getType());
        assertEquals("逾期 3 天", readiness.getItems().get(0).getTaskName());
        assertEquals("TASK_OVERDUE", readiness.getItems().get(1).getType());
        assertEquals("CURRENT_NODE_OWNER_MISSING", readiness.getItems().get(2).getType());
        assertTrue(readiness.getItems().get(readiness.getItems().size() - 1).getType().startsWith("FUTURE_NODE_"));
    }

    @Test
    void reportsOpenHighRiskAsCriticalAction() {
        ProjectReadinessDTO readiness = ProjectAttentionService.buildForProject(
                project(),
                List.of(node(11L, NodeStatus.IN_PROGRESS.getCode(), 8L,
                        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 0)),
                List.of(task(302L, "已有任务", TaskStatus.TODO.getCode(), LocalDate.of(2026, 9, 30))),
                List.of(risk(301L, 11L, "接口风险", "HIGH", "OPEN")),
                TODAY);

        assertEquals(1, countType(readiness, "HIGH_RISK_OPEN"));
        assertEquals("接口风险", readiness.getItems().stream()
                .filter(item -> "HIGH_RISK_OPEN".equals(item.getType()))
                .findFirst().orElseThrow().getDescription());
    }

    private static ProjectDTO project() {
        ProjectDTO project = new ProjectDTO();
        project.setId(1L);
        project.setName("推进中心测试项目");
        project.setPriority(2);
        project.setStatus(ProjectStatus.ACTIVE.getCode());
        project.setProjectManagerId(8L);
        return project;
    }

    private static ProjectNodeDO node(Long id, int status, Long ownerId,
                                      LocalDate startDate, LocalDate endDate, int sort) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(id);
        node.setProjectId(1L);
        node.setName("节点 " + id);
        node.setStatus(status);
        node.setOwnerId(ownerId);
        node.setStartDate(startDate);
        node.setEndDate(endDate);
        node.setSort(sort);
        return node;
    }

    private static ProjectTaskDO task(Long id, String title, int status, LocalDate dueDate) {
        ProjectTaskDO task = new ProjectTaskDO();
        task.setId(id);
        task.setProjectId(1L);
        task.setNodeId(11L);
        task.setTitle(title);
        task.setStatus(status);
        task.setPriority(1);
        task.setDueDate(dueDate);
        task.setDeleted(false);
        return task;
    }

    private static ProjectNodeRiskDO risk(Long id, Long nodeId, String title, String level, String status) {
        ProjectNodeRiskDO risk = new ProjectNodeRiskDO();
        risk.setId(id);
        risk.setProjectId(1L);
        risk.setNodeId(nodeId);
        risk.setTitle(title);
        risk.setLevel(level);
        risk.setStatus(status);
        return risk;
    }

    private static long countType(ProjectReadinessDTO readiness, String type) {
        return readiness.getItems().stream().filter(item -> type.equals(item.getType())).count();
    }

    private static List<String> types(ProjectReadinessDTO readiness) {
        return readiness.getItems().stream()
                .map(ProjectActionItemDTO::getType)
                .toList();
    }
}
