package com.brad.pms.ai.command;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.brad.pms.workflow.WorkflowComponentKey;

/** Central metadata for the commands currently implemented by PMS. */
public final class PmsCommandMetadata {

    private PmsCommandMetadata() {
    }

    public static String domainScope(CommandName name) {
        return switch (name) {
            case BATCH_WRITE -> "pms:command:preview";
            case REQUIREMENT_CREATE, REQUIREMENT_UPDATE,
                 REQUIREMENT_EXECUTION_TARGET_LINK, REQUIREMENT_EXECUTION_TARGET_CHANGE,
                 REQUIREMENT_EXECUTION_TARGET_UNLINK -> "pms:requirement:write";
            case FOLLOWER_ADD, FOLLOWER_REMOVE, MEMBER_ADD, MEMBER_REMOVE,
                 PROJECT_ARCHIVE, PROJECT_CREATE, PROJECT_DELETE, PROJECT_UPDATE -> "pms:project:write";
            case NODE_COMPLETE, NODE_ROLLBACK -> "pms:workflow:write";
            case NODE_FIELD_UPDATE, NODE_OWNER_UPDATE, NODE_SCHEDULE_UPDATE -> "pms:workflow:write";
            case TOPIC_CREATE, TOPIC_UPDATE, TOPIC_PROJECT_LINK, STORY_CREATE, STORY_UPDATE,
                 STORY_TOPIC_LINK -> "pms:development:write";
            case DEVELOPMENT_ITEM_NODE_OWNER_UPDATE, DEVELOPMENT_ITEM_NODE_SCHEDULE_UPDATE,
                 DEVELOPMENT_ITEM_NODE_FIELD_UPDATE, DEVELOPMENT_ITEM_NODE_COMPLETE -> "pms:workflow:write";
            case DEVELOPMENT_ITEM_TASK_CREATE -> "pms:development:write";
            case ITERATION_PLAN_CREATE, ITERATION_PLAN_UPDATE, ITERATION_PLAN_STORY_ADD,
                 ITERATION_PLAN_STORY_REMOVE -> "pms:iteration:write";
            case TASK_CREATE, TASK_ASSIGN, TASK_UPDATE -> "pms:task:write";
        };
    }

    /** Resource domains exposed by the command to the dynamic capability catalog. */
    public static List<String> resourceTypes(CommandName name) {
        return switch (name) {
            case REQUIREMENT_CREATE, REQUIREMENT_UPDATE,
                 REQUIREMENT_EXECUTION_TARGET_LINK, REQUIREMENT_EXECUTION_TARGET_CHANGE,
                 REQUIREMENT_EXECUTION_TARGET_UNLINK -> List.of("requirement");
            case TOPIC_CREATE, TOPIC_UPDATE, TOPIC_PROJECT_LINK -> List.of("topic");
            case STORY_CREATE, STORY_UPDATE, STORY_TOPIC_LINK -> List.of("story");
            case DEVELOPMENT_ITEM_NODE_OWNER_UPDATE, DEVELOPMENT_ITEM_NODE_SCHEDULE_UPDATE,
                 DEVELOPMENT_ITEM_NODE_FIELD_UPDATE, DEVELOPMENT_ITEM_NODE_COMPLETE,
                 DEVELOPMENT_ITEM_TASK_CREATE -> List.of("topic", "story", "requirement");
            case ITERATION_PLAN_CREATE, ITERATION_PLAN_UPDATE,
                 ITERATION_PLAN_STORY_ADD, ITERATION_PLAN_STORY_REMOVE -> List.of("iteration_plan");
            case TASK_CREATE, TASK_ASSIGN, TASK_UPDATE -> List.of("task");
            case NODE_COMPLETE, NODE_FIELD_UPDATE, NODE_ROLLBACK,
                 NODE_OWNER_UPDATE, NODE_SCHEDULE_UPDATE -> List.of("project_node");
            case PROJECT_ARCHIVE, PROJECT_CREATE, PROJECT_DELETE, PROJECT_UPDATE,
                 FOLLOWER_ADD, FOLLOWER_REMOVE, MEMBER_ADD, MEMBER_REMOVE, BATCH_WRITE -> List.of("project");
        };
    }

