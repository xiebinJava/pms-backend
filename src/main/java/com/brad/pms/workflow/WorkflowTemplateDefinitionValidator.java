package com.brad.pms.workflow;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class WorkflowTemplateDefinitionValidator {
    private static final Pattern KEY_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z0-9_-]{0,63}");
    private static final Set<String> SUPPORTED_COMPONENTS = Set.of(
            WorkflowComponentKey.PROJECT_BASIC_INFO, WorkflowComponentKey.REQUIREMENT_SCOPE,
            WorkflowComponentKey.SOLUTION_DESIGN, WorkflowComponentKey.PLAN_RESOURCE_RISK,
            WorkflowComponentKey.DEVELOPMENT_CONTROL, WorkflowComponentKey.BUSINESS_ACCEPTANCE,
            WorkflowComponentKey.RELEASE_HANDOVER, WorkflowComponentKey.VALUE_REVIEW,
            WorkflowComponentKey.KNOWLEDGE_STANDARD, WorkflowComponentKey.STORY_LIST,
            WorkflowComponentKey.REQUIREMENT_EXECUTION, WorkflowComponentKey.REQUIREMENT_RECEIVING_ANALYSIS,
            WorkflowComponentKey.REQUIREMENT_NODE_WORKBENCH, WorkflowComponentKey.TOPIC_RESEARCH, WorkflowComponentKey.TOPIC_DESIGN_REVIEW,
            WorkflowComponentKey.STORY_NODE_WORKBENCH, WorkflowComponentKey.STORY_TESTING);
    private static final Set<String> RECEIVING_CONFIG_KEYS = Set.of(
            "showFilter", "showAnalysis", "showDecision", "requireCategory", "showFeasibilityScore",
            "requireFeasibilityScore", "showRoiScore", "requireRoiScore", "showStrategicFitScore",
            "requireStrategicFitScore", "requireAnalysisConclusion", "allowReject");
    private static final Set<String> STORY_WORKBENCH_CONFIG_KEYS = Set.of(
            "nodeKey", "nodeName", "variant", "purpose", "activities");
    private static final Map<String, String> STORY_VARIANT_NODE_MATCH = Map.of(
            "writing", "写卡",
            "iteration", "迭代",
            "development", "开发",
            "acceptance", "验收",
            "release", "发布",
            "launch", "上线");
    private static final Set<String> PROJECT_BASIC_INFO_FIELDS = Set.of("description", "priority", "projectLevel",
            "schedule", "businessLine", "projectManager", "projectMembers", "followers");
    private static final Set<WorkflowFieldType> V1_FIELD_TYPES = Set.of(
            WorkflowFieldType.TEXT, WorkflowFieldType.TEXTAREA, WorkflowFieldType.NUMBER, WorkflowFieldType.DATE,
            WorkflowFieldType.SINGLE_SELECT, WorkflowFieldType.MULTI_SELECT, WorkflowFieldType.PERSON,
            WorkflowFieldType.ATTACHMENT);
    private static final Map<String, WorkflowFieldType> BINDING_TYPES = Map.ofEntries(
            Map.entry("project.description", WorkflowFieldType.TEXTAREA),
            Map.entry("project.priority", WorkflowFieldType.RADIO),
            Map.entry("project.projectLevel", WorkflowFieldType.SINGLE_SELECT),
            Map.entry("project.schedule", WorkflowFieldType.DATE_RANGE),
            Map.entry("project.businessLine", WorkflowFieldType.SINGLE_SELECT),
            Map.entry("project.projectManager", WorkflowFieldType.PERSON),
            Map.entry("project.projectMembers", WorkflowFieldType.PERSON_MULTI),
            Map.entry("project.followers", WorkflowFieldType.PERSON_MULTI),
            Map.entry("requirement.title", WorkflowFieldType.TEXT),
            Map.entry("requirement.description", WorkflowFieldType.TEXTAREA),
            Map.entry("requirement.priority", WorkflowFieldType.SINGLE_SELECT),
            Map.entry("requirement.businessLine", WorkflowFieldType.SINGLE_SELECT),
            Map.entry("requirement.owner", WorkflowFieldType.PERSON),
            Map.entry("topic.title", WorkflowFieldType.TEXT),
            Map.entry("topic.owner", WorkflowFieldType.PERSON),
            Map.entry("topic.project", WorkflowFieldType.TEXT),
            Map.entry("topic.status", WorkflowFieldType.TEXT),
            Map.entry("topic.progress", WorkflowFieldType.NUMBER),
            Map.entry("topic.latestBuildVersion", WorkflowFieldType.TEXT),
            Map.entry("topic.testStatus", WorkflowFieldType.TEXT),
            Map.entry("story.title", WorkflowFieldType.TEXT),
            Map.entry("story.owner", WorkflowFieldType.PERSON),
            Map.entry("story.status", WorkflowFieldType.TEXT),
            Map.entry("story.progress", WorkflowFieldType.NUMBER),
            Map.entry("story.storyPoints", WorkflowFieldType.NUMBER),
            Map.entry("story.schedule", WorkflowFieldType.DATE_RANGE),
            Map.entry("story.blocker", WorkflowFieldType.TEXTAREA));

    private WorkflowTemplateDefinitionValidator() { }

    public static WorkflowTemplateDefinition validate(WorkflowTemplateDefinition definition) {
        if (definition == null || (definition.schemaVersion() != 1 && definition.schemaVersion() != 2)) {
            throw new IllegalArgumentException("流程模板版本无效");
        }
        if (definition.nodes() == null || definition.nodes().isEmpty()) {
            throw new IllegalArgumentException("流程至少需要一个节点");
        }

        Set<String> nodeKeys = new HashSet<>();
        for (WorkflowNodeDefinition node : definition.nodes()) {
            if (node == null || !validKey(node.key())) {
                throw new IllegalArgumentException("节点标识格式无效");
            }
            if (!nodeKeys.add(node.key())) {
                throw new IllegalArgumentException("节点标识重复: " + node.key());
            }
            if (blank(node.name())) {
                throw new IllegalArgumentException("节点名称不能为空");
            }
            if (definition.schemaVersion() == 1) {
                validateV1Node(node);
            } else {
                validateV2Node(node);
            }
        }
        long receivingComponentCount = definition.nodes().stream()
                .flatMap(node -> node.runtimeComponents().stream())
                .filter(WorkflowComponentKey.REQUIREMENT_RECEIVING_ANALYSIS::equals)
                .count();
        if (receivingComponentCount > 1) {
            throw new IllegalArgumentException("需求接收与分析组件只能配置一次");
        }
        return definition;
    }

    public static WorkflowTemplateDefinition validateForProcessType(
            String processTypeCode, WorkflowTemplateDefinition definition) {
        validate(definition);
        if (!"topic-management".equals(processTypeCode) && definition.nodes().stream()
                .flatMap(node -> node.runtimeComponents().stream()).anyMatch(WorkflowComponentKey.TOPIC_DESIGN_REVIEW::equals)) {
            throw new IllegalArgumentException("方案设计与评审工作台只能配置在专题流程");
        }
        boolean hasTopicResearch = definition.nodes().stream()
                .flatMap(node -> node.runtimeComponents().stream())
                .anyMatch(WorkflowComponentKey.TOPIC_RESEARCH::equals);
        if (hasTopicResearch && !"topic-management".equals(processTypeCode)) {
            throw new IllegalArgumentException("需求调研工作台只能配置在专题流程");
        }
        boolean hasRequirementExecution = definition.nodes().stream()
                .flatMap(node -> node.runtimeComponents().stream())
                .anyMatch(WorkflowComponentKey.REQUIREMENT_EXECUTION::equals);
        if (hasRequirementExecution && !"requirement-management".equals(processTypeCode)) {
            throw new IllegalArgumentException("需求执行对象组件只能配置在需求流程");
        }
        boolean hasRequirementReceivingAnalysis = definition.nodes().stream()
                .flatMap(node -> node.runtimeComponents().stream())
                .anyMatch(WorkflowComponentKey.REQUIREMENT_RECEIVING_ANALYSIS::equals);
        if (hasRequirementReceivingAnalysis && !"requirement-management".equals(processTypeCode)) {
            throw new IllegalArgumentException("需求接收与分析组件只能配置在需求流程");
        }
        boolean hasRequirementNodeWorkbench = definition.nodes().stream()
                .flatMap(node -> node.runtimeComponents().stream())
                .anyMatch(WorkflowComponentKey.REQUIREMENT_NODE_WORKBENCH::equals);
        if (hasRequirementNodeWorkbench && !"requirement-management".equals(processTypeCode)) {
            throw new IllegalArgumentException("需求节点工作台只能配置在需求流程");
        }
        boolean hasStoryList = definition.nodes().stream()
                .flatMap(node -> node.runtimeComponents().stream())
                .anyMatch(WorkflowComponentKey.STORY_LIST::equals);
        if (hasStoryList && !"topic-management".equals(processTypeCode)) {
            throw new IllegalArgumentException("故事列表工作台只能配置在专题流程");
        }
        boolean hasStoryNodeWorkbench = definition.nodes().stream()
                .flatMap(node -> node.runtimeComponents().stream())
                .anyMatch(WorkflowComponentKey.STORY_NODE_WORKBENCH::equals);
        if (hasStoryNodeWorkbench && !"story-management".equals(processTypeCode)) {
            throw new IllegalArgumentException("故事节点工作台只能配置在故事流程");
        }
        boolean hasStoryTesting = definition.nodes().stream()
                .flatMap(node -> node.runtimeComponents().stream())
                .anyMatch(WorkflowComponentKey.STORY_TESTING::equals);
        if (hasStoryTesting && !"story-management".equals(processTypeCode)) {
            throw new IllegalArgumentException("故事测试工作台只能配置在故事流程");
        }
        boolean hasRequirementBindings = definition.nodes().stream()
                .flatMap(node -> node.fields().stream())
                .anyMatch(field -> field.binding() != null && field.binding().startsWith("requirement."));
        if (hasRequirementBindings && !"requirement-management".equals(processTypeCode)) {
            throw new IllegalArgumentException("需求字段绑定只能配置在需求流程");
        }
        if ("requirement-management".equals(processTypeCode)
                && (!blank(definition.sourceProjectNodeKey()) || !blank(definition.sourceTopicNodeKey()))) {
            throw new IllegalArgumentException("需求流程不能配置事项挂载点");
        }
        if ("requirement-management".equals(processTypeCode)) {
            validateRequirementWorkbenchPlacement(definition);
        }
        if ("story-management".equals(processTypeCode)) {
            validateStoryWorkbenchPlacement(definition);
        }
        return definition;
    }

    public static WorkflowTemplateDefinition validateForPublish(
            String processTypeCode, WorkflowTemplateDefinition definition) {
        WorkflowTemplateDefinition validated = validateForProcessType(processTypeCode, definition);
        if ("requirement-management".equals(processTypeCode)) {
            long count = validated.nodes().stream()
                    .flatMap(node -> node.runtimeComponents().stream())
                    .filter(WorkflowComponentKey.REQUIREMENT_RECEIVING_ANALYSIS::equals)
                    .count();
            if (count != 1) {
                throw new IllegalArgumentException("需求模板必须配置一个需求接收与分析组件");
            }
        }
        return validated;
    }

    public static Set<String> supportedComponents() {
        return SUPPORTED_COMPONENTS;
    }

    private static void validateV1Node(WorkflowNodeDefinition node) {
        if (node.components() == null || node.components().stream().anyMatch(c -> !SUPPORTED_COMPONENTS.contains(c))) {
            throw new IllegalArgumentException("存在不支持的工作台组件");
        }
        Set<String> componentIds = new HashSet<>();
        if (node.components().stream().anyMatch(c -> !componentIds.add(c))) {
            throw new IllegalArgumentException("节点工作台组件不能重复");
        }
        if (node.projectBasicInfo() != node.components().contains(WorkflowComponentKey.PROJECT_BASIC_INFO)) {
            throw new IllegalArgumentException("项目信息组件配置不一致");
        }
        validateProjectFields(node);
        validateComponentConfigs(node);
        validateFields(node.fields(), false);
    }

    private static void validateV2Node(WorkflowNodeDefinition node) {
        if (node.components() != null && !node.components().isEmpty()) {
            throw new IllegalArgumentException("v2 节点不能配置旧工作台组件");
        }
        if (node.projectBasicInfo() || (node.projectBasicInfoFields() != null && !node.projectBasicInfoFields().isEmpty())) {
            throw new IllegalArgumentException("v2 节点不能配置旧项目信息字段");
        }
        validateFields(node.fields(), true);
        validateContentOrder(node.contentOrder(), node.fields());
        validateComponentConfigs(node);
    }

    private static void validateFields(List<WorkflowFieldDefinition> fields, boolean v2) {
        if (fields == null) throw new IllegalArgumentException("节点字段定义不能为空");
        Set<String> keys = new HashSet<>();
        for (WorkflowFieldDefinition field : fields) {
            if (field == null || !validKey(field.key())) {
                throw new IllegalArgumentException("字段标识格式无效");
            }
            if (!keys.add(field.key())) {
                throw new IllegalArgumentException("字段标识重复: " + field.key());
            }
            if (blank(field.label()) || field.type() == null) {
                throw new IllegalArgumentException("字段名称和类型不能为空");
            }
            if (!v2 && !V1_FIELD_TYPES.contains(field.type())) {
                throw new IllegalArgumentException("字段类型无效");
            }
            if (!v2 && field.binding() != null) {
                throw new IllegalArgumentException("v1 字段不能配置绑定");
            }
            if (!v2 && field.visible() != null) {
                throw new IllegalArgumentException("v1 字段不能配置显示属性");
            }
            if (v2 && field.required() && Boolean.FALSE.equals(field.visible())) {
                throw new IllegalArgumentException("必填字段必须显示");
            }
            List<String> options = field.options() == null ? List.of() : field.options();
            if (v2 && field.binding() != null) {
                WorkflowFieldType expectedType = BINDING_TYPES.get(field.binding());
                if (expectedType == null) throw new IllegalArgumentException("字段绑定无效");
                boolean legacyRequirementPriority = "requirement.priority".equals(field.binding())
                        && field.type() == WorkflowFieldType.NUMBER;
                if (field.type() != expectedType && !legacyRequirementPriority) {
                    throw new IllegalArgumentException("字段绑定控件类型不匹配");
                }
                if (!options.isEmpty()) throw new IllegalArgumentException("绑定字段不能配置选项");
                continue;
            }
            if (field.type() == WorkflowFieldType.RADIO || field.type() == WorkflowFieldType.SINGLE_SELECT
                    || field.type() == WorkflowFieldType.MULTI_SELECT) {
                if (options.isEmpty() || options.stream().anyMatch(WorkflowTemplateDefinitionValidator::blank)) {
                    throw new IllegalArgumentException("单选或多选字段至少配置一个选项");
                }
                if (new HashSet<>(options).size() != options.size()) {
                    throw new IllegalArgumentException("字段选项不能重复");
                }
            } else if (!options.isEmpty()) {
                throw new IllegalArgumentException("只有单选或多选字段可以配置选项");
            }
        }
    }

    private static void validateContentOrder(List<String> contentOrder, List<WorkflowFieldDefinition> fields) {
        if (contentOrder == null) throw new IllegalArgumentException("节点内容排序不能为空");
        Set<String> entries = new HashSet<>();
        for (String entry : contentOrder) {
            if (entry == null) throw new IllegalArgumentException("节点内容排序存在未知项");
            if (!entries.add(entry)) throw new IllegalArgumentException("节点内容排序不能重复");
            if ("fields".equals(entry) || "legacy-custom-fields".equals(entry)) continue;
            if (!entry.startsWith("component:")) throw new IllegalArgumentException("节点内容排序存在未知项");
            String component = entry.substring("component:".length());
            if (!SUPPORTED_COMPONENTS.contains(component) || WorkflowComponentKey.PROJECT_BASIC_INFO.equals(component)) {
                throw new IllegalArgumentException("节点内容排序存在未知项");
            }
        }
        boolean hasBoundFields = fields.stream().anyMatch(field -> field.binding() != null);
        boolean hasUnboundFields = fields.stream().anyMatch(field -> field.binding() == null);
        boolean hasFieldsSlot = entries.contains("fields");
        boolean hasLegacyCustomFieldsSlot = entries.contains("legacy-custom-fields");

        if ((hasFieldsSlot && fields.isEmpty())
                || (hasLegacyCustomFieldsSlot && !hasUnboundFields)
                || (hasLegacyCustomFieldsSlot && hasFieldsSlot && !hasBoundFields)) {
            throw new IllegalArgumentException("节点内容排序包含空字段区");
        }
        if ((hasBoundFields && !hasFieldsSlot)
                || (hasUnboundFields && !hasLegacyCustomFieldsSlot && !hasFieldsSlot)) {
            throw new IllegalArgumentException("节点内容排序必须包含字段区");
        }
    }

    private static void validateProjectFields(WorkflowNodeDefinition node) {
        List<WorkflowProjectFieldDefinition> fields = node.projectBasicInfoFields();
        if (!node.projectBasicInfo()) {
            if (fields != null && !fields.isEmpty()) throw new IllegalArgumentException("非项目信息节点不能配置项目字段");
            return;
        }
        if (fields == null || fields.isEmpty()) throw new IllegalArgumentException("项目信息组件至少需要一个字段");
        Set<String> keys = new HashSet<>();
        for (WorkflowProjectFieldDefinition field : fields) {
            if (field == null || !PROJECT_BASIC_INFO_FIELDS.contains(field.key())) {
                throw new IllegalArgumentException("不支持的项目信息字段");
            }
            if (!keys.add(field.key())) throw new IllegalArgumentException("项目信息字段不能重复");
            if (blank(field.label())) throw new IllegalArgumentException("项目信息字段名称不能为空");
            if (field.required() && !field.visible()) throw new IllegalArgumentException("必填项目信息字段必须显示");
        }
    }

    private static void validateComponentConfigs(WorkflowNodeDefinition node) {
        if (node.componentConfigs() == null) return;
        Set<String> runtimeComponents = new HashSet<>(node.runtimeComponents());
        for (Map.Entry<String, JsonNode> entry : node.componentConfigs().entrySet()) {
            if (!runtimeComponents.contains(entry.getKey())
                    || !SUPPORTED_COMPONENTS.contains(entry.getKey())) {
                throw new IllegalArgumentException("工作台组件配置无效: " + entry.getKey());
            }
            JsonNode config = entry.getValue();
            if (config == null || !config.isObject()) {
                throw new IllegalArgumentException("工作台组件配置必须是对象: " + entry.getKey());
            }
            if (WorkflowComponentKey.REQUIREMENT_RECEIVING_ANALYSIS.equals(entry.getKey())) {
                config.fieldNames().forEachRemaining(key -> {
                    if (!RECEIVING_CONFIG_KEYS.contains(key)) {
                        throw new IllegalArgumentException("需求接收与分析组件配置项无效: " + key);
                    }
                    if (!config.get(key).isBoolean()) {
                        throw new IllegalArgumentException("需求接收与分析组件配置项必须是布尔值: " + key);
                    }
                });
                if (config.has("showDecision") && !config.path("showDecision").asBoolean()) {
                    throw new IllegalArgumentException("需求接收与分析组件必须显示接收结论区");
                }
                rejectHiddenRequired(config, "showFeasibilityScore", "requireFeasibilityScore");
                rejectHiddenRequired(config, "showRoiScore", "requireRoiScore");
                rejectHiddenRequired(config, "showStrategicFitScore", "requireStrategicFitScore");
            }
            if (WorkflowComponentKey.REQUIREMENT_NODE_WORKBENCH.equals(entry.getKey())) {
                validateRequirementNodeWorkbenchConfig(node, config);
            }
            if (WorkflowComponentKey.STORY_NODE_WORKBENCH.equals(entry.getKey())) {
                validateStoryNodeWorkbenchConfig(node, config);
            }
            if (WorkflowComponentKey.STORY_TESTING.equals(entry.getKey())) {
                validateStoryTestingConfig(config);
            }
        }
    }

    private static void validateRequirementWorkbenchPlacement(WorkflowTemplateDefinition definition) {
        for (int index = 0; index < definition.nodes().size(); index++) {
            WorkflowNodeDefinition node = definition.nodes().get(index);
            Set<String> components = new HashSet<>(node.runtimeComponents());
            boolean hasRequirementWorkbench = components.contains(WorkflowComponentKey.REQUIREMENT_NODE_WORKBENCH)
                    || components.contains(WorkflowComponentKey.REQUIREMENT_RECEIVING_ANALYSIS)
                    || components.contains(WorkflowComponentKey.REQUIREMENT_EXECUTION);
            if (index == 0 && hasRequirementWorkbench) {
                throw new IllegalArgumentException("需求录入节点不能配置需求工作台");
            }
            if (components.contains(WorkflowComponentKey.REQUIREMENT_RECEIVING_ANALYSIS)
                    && !String.valueOf(node.name()).contains("需求接收")) {
                throw new IllegalArgumentException("需求接收与分析组件只能配置在需求接收节点");
            }
            if (components.contains(WorkflowComponentKey.REQUIREMENT_EXECUTION)
                    && !String.valueOf(node.name()).contains("需求开发")) {
                throw new IllegalArgumentException("需求执行对象组件只能配置在需求开发节点");
            }
            if (components.contains(WorkflowComponentKey.REQUIREMENT_NODE_WORKBENCH)
                    && (String.valueOf(node.name()).contains("需求接收")
                    || String.valueOf(node.name()).contains("需求开发"))) {
                throw new IllegalArgumentException("需求接收和需求开发节点必须使用对应的专用工作台");
            }
        }
    }

    private static void validateRequirementNodeWorkbenchConfig(WorkflowNodeDefinition node, JsonNode config) {
        if (!config.has("nodeKey") || !config.path("nodeKey").isTextual()
                || !node.key().equals(config.path("nodeKey").asText())) {
            throw new IllegalArgumentException("需求节点工作台配置必须绑定当前节点");
        }
        if (config.has("nodeName") && !config.path("nodeName").isTextual()) {
            throw new IllegalArgumentException("需求节点工作台节点名称配置无效");
        }
        if (config.has("purpose") && !config.path("purpose").isTextual()) {
            throw new IllegalArgumentException("需求节点工作台说明配置无效");
        }
        if (config.has("activities")) {
            if (!config.path("activities").isArray()
                    || config.path("activities").size() == 0) {
                throw new IllegalArgumentException("需求节点工作台活动配置无效");
            }
            config.path("activities").forEach(activity -> {
                if (!activity.isTextual() || activity.asText().isBlank()) {
                    throw new IllegalArgumentException("需求节点工作台活动配置无效");
                }
            });
        }
        if (config.has("systemField")) {
            if (!String.valueOf(node.name()).contains("需求澄清")) {
                throw new IllegalArgumentException("需求系统字段只能配置在需求澄清节点");
            }
            JsonNode systemField = config.path("systemField");
            if (!systemField.isObject()) {
                throw new IllegalArgumentException("需求系统字段配置必须是对象");
            }
            if (systemField.has("visibleWhenCategory")) {
                String category = systemField.path("visibleWhenCategory").asText(null);
                if (!"FUNCTIONAL".equals(category) && !"NON_FUNCTIONAL".equals(category)) {
                    throw new IllegalArgumentException("需求系统字段联动分类无效");
                }
            }
            if (systemField.has("required") && !systemField.path("required").isBoolean()) {
                throw new IllegalArgumentException("需求系统字段必填配置无效");
            }
        }
    }

    private static void validateStoryWorkbenchPlacement(WorkflowTemplateDefinition definition) {
        Set<String> seenVariants = new HashSet<>();
        boolean seenTesting = false;
        for (WorkflowNodeDefinition node : definition.nodes()) {
            Set<String> components = new HashSet<>(node.runtimeComponents());
            String name = String.valueOf(node.name());
            if (components.contains(WorkflowComponentKey.STORY_NODE_WORKBENCH)) {
                JsonNode config = node.componentConfigs() == null ? null
                        : node.componentConfigs().get(WorkflowComponentKey.STORY_NODE_WORKBENCH);
                String variant = config == null ? null : config.path("variant").asText(null);
                String requiredMatch = variant == null ? null : STORY_VARIANT_NODE_MATCH.get(variant);
                if (requiredMatch == null || !name.contains(requiredMatch)) {
                    throw new IllegalArgumentException("故事节点工作台只能配置在对应节点");
                }
                if (!seenVariants.add(variant)) {
                    throw new IllegalArgumentException("故事节点工作台不能重复配置");
                }
            }
            if (components.contains(WorkflowComponentKey.STORY_TESTING)) {
                if (!name.contains("测试")) {
                    throw new IllegalArgumentException("故事测试工作台只能配置在测试节点");
                }
                if (seenTesting) {
                    throw new IllegalArgumentException("故事测试工作台只能配置一次");
                }
                seenTesting = true;
            }
        }
    }

    private static void validateStoryNodeWorkbenchConfig(WorkflowNodeDefinition node, JsonNode config) {
        config.fieldNames().forEachRemaining(key -> {
            if (!STORY_WORKBENCH_CONFIG_KEYS.contains(key)) {
                throw new IllegalArgumentException("故事节点工作台配置项无效: " + key);
            }
        });
        if (!config.has("nodeKey") || !config.path("nodeKey").isTextual()
                || !node.key().equals(config.path("nodeKey").asText())) {
            throw new IllegalArgumentException("故事节点工作台配置必须绑定当前节点");
        }
        if (!config.has("variant") || !config.path("variant").isTextual()
                || !STORY_VARIANT_NODE_MATCH.containsKey(config.path("variant").asText())) {
            throw new IllegalArgumentException("故事节点工作台类型无效");
        }
        if (config.has("nodeName") && !config.path("nodeName").isTextual()) {
            throw new IllegalArgumentException("故事节点工作台节点名称配置无效");
        }
        if (config.has("purpose") && !config.path("purpose").isTextual()) {
            throw new IllegalArgumentException("故事节点工作台说明配置无效");
        }
        if (config.has("activities")) {
            if (!config.path("activities").isArray() || config.path("activities").size() == 0) {
                throw new IllegalArgumentException("故事节点工作台活动配置无效");
            }
            config.path("activities").forEach(activity -> {
                if (!activity.isTextual() || activity.asText().isBlank()) {
                    throw new IllegalArgumentException("故事节点工作台活动配置无效");
                }
            });
        }
    }

    private static void validateStoryTestingConfig(JsonNode config) {
        config.fieldNames().forEachRemaining(key -> {
            if (!"testingResultsEnabled".equals(key)) {
                throw new IllegalArgumentException("故事测试工作台配置项无效: " + key);
            }
        });
        if (config.has("testingResultsEnabled") && !config.path("testingResultsEnabled").isBoolean()) {
            throw new IllegalArgumentException("故事测试工作台配置项必须是布尔值");
        }
    }

    private static void rejectHiddenRequired(JsonNode config, String visibleKey, String requiredKey) {
        if (config.path(requiredKey).asBoolean(false) && config.has(visibleKey)
                && !config.path(visibleKey).asBoolean()) {
            throw new IllegalArgumentException("隐藏的评分项不能配置为必填: " + requiredKey);
        }
    }

    private static boolean validKey(String key) {
        return key != null && KEY_PATTERN.matcher(key).matches();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
