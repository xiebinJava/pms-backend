package com.brad.pms.service;

import com.brad.pms.common.TaskScheduleCalculator;
import com.brad.pms.common.enums.NodeStatus;
import com.brad.pms.common.enums.ProjectAttentionSeverity;
import com.brad.pms.common.enums.ProjectAttentionType;
import com.brad.pms.common.enums.ProjectStatus;
import com.brad.pms.common.enums.TaskScheduleState;
import com.brad.pms.common.enums.TaskStatus;
import com.brad.pms.dto.response.ProjectActionItemDTO;
import com.brad.pms.dto.response.ProjectAttentionSummaryDTO;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.dto.response.ProjectPermissionsDTO;
import com.brad.pms.dto.response.ProjectReadinessDTO;
import com.brad.pms.dto.response.WorkbenchActionCenterDTO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeRiskDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectNodeRiskMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Collator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectAttentionService {

    public static final int WORKBENCH_LIMIT = 20;
    private static final int DUE_SOON_DAYS = 7;
    private static final Collator NAME_ORDER = Collator.getInstance(java.util.Locale.CHINA);

    private final ProjectNodeMapper nodeMapper;
    private final ProjectTaskMapper taskMapper;
    private final ProjectNodeRiskMapper riskMapper;

    public ProjectReadinessDTO loadForProject(ProjectDTO project) {
        if (project == null || project.getId() == null) return new ProjectReadinessDTO();
        return buildForProject(project,
                nodeMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.brad.pms.entity.ProjectNodeDO>()
                        .eq(com.brad.pms.entity.ProjectNodeDO::getProjectId, project.getId())
                        .orderByAsc(com.brad.pms.entity.ProjectNodeDO::getSort)),
                taskMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.brad.pms.entity.ProjectTaskDO>()
                        .eq(com.brad.pms.entity.ProjectTaskDO::getProjectId, project.getId())),
                riskMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.brad.pms.entity.ProjectNodeRiskDO>()
                        .eq(com.brad.pms.entity.ProjectNodeRiskDO::getProjectId, project.getId())),
                TaskScheduleCalculator.today());
    }

    public Map<Long, ProjectAttentionSummaryDTO> loadSummaries(List<ProjectDTO> projects) {
        List<Long> projectIds = safe(projects).stream().map(ProjectDTO::getId).filter(Objects::nonNull).distinct().toList();
        if (projectIds.isEmpty()) return Map.of();
        Map<Long, List<ProjectNodeDO>> nodesByProject = nodeMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProjectNodeDO>()
                                .in(ProjectNodeDO::getProjectId, projectIds))
                .stream().collect(Collectors.groupingBy(ProjectNodeDO::getProjectId));
        Map<Long, List<ProjectTaskDO>> tasksByProject = taskMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProjectTaskDO>()
                                .in(ProjectTaskDO::getProjectId, projectIds))
                .stream().collect(Collectors.groupingBy(ProjectTaskDO::getProjectId));
        Map<Long, List<ProjectNodeRiskDO>> risksByProject = riskMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProjectNodeRiskDO>()
                                .in(ProjectNodeRiskDO::getProjectId, projectIds))
                .stream().collect(Collectors.groupingBy(ProjectNodeRiskDO::getProjectId));
        LocalDate today = TaskScheduleCalculator.today();
        return safe(projects).stream().filter(project -> project.getId() != null)
                .collect(Collectors.toMap(ProjectDTO::getId,
                        project -> buildSummary(project, nodesByProject.get(project.getId()),
                                tasksByProject.get(project.getId()), risksByProject.get(project.getId()), today),
                        (left, right) -> left));
    }

    public WorkbenchActionCenterDTO loadForProjects(List<ProjectDTO> projects) {
        List<Long> projectIds = safe(projects).stream().map(ProjectDTO::getId).filter(Objects::nonNull).distinct().toList();
        if (projectIds.isEmpty()) return new WorkbenchActionCenterDTO();
        Map<Long, List<ProjectNodeDO>> nodesByProject = nodeMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProjectNodeDO>()
                                .in(ProjectNodeDO::getProjectId, projectIds))
                .stream().collect(Collectors.groupingBy(ProjectNodeDO::getProjectId));
        Map<Long, List<ProjectTaskDO>> tasksByProject = taskMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProjectTaskDO>()
                                .in(ProjectTaskDO::getProjectId, projectIds))
                .stream().collect(Collectors.groupingBy(ProjectTaskDO::getProjectId));
        Map<Long, List<ProjectNodeRiskDO>> risksByProject = riskMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProjectNodeRiskDO>()
                                .in(ProjectNodeRiskDO::getProjectId, projectIds))
                .stream().collect(Collectors.groupingBy(ProjectNodeRiskDO::getProjectId));
        return buildForProjects(projects, nodesByProject, tasksByProject, risksByProject, TaskScheduleCalculator.today());
    }

    public static ProjectReadinessDTO buildForProject(ProjectDTO project,
                                                      List<ProjectNodeDO> nodes,
                                                      List<ProjectTaskDO> tasks,
                                                      List<ProjectNodeRiskDO> risks,
                                                      LocalDate today) {
        ProjectReadinessDTO result = new ProjectReadinessDTO();
        List<ProjectNodeDO> safeNodes = safe(nodes);
        List<ProjectTaskDO> safeTasks = safe(tasks).stream()
                .filter(task -> !Boolean.TRUE.equals(task.getDeleted()))
                .toList();
        List<ProjectNodeRiskDO> safeRisks = safe(risks);

        int totalNodes = safeNodes.size();
        int completedNodes = (int) safeNodes.stream()
                .filter(node -> Objects.equals(node.getStatus(), NodeStatus.COMPLETED.getCode()))
                .count();
        result.setTotalCount(totalNodes);
        result.setCompletedCount(completedNodes);
        result.setPercent(totalNodes == 0
                ? (ProjectStatus.normalize(project.getStatus()) == ProjectStatus.COMPLETED.getCode() ? 100 : 0)
                : Math.round(completedNodes * 100.0f / totalNodes));

        if (!ProjectStatus.isOpen(project.getStatus())) {
            return result;
        }

        List<ProjectActionItemDTO> items = new ArrayList<>();
        String projectName = text(project.getName(), "未命名项目");
        if (project.getProjectManagerId() == null) {
            items.add(projectAction(project, ProjectAttentionType.PROJECT_MANAGER_MISSING,
                    "项目缺少项目经理", "请设置项目经理", "PROJECT_PROFILE", canSetProjectManager(project)));
        }

        ProjectNodeDO currentNode = safeNodes.stream()
                .filter(node -> Objects.equals(node.getStatus(), NodeStatus.IN_PROGRESS.getCode()))
                .min(nodeOrder())
                .orElse(null);
        if (currentNode != null) {
            addNodeConfigurationItems(items, project, currentNode, true);
        }

        LocalDate safeToday = today == null ? TaskScheduleCalculator.today() : today;
        LocalDate dueSoonLimit = safeToday.plusDays(DUE_SOON_DAYS);
        for (ProjectTaskDO task : safeTasks) {
            if (currentNode == null || !Objects.equals(currentNode.getId(), task.getNodeId())) continue;
            if (Objects.equals(task.getStatus(), TaskStatus.DONE.getCode()) || task.getDueDate() == null) continue;
            TaskScheduleCalculator.TaskScheduleSnapshot snapshot =
                    TaskScheduleCalculator.calculate(task.getStatus(), task.getDueDate(), safeToday);
            ProjectNodeDO node = safeNodes.stream()
                    .filter(candidate -> Objects.equals(candidate.getId(), task.getNodeId()))
                    .findFirst().orElse(null);
            if (snapshot.state() == TaskScheduleState.OVERDUE) {
                items.add(taskAction(project, node, task, ProjectAttentionType.TASK_OVERDUE,
                        "任务已逾期", "请处理逾期任务", "TASK_DETAIL", snapshot.overdueDays()));
            } else if (snapshot.state() == TaskScheduleState.DUE_TODAY) {
                items.add(taskAction(project, node, task, ProjectAttentionType.TASK_DUE_TODAY,
                        "任务今日到期", "请确认今日任务进度", "TASK_DETAIL", 0));
            } else if (!task.getDueDate().isAfter(dueSoonLimit)) {
                items.add(taskAction(project, node, task, ProjectAttentionType.TASK_DUE_SOON,
                        "任务即将到期", "请提前确认任务进度", "TASK_DETAIL", 0));
            }
        }

        safeRisks.stream()
                .filter(risk -> currentNode != null && Objects.equals(currentNode.getId(), risk.getNodeId()))
                .filter(risk -> "HIGH".equalsIgnoreCase(risk.getLevel()))
                .filter(risk -> "OPEN".equalsIgnoreCase(risk.getStatus()))
                .forEach(risk -> {
                    ProjectNodeDO node = safeNodes.stream()
                            .filter(candidate -> Objects.equals(candidate.getId(), risk.getNodeId()))
                            .findFirst().orElse(null);
                    ProjectActionItemDTO item = nodeAction(project, node, ProjectAttentionType.HIGH_RISK_OPEN,
                            "存在高风险问题", text(risk.getTitle(), "未命名风险"), "RISK_DETAIL", true,
                            canManageProject(project));
                    items.add(item);
                });

        items.sort(actionOrder());
        result.setItems(items);
        result.setCriticalCount((int) items.stream().filter(item -> ProjectAttentionSeverity.CRITICAL.name().equals(item.getSeverity())).count());
        result.setWarningCount((int) items.stream().filter(item -> ProjectAttentionSeverity.WARNING.name().equals(item.getSeverity())).count());
        result.setNextAction(items.isEmpty() ? null : items.get(0));
        return result;
    }

    public static ProjectAttentionSummaryDTO buildSummary(ProjectDTO project,
                                                           List<ProjectNodeDO> nodes,
                                                           List<ProjectTaskDO> tasks,
                                                           List<ProjectNodeRiskDO> risks,
                                                           LocalDate today) {
        ProjectReadinessDTO readiness = buildForProject(project, nodes, tasks, risks, today);
        ProjectAttentionSummaryDTO summary = new ProjectAttentionSummaryDTO();
        summary.setCriticalCount(readiness.getCriticalCount());
        summary.setWarningCount(readiness.getWarningCount());
        summary.setOverdueTaskCount((int) readiness.getItems().stream()
                .filter(item -> ProjectAttentionType.TASK_OVERDUE.name().equals(item.getType())).count());
        summary.setCurrentNodeIssueCount((int) readiness.getItems().stream()
                .filter(item -> item.getType() != null && item.getType().startsWith("CURRENT_NODE_"))
                .count());
        return summary;
    }

    public static WorkbenchActionCenterDTO buildForProjects(List<ProjectDTO> projects,
                                                            Map<Long, List<ProjectNodeDO>> nodesByProject,
                                                            Map<Long, List<ProjectTaskDO>> tasksByProject,
                                                            Map<Long, List<ProjectNodeRiskDO>> risksByProject,
                                                            LocalDate today) {
        List<ProjectActionItemDTO> allItems = new ArrayList<>();
        for (ProjectDTO project : safe(projects)) {
            ProjectReadinessDTO readiness = buildForProject(
                    project,
                    value(nodesByProject, project.getId()),
                    value(tasksByProject, project.getId()),
                    value(risksByProject, project.getId()),
                    today);
            allItems.addAll(readiness.getItems());
        }
        allItems.sort(actionOrder());
        WorkbenchActionCenterDTO result = new WorkbenchActionCenterDTO();
        result.setTotalCount(allItems.size());
        result.setCriticalCount((int) allItems.stream().filter(item -> ProjectAttentionSeverity.CRITICAL.name().equals(item.getSeverity())).count());
        result.setWarningCount((int) allItems.stream().filter(item -> ProjectAttentionSeverity.WARNING.name().equals(item.getSeverity())).count());
        result.setItems(allItems.stream().limit(WORKBENCH_LIMIT).toList());
        return result;
    }

    private static void addNodeConfigurationItems(List<ProjectActionItemDTO> items, ProjectDTO project,
                                                   ProjectNodeDO node, boolean current) {
        String prefix = current ? "CURRENT_NODE_" : "FUTURE_NODE_";
        if (node.getOwnerId() == null) {
            items.add(nodeAction(project, node, type(prefix + "OWNER_MISSING"),
                    current ? "当前节点缺少负责人" : "后续节点缺少负责人",
                    "请设置节点负责人", "NODE_OWNER", current, canAssignNodeOwner(project)));
        }
        if (node.getStartDate() == null || node.getEndDate() == null) {
            items.add(nodeAction(project, node, type(prefix + "SCHEDULE_MISSING"),
                    current ? "当前节点缺少排期" : "后续节点缺少排期",
                    "请补充节点开始和结束日期", "NODE_SCHEDULE", current, canManageProject(project)));
        }
    }

    private static ProjectActionItemDTO projectAction(ProjectDTO project, ProjectAttentionType type,
                                                      String title, String description, String target,
                                                      boolean canAct) {
        ProjectActionItemDTO item = base(project, type, title, description, target, canAct);
        item.setSeverity(ProjectAttentionSeverity.CRITICAL.name());
        return item;
    }

    private static ProjectActionItemDTO nodeAction(ProjectDTO project, ProjectNodeDO node,
                                                   ProjectAttentionType type, String title, String description,
                                                   String target, boolean current, boolean canAct) {
        ProjectActionItemDTO item = base(project, type, title, description, target, canAct);
        if (node != null) {
            item.setNodeId(node.getId());
            item.setNodeName(node.getName());
            item.setNodeSort(node.getSort());
        }
        item.setSeverity(current || type == ProjectAttentionType.HIGH_RISK_OPEN
                ? ProjectAttentionSeverity.CRITICAL.name() : ProjectAttentionSeverity.WARNING.name());
        return item;
    }

    private static ProjectActionItemDTO taskAction(ProjectDTO project, ProjectNodeDO node, ProjectTaskDO task,
                                                   ProjectAttentionType type, String title, String description,
                                                   String target, int overdueDays) {
        ProjectActionItemDTO item = nodeAction(project, node, type, title, description, target, true,
                canEditTask(project, task));
        item.setTaskId(task.getId());
        item.setTaskName(task.getTitle());
        item.setDueDate(task.getDueDate());
        item.setOverdueDays(overdueDays);
        if (type != ProjectAttentionType.TASK_OVERDUE && type != ProjectAttentionType.TASK_DUE_TODAY) {
            item.setSeverity(ProjectAttentionSeverity.WARNING.name());
        }
        return item;
    }

    private static ProjectActionItemDTO base(ProjectDTO project, ProjectAttentionType type, String title,
                                             String description, String target, boolean canAct) {
        ProjectActionItemDTO item = new ProjectActionItemDTO();
        item.setType(type.name());
        item.setProjectId(project.getId());
        item.setProjectName(text(project.getName(), "未命名项目"));
        item.setTitle(title);
        item.setDescription(description);
        item.setActionLabel(canAct ? "去处理" : "查看详情");
        item.setActionTarget(target);
        item.setCanAct(canAct);
        return item;
    }

    private static Comparator<ProjectNodeDO> nodeOrder() {
        return Comparator.comparing(ProjectNodeDO::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ProjectNodeDO::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Comparator<ProjectActionItemDTO> actionOrder() {
        return Comparator.comparingInt((ProjectActionItemDTO item) -> priority(item.getType()))
                .thenComparing(ProjectActionItemDTO::getOverdueDays, Comparator.reverseOrder())
                .thenComparing(ProjectActionItemDTO::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ProjectActionItemDTO::getNodeSort, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(item -> text(item.getProjectName(), ""), NAME_ORDER)
                .thenComparing(item -> text(item.getTitle(), ""), NAME_ORDER);
    }

    private static int priority(String type) {
        if (ProjectAttentionType.TASK_OVERDUE.name().equals(type)) return 10;
        if (ProjectAttentionType.TASK_DUE_TODAY.name().equals(type)) return 20;
        if (ProjectAttentionType.CURRENT_NODE_OWNER_MISSING.name().equals(type)) return 30;
        if (ProjectAttentionType.CURRENT_NODE_SCHEDULE_MISSING.name().equals(type)) return 40;
        if (ProjectAttentionType.HIGH_RISK_OPEN.name().equals(type)) return 60;
        if (ProjectAttentionType.PROJECT_MANAGER_MISSING.name().equals(type)) return 70;
        if (ProjectAttentionType.TASK_DUE_SOON.name().equals(type)) return 80;
        return 90;
    }

    private static ProjectAttentionType type(String name) {
        return ProjectAttentionType.valueOf(name);
    }

    private static boolean canSetProjectManager(ProjectDTO project) {
        ProjectPermissionsDTO permissions = project.getPermissions();
        return permissions == null || permissions.isCanSetProjectManager();
    }

    private static boolean canAssignNodeOwner(ProjectDTO project) {
        ProjectPermissionsDTO permissions = project.getPermissions();
        return permissions == null || permissions.isCanAssignNodeOwner();
    }

    private static boolean canManageProject(ProjectDTO project) {
        ProjectPermissionsDTO permissions = project.getPermissions();
        return permissions == null || permissions.isCanManageProject();
    }

    private static boolean canEditTask(ProjectDTO project, ProjectTaskDO task) {
        if (canManageProject(project)) return true;
        Long currentUserId = UserContext.userIdOrNull();
        return currentUserId != null && Objects.equals(currentUserId, task.getAssigneeId());
    }

    private static <T> List<T> safe(Collection<T> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }

    private static <T> List<T> value(Map<Long, List<T>> values, Long key) {
        return values == null || key == null ? List.of() : values.getOrDefault(key, List.of());
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