    /**
     * Returns the resource domains a configured runtime component can operate on.
     *
     * The process type is deliberately not part of this mapping. A template may
     * be attached to a newly-created process type, while its runtime component
     * keys still identify the business surface and the command registry remains
     * the source of the available actions.
     */
    public static Set<String> workflowComponentDomains(String componentKey) {
        return switch (componentKey) {
            case WorkflowComponentKey.DEVELOPMENT_CONTROL -> Set.of(
                    "project_node", "topic", "story", "requirement", "task");
            case WorkflowComponentKey.STORY_LIST, WorkflowComponentKey.STORY_SPLIT -> Set.of("story");
            case WorkflowComponentKey.REQUIREMENT_EXECUTION -> Set.of("requirement");
            default -> Set.of();
        };
    }

    /** A workflow component action is discovered from the command registry. */
    public static boolean isWorkflowComponentAction(CommandName name) {
        return resourceTypes(name).stream().anyMatch(Set.of(
                "project_node", "topic", "story", "requirement", "task")::contains);
    }

    public static PmsCommandDescriptor descriptor(CommandName name) {
        return switch (name) {
            case BATCH_WRITE -> new PmsCommandDescriptor(
                    name,
                    "在一次操作中批量执行多条已注册的 PMS 写入命令（例如批量创建任务或成员），失败整体回滚",
                    "write",
                    "high",
                    true,
                    List.of("pms:command:preview", "pms:command:execute"),
                    Map.of("operations", Map.of("type", "array", "required", true,
                            "description", "最多 20 条 {command, arguments} 子操作")),
                    true,
                    true,
                    List.of("project-detail", "project-list", "project-dashboard", "task-board"));
            case REQUIREMENT_CREATE -> descriptor(name, "创建需求并初始化需求流程", "high", List.of(
                    Map.entry("title", Map.of("type", "string", "required", true)),
                    Map.entry("description", Map.of("type", "string")),
                    Map.entry("priority", Map.of("type", "integer", "enum", List.of(0, 1, 2, 3))),
                    Map.entry("ownerId", Map.of("type", "integer")),
                    Map.entry("templateVersionId", Map.of("type", "integer"))));
            case REQUIREMENT_UPDATE -> descriptor(name, "更新需求基本信息", "high", List.of(
                    Map.entry("requirementId", Map.of("type", "integer", "required", true)),
                    Map.entry("version", Map.of("type", "integer", "required", true)),
                    Map.entry("title", Map.of("type", "string", "required", true)),
                    Map.entry("description", Map.of("type", "string")),
                    Map.entry("priority", Map.of("type", "integer", "enum", List.of(0, 1, 2, 3))),
                    Map.entry("ownerId", Map.of("type", "integer"))));
            case REQUIREMENT_EXECUTION_TARGET_LINK, REQUIREMENT_EXECUTION_TARGET_CHANGE -> descriptor(name,
                    name == CommandName.REQUIREMENT_EXECUTION_TARGET_LINK ? "关联需求执行对象" : "更换需求执行对象",
                    "high", List.of(
                    Map.entry("requirementId", Map.of("type", "integer", "required", true)),
                    Map.entry("requirementVersion", Map.of("type", "integer", "required", true)),
                    Map.entry("targetType", Map.of("type", "string", "enum", List.of("PROJECT", "TOPIC", "STORY"), "required", true)),
                    Map.entry("targetId", Map.of("type", "integer", "required", true)),
                    Map.entry("reason", Map.of("type", "string"))));
            case REQUIREMENT_EXECUTION_TARGET_UNLINK -> descriptor(name, "解除需求执行对象关联", "high", List.of(
                    Map.entry("requirementId", Map.of("type", "integer", "required", true)),
                    Map.entry("requirementVersion", Map.of("type", "integer", "required", true)),
                    Map.entry("reason", Map.of("type", "string"))));
            case FOLLOWER_ADD -> descriptor(name, "为项目添加关注人", "medium", List.of(
                    Map.entry("projectId", Map.of("type", "integer", "required", true)),
                    Map.entry("userId", Map.of("type", "integer", "required", true))));
            case FOLLOWER_REMOVE -> descriptor(name, "移除项目关注人", "medium", List.of(
                    Map.entry("projectId", Map.of("type", "integer", "required", true)),
                    Map.entry("userId", Map.of("type", "integer", "required", true))));
            case MEMBER_ADD -> descriptor(name, "向项目添加成员", "medium", List.of(
                    Map.entry("projectId", Map.of("type", "integer", "required", true)),
                    Map.entry("userId", Map.of("type", "integer", "required", true)),
                    Map.entry("role", Map.of("type", "integer", "enum", List.of(1, 2)))));
            case MEMBER_REMOVE -> descriptor(name, "从项目移除成员", "high", List.of(
                    Map.entry("projectId", Map.of("type", "integer", "required", true)),
                    Map.entry("memberId", Map.of("type", "integer")),
                    Map.entry("userId", Map.of("type", "integer"))));
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
            case NODE_FIELD_UPDATE -> new PmsCommandDescriptor(
                    name,
                    "更新节点工作台字段（需求范围、方案设计、计划资源风险、开发测试、验收、发布、价值验证、知识沉淀、自定义字段）",
                    "write",
                    "medium",
                    true,
                    List.of("pms:workflow:write", "pms:command:preview", "pms:command:execute"),
                    Map.ofEntries(
                            Map.entry("projectId", Map.of("type", "integer", "required", true)),
                            Map.entry("nodeId", Map.of("type", "integer", "required", true)),
                            Map.entry("workbench", Map.of("type", "string", "required", true,
                                    "description", "工作台标识：requirement-scope / solution-design / plan-resource-risk / "
                                            + "solution-decision / development-control / business-acceptance / release-handover / "
                                            + "value-review / knowledge-standard / custom-fields；与节点 components 名称一致")),
                            Map.entry("fields", Map.of("type", "object", "required", true,
                                    "description", "仅包含需要修改的字段；未提供的字段保留原值"))),
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
            case PROJECT_CREATE -> descriptor(name, "创建项目并初始化项目流程；priority：3=紧急、2=高、1=普通、0=低；项目负责人与创建人自动为当前登录账号", "high", List.of(
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
                    "更新项目名称、描述、优先级（3=紧急、2=高、1=普通、0=低）、等级、组织单元、项目经理或整体排期",
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
                            Map.entry("projectManagerId", Map.of("type", "integer")),
                            Map.entry("startDate", Map.of("type", "string", "format", "date")),
                            Map.entry("endDate", Map.of("type", "string", "format", "date"))),
                    true,
                    true,
                    List.of("project-detail", "project-list", "project-dashboard"));
            case TOPIC_CREATE -> descriptor(name, "创建专题，可选择关联进行中的项目", "high", List.of(
                    Map.entry("title", Map.of("type", "string", "required", true)),
                    Map.entry("ownerId", Map.of("type", "integer")),
                    Map.entry("projectId", Map.of("type", "integer")),
                    Map.entry("templateVersionId", Map.of("type", "integer"))));
            case TOPIC_UPDATE -> descriptor(name, "更新专题名称、负责人或关联项目", "high", List.of(
                    Map.entry("topicId", Map.of("type", "integer", "required", true)),
                    Map.entry("title", Map.of("type", "string", "required", true)),
                    Map.entry("ownerId", Map.of("type", "integer")),
                    Map.entry("projectId", Map.of("type", "integer"))));
            case TOPIC_PROJECT_LINK -> descriptor(name, "绑定或解除专题与项目的关联", "high", List.of(
                    Map.entry("topicId", Map.of("type", "integer", "required", true)),
                    Map.entry("projectId", Map.of("type", "integer"))));
            case STORY_CREATE -> descriptor(name, "创建故事，可选择关联专题", "high", List.of(
                    Map.entry("title", Map.of("type", "string", "required", true)),
                    Map.entry("topicId", Map.of("type", "integer")),
                    Map.entry("ownerId", Map.of("type", "integer")),
                    Map.entry("templateVersionId", Map.of("type", "integer")),
                    Map.entry("status", Map.of("type", "string")),
                    Map.entry("progress", Map.of("type", "integer")),
                    Map.entry("storyPoints", Map.of("type", "integer")),
                    Map.entry("startDate", Map.of("type", "string", "format", "date")),
                    Map.entry("dueDate", Map.of("type", "string", "format", "date")),
                    Map.entry("blocker", Map.of("type", "string")),
                    Map.entry("sort", Map.of("type", "integer"))));
            case STORY_UPDATE -> descriptor(name, "更新故事基本信息及专题关联", "high", List.of(
                    Map.entry("storyId", Map.of("type", "integer", "required", true)),
                    Map.entry("title", Map.of("type", "string", "required", true)),
                    Map.entry("topicId", Map.of("type", "integer")),
                    Map.entry("ownerId", Map.of("type", "integer")),
                    Map.entry("status", Map.of("type", "string")),
                    Map.entry("progress", Map.of("type", "integer")),
                    Map.entry("storyPoints", Map.of("type", "integer")),
                    Map.entry("startDate", Map.of("type", "string", "format", "date")),
                    Map.entry("dueDate", Map.of("type", "string", "format", "date")),
                    Map.entry("blocker", Map.of("type", "string")),
                    Map.entry("sort", Map.of("type", "integer"))));
            case STORY_TOPIC_LINK -> descriptor(name, "绑定或解除故事与专题的关联", "high", List.of(
                    Map.entry("storyId", Map.of("type", "integer", "required", true)),
                    Map.entry("topicId", Map.of("type", "integer"))));
            case DEVELOPMENT_ITEM_NODE_OWNER_UPDATE -> descriptor(name, "更新研发事项流程节点负责人", "high", List.of(
                    Map.entry("itemType", Map.of("type", "string", "enum", List.of("TOPIC", "STORY", "REQUIREMENT"), "required", true)),
                    Map.entry("itemId", Map.of("type", "integer", "required", true)),
                    Map.entry("nodeId", Map.of("type", "integer", "required", true)),
                    Map.entry("ownerId", Map.of("type", "integer")),
                    Map.entry("version", Map.of("type", "integer", "required", true))));
            case DEVELOPMENT_ITEM_NODE_SCHEDULE_UPDATE -> descriptor(name, "更新研发事项流程节点排期", "medium", List.of(
                    Map.entry("itemType", Map.of("type", "string", "required", true)),
                    Map.entry("itemId", Map.of("type", "integer", "required", true)),
                    Map.entry("nodeId", Map.of("type", "integer", "required", true)),
                    Map.entry("startDate", Map.of("type", "string", "format", "date")),
                    Map.entry("endDate", Map.of("type", "string", "format", "date")),
                    Map.entry("version", Map.of("type", "integer", "required", true))));
            case DEVELOPMENT_ITEM_NODE_FIELD_UPDATE -> descriptor(name, "更新研发事项流程节点动态字段", "medium", List.of(
                    Map.entry("itemType", Map.of("type", "string", "required", true)),
                    Map.entry("itemId", Map.of("type", "integer", "required", true)),
                    Map.entry("nodeId", Map.of("type", "integer", "required", true)),
                    Map.entry("fieldValues", Map.of("type", "object", "required", true)),
                    Map.entry("version", Map.of("type", "integer", "required", true))));
            case DEVELOPMENT_ITEM_NODE_COMPLETE -> descriptor(name, "完成研发事项流程节点并推进流程", "high", List.of(
                    Map.entry("itemType", Map.of("type", "string", "required", true)),
                    Map.entry("itemId", Map.of("type", "integer", "required", true)),
                    Map.entry("nodeId", Map.of("type", "integer", "required", true))));
            case DEVELOPMENT_ITEM_TASK_CREATE -> descriptor(name, "在研发事项流程节点创建任务或子任务", "medium", List.of(
                    Map.entry("itemType", Map.of("type", "string", "required", true)),
                    Map.entry("itemId", Map.of("type", "integer", "required", true)),
                    Map.entry("nodeId", Map.of("type", "integer", "required", true)),
                    Map.entry("parentId", Map.of("type", "integer")),
                    Map.entry("title", Map.of("type", "string", "required", true)),
                    Map.entry("description", Map.of("type", "string")),
                    Map.entry("status", Map.of("type", "integer", "enum", List.of(0, 1, 2))),
                    Map.entry("priority", Map.of("type", "integer", "enum", List.of(0, 1, 2))),
                    Map.entry("assigneeId", Map.of("type", "integer")),
                    Map.entry("dueDate", Map.of("type", "string", "format", "date")),
                    Map.entry("sort", Map.of("type", "integer"))));
            case ITERATION_PLAN_CREATE -> descriptor(name, "在项目节点下创建没有独立流程的迭代计划", "high", List.of(
                    Map.entry("projectId", Map.of("type", "integer", "required", true)),
                    Map.entry("nodeId", Map.of("type", "integer", "required", true)),
                    Map.entry("name", Map.of("type", "string", "required", true)),
                    Map.entry("systemId", Map.of("type", "integer", "description", "无需求系统来源时选择所属系统")),
                    Map.entry("ownerId", Map.of("type", "integer")),
                    Map.entry("goal", Map.of("type", "string")),
                    Map.entry("status", Map.of("type", "string")),
                     Map.entry("startDate", Map.of("type", "string", "format", "date")),
                     Map.entry("dueDate", Map.of("type", "string", "format", "date")),
                     Map.entry("sort", Map.of("type", "integer")),
                     Map.entry("systemVersionId", Map.of("type", "integer"))));
            case ITERATION_PLAN_UPDATE -> descriptor(name, "更新迭代计划基本信息", "high", List.of(
                    Map.entry("iterationPlanId", Map.of("type", "integer", "required", true)),
                    Map.entry("name", Map.of("type", "string", "required", true)),
                    Map.entry("systemId", Map.of("type", "integer")),
                    Map.entry("ownerId", Map.of("type", "integer")),
                    Map.entry("goal", Map.of("type", "string")),
                    Map.entry("status", Map.of("type", "string")),
                     Map.entry("startDate", Map.of("type", "string", "format", "date")),
                     Map.entry("dueDate", Map.of("type", "string", "format", "date")),
                     Map.entry("sort", Map.of("type", "integer")),
                     Map.entry("systemVersionId", Map.of("type", "integer"))));
            case ITERATION_PLAN_STORY_ADD, ITERATION_PLAN_STORY_REMOVE -> descriptor(name,
                    name == CommandName.ITERATION_PLAN_STORY_ADD ? "将故事加入迭代计划" : "将故事移出迭代计划", "medium", List.of(
                    Map.entry("iterationPlanId", Map.of("type", "integer", "required", true)),
                    Map.entry("storyId", Map.of("type", "integer", "required", true))));
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
        List<String> refreshScopes = new java.util.ArrayList<>(List.of(
                "project-detail", "project-list", "project-dashboard", "task-board"));
        switch (name) {
            case REQUIREMENT_CREATE, REQUIREMENT_UPDATE,
                 REQUIREMENT_EXECUTION_TARGET_LINK, REQUIREMENT_EXECUTION_TARGET_CHANGE,
                 REQUIREMENT_EXECUTION_TARGET_UNLINK -> refreshScopes.add("requirement-list");
            case TOPIC_CREATE, TOPIC_UPDATE, TOPIC_PROJECT_LINK,
                 STORY_CREATE, STORY_UPDATE, STORY_TOPIC_LINK -> refreshScopes.add("development-list");
            case ITERATION_PLAN_CREATE, ITERATION_PLAN_UPDATE,
                 ITERATION_PLAN_STORY_ADD, ITERATION_PLAN_STORY_REMOVE -> refreshScopes.add("iteration-plan");
            default -> { }
        }
        return new PmsCommandDescriptor(name, description, "write", risk, true,
                List.of(domainScope(name), "pms:command:preview", "pms:command:execute"),
                schema, true, true, refreshScopes);
    }
}
