package com.brad.pms.workflow;

import java.util.HashSet;
import java.util.List;
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

    private WorkflowTemplateDefinitionValidator() { }

    public static WorkflowTemplateDefinition validate(WorkflowTemplateDefinition definition) {
        if (definition == null || definition.schemaVersion() != 1) {
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
            validateFields(node.fields());
        }
        return definition;
    }

    public static Set<String> supportedComponents() {
        return SUPPORTED_COMPONENTS;
    }

    private static void validateFields(List<WorkflowFieldDefinition> fields) {
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
            List<String> options = field.options() == null ? List.of() : field.options();
            if (field.type() == WorkflowFieldType.SINGLE_SELECT || field.type() == WorkflowFieldType.MULTI_SELECT) {
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
