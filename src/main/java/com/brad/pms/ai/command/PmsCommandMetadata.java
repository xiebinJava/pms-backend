package com.brad.pms.ai.command;

import java.util.List;
import java.util.Map;

/** Central metadata for the commands currently implemented by PMS. */
public final class PmsCommandMetadata {

    private PmsCommandMetadata() {
    }

    public static String domainScope(CommandName name) {
        return switch (name) {
            case MEMBER_ADD, MEMBER_REMOVE, PROJECT_ARCHIVE, PROJECT_CREATE, PROJECT_DELETE, PROJECT_UPDATE -> "pms:project:write";
            case NODE_COMPLETE, NODE_ROLLBACK -> "pms:workflow:write";
            case NODE_OWNER_UPDATE, NODE_SCHEDULE_UPDATE -> "pms:workflow:write";
            case TASK_CREATE, TASK_ASSIGN, TASK_UPDATE -> "pms:task:write";
        };
    }

    public static PmsCommandDescriptor descriptor(CommandName name) {
        return switch (name) {
            case MEMBER_ADD -> descriptor(name, "向项目添加成员", "medium", List.of(
                    Map.entry("projectId", Map.of("type", "integer", "required", true)),
                    Map.entry("userId", Map.of("type", "integer", "required", true)),
                    Map.entry("role", Map.of("type", "integer", "enum", List.of(1, 2)))));
            case MEMBER_REMOVE -> descriptor(name, "从项目移除成员", "high", List.of(
                    Map.entry("projectId", Map.of("type", "integer", "required", true)),
                    Map.entry("memberId", Map.of("type", "integer", "required", true))));
            case NODE_COMPLETE -> new PmsCommandDescriptor(
                    name,
                    "完成指定项目节点并推进项目流程",
                    "write",
                    "high",
                    true,
                    List.of("pms:workflow:write", "pms:command:preview", "pms:command:execute"),
                    Map.of(
                            "projectId", Map.of("type", "integer", "required", true),
                            "nodeId", Map.of("type", "integer", "required", true)),
                    true,
                    true,
                    List.of("project-detail", "project-dashboard", "task-board"));
            case NODE_ROLLBACK -> new PmsCommandDescriptor(
                    name,
                    "将项目流程回退到指定的已完成节点",
                    "write",
                    "high",
                    true,
                    List.of("pms:workflow:write", "pms:command:preview", "pms:command:execute"),
                    Map.of(
                            "projectId", Map.of("type", "integer", "required", true),
                            "nodeId", Map.of("type", "integer", "required", true),
                            "reason", Map.of("type", "string", "required", true)),
                    true,
                    true,
                    List.of("project-detail", "project-dashboard", "task-board"));
            case NODE_OWNER_UPDATE -> descriptor(name, "更新项目节点负责人", "high", List.of(
                    Map.entry("projectId", Map.of("type", "integer", "required", true)),
                    Map.entry("nodeId", Map.of("type", "integer", "required", true)),
                    Map.entry("ownerId", Map.of("type", "integer", "required", true))));
            case NODE_SCHEDULE_UPDATE -> descriptor(name, "更新项目节点排期", "medium", List.of(
                    Map.entry("projectId", Map.of("type", "integer", "required", true)),
                    Map.entry("nodeId", Map.of("type", "integer", "required", true)),
                    Map.entry("startDate", Map.of("type", "string", "format", "date")),
                    Map.entry("endDate", Map.of("type", "string", "format", "date"))));
            case PROJECT_ARCHIVE -> descriptor(name, "归档（终止）一个进行中的项目", "high", List.of(
                    Map.entry("projectId", Map.of("type", "integer", "required", true)),
                    Map.entry("reason", Map.of("type", "string", "required", true))));
            case PROJECT_CREATE -> descriptor(name, "创建项目并初始化项目流程", "high", List.of(
                    Map.entry("name", Map.of("type", "string", "required", true)),
                    Map.entry("description", Map.of("type", "string")),
                    Map.entry("priority", Map.of("type", "integer")),
                    Map.entry("projectLevel", Map.of("type", "integer")),
                    Map.entry("projectTypeId", Map.of("type", "integer")),
                    Map.entry("workflowTemplateVersionId", Map.of("type", "integer")),
                    Map.entry("startDate", Map.of("type", "string", "format", "date")),
                    Map.entry("endDate", Map.of("type", "string", "format", "date")),
                    Map.entry("orgUnitId", Map.of("type", "integer"))));
            case PROJECT_DELETE -> descriptor(name, "删除项目（软删除）", "high", List.of(
                    Map.entry("projectId", Map.of("type", "integer", "required", true)),
                    Map.entry("reason", Map.of("type", "string", "required", true))));
            case PROJECT_UPDATE -> new PmsCommandDescriptor(
                    name,
                    "更新项目名称、描述、优先级、等级、组织单元或整体排期",
                    "write",
                    "high",
                    true,
                    List.of("pms:project:write", "pms:command:preview", "pms:command:execute"),
                    Map.ofEntries(
                            Map.entry("projectId", Map.of("type", "integer", "required", true)),
                            Map.entry("name", Map.of("type", "string")),
                            Map.entry("description", Map.of("type", "string")),
                            Map.entry("priority", Map.of("type", "integer")),
                            Map.entry("projectLevel", Map.of("type", "integer")),
                            Map.entry("orgUnitId", Map.of("type", "integer")),
                            Map.entry("startDate", Map.of("type", "string", "format", "date")),
                            Map.entry("endDate", Map.of("type", "string", "format", "date"))),
                    true,
                    true,
                    List.of("project-detail", "project-list", "project-dashboard"));
            case TASK_CREATE -> new PmsCommandDescriptor(
                    name,
                    "在指定项目节点创建任务",
                    "write",
                    "medium",
                    true,
                    List.of("pms:task:write", "pms:command:preview", "pms:command:execute"),
                    Map.of(
                            "projectId", Map.of("type", "integer", "required", true),
                            "nodeId", Map.of("type", "integer", "required", true),
                            "title", Map.of("type", "string", "required", true),
                            "assigneeId", Map.of("type", "integer"),
                            "dueDate", Map.of("type", "string", "format", "date")),
                    true,
                    true,
                    List.of("project-detail", "task-board", "project-dashboard"));
            case TASK_ASSIGN -> new PmsCommandDescriptor(
                    name,
                    "修改任务负责人",
                    "write",
                    "medium",
                    true,
                    List.of("pms:task:write", "pms:command:preview", "pms:command:execute"),
                    Map.of(
                            "taskId", Map.of("type", "integer", "required", true),
                            "assigneeId", Map.of("type", "integer", "required", true),
                            "version", Map.of("type", "integer")),
                    true,
                    true,
                    List.of("project-detail", "task-board", "project-dashboard"));
            case TASK_UPDATE -> new PmsCommandDescriptor(
                    name,
                    "更新任务标题、描述、状态、优先级、负责人或截止日期",
                    "write",
                    "medium",
                    true,
                    List.of("pms:task:write", "pms:command:preview", "pms:command:execute"),
                    Map.ofEntries(
                            Map.entry("taskId", Map.of("type", "integer", "required", true)),
                            Map.entry("version", Map.of("type", "integer")),
                            Map.entry("title", Map.of("type", "string")),
                            Map.entry("description", Map.of("type", "string")),
                            Map.entry("deliverable", Map.of("type", "string")),
                            Map.entry("status", Map.of("type", "integer", "enum", List.of(0, 1, 2))),
                            Map.entry("priority", Map.of("type", "integer")),
                            Map.entry("assigneeId", Map.of("type", "integer")),
                            Map.entry("requirementId", Map.of("type", "integer")),
                            Map.entry("clearRequirement", Map.of("type", "boolean")),
                            Map.entry("sort", Map.of("type", "integer")),
                            Map.entry("dueDate", Map.of("type", "string", "format", "date")),
                            Map.entry("clearDueDate", Map.of("type", "boolean"))),
                    true,
                    true,
                    List.of("project-detail", "task-board", "project-dashboard"));
        };
    }

    private static PmsCommandDescriptor descriptor(CommandName name, String description, String risk,
                                                   List<Map.Entry<String, Map<String, Object>>> parameters) {
        Map<String, Object> schema = new java.util.LinkedHashMap<>();
        parameters.forEach(entry -> schema.put(entry.getKey(), entry.getValue()));
        return new PmsCommandDescriptor(name, description, "write", risk, true,
                List.of(domainScope(name), "pms:command:preview", "pms:command:execute"),
                schema, true, true,
                List.of("project-detail", "project-list", "project-dashboard", "task-board"));
    }
}
