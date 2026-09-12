package com.brad.pms.workflow;

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
            WorkflowComponentKey.KNOWLEDGE_STANDARD);
    private static final Set<String> PROJECT_BASIC_INFO_FIELDS = Set.of("description", "priority", "projectLevel",
            "schedule", "businessLine", "projectManager", "projectMembers", "followers");
    private static final Set<WorkflowFieldType> V1_FIELD_TYPES = Set.of(
            WorkflowFieldType.TEXT, WorkflowFieldType.TEXTAREA, WorkflowFieldType.NUMBER, WorkflowFieldType.DATE,
            WorkflowFieldType.SINGLE_SELECT, WorkflowFieldType.MULTI_SELECT, WorkflowFieldType.PERSON,
            WorkflowFieldType.ATTACHMENT);
    private static final Map<String, WorkflowFieldType> BINDING_TYPES = Map.of(
            "project.description", WorkflowFieldType.TEXTAREA,
            "project.priority", WorkflowFieldType.RADIO,
            "project.projectLevel", WorkflowFieldType.SINGLE_SELECT,
            "project.schedule", WorkflowFieldType.DATE_RANGE,
            "project.businessLine", WorkflowFieldType.SINGLE_SELECT,
            "project.projectManager", WorkflowFieldType.PERSON,
            "project.projectMembers", WorkflowFieldType.PERSON_MULTI,
            "project.followers", WorkflowFieldType.PERSON_MULTI);

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
        return definition;
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
        validateContentOrder(node.contentOrder());
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
                if (field.type() != expectedType) throw new IllegalArgumentException("字段绑定控件类型不匹配");
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

    private static void validateContentOrder(List<String> contentOrder) {
        if (contentOrder == null) throw new IllegalArgumentException("节点内容排序不能为空");
        Set<String> entries = new HashSet<>();
        for (String entry : contentOrder) {
            if (!entries.add(entry)) throw new IllegalArgumentException("节点内容排序不能重复");
            if ("fields".equals(entry)) continue;
            if (!entry.startsWith("component:")) throw new IllegalArgumentException("节点内容排序存在未知项");
            String component = entry.substring("component:".length());
            if (!SUPPORTED_COMPONENTS.contains(component) || WorkflowComponentKey.PROJECT_BASIC_INFO.equals(component)) {
                throw new IllegalArgumentException("节点内容排序存在未知项");
            }
        }
        if (!entries.contains("fields")) throw new IllegalArgumentException("节点内容排序必须包含字段区");
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

    private static boolean validKey(String key) {
        return key != null && KEY_PATTERN.matcher(key).matches();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
