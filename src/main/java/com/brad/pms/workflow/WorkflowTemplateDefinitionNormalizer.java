package com.brad.pms.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Applies compatibility migrations that can be derived from a published template
 * without changing the stored workflow snapshot. Runtime consumers and the admin
 * editor therefore see the same canonical definition.
 */
public final class WorkflowTemplateDefinitionNormalizer {
    private WorkflowTemplateDefinitionNormalizer() { }

    public static WorkflowTemplateDefinition normalizeForProcessType(
            String processTypeCode, WorkflowTemplateDefinition definition) {
        if (!"requirement-management".equals(processTypeCode)
                || definition == null || definition.schemaVersion() != 2) {
            return definition;
        }
        boolean changed = false;
        List<WorkflowNodeDefinition> nodes = new ArrayList<>();
        for (WorkflowNodeDefinition node : definition.nodes()) {
            WorkflowNodeDefinition normalized = normalizeNode(node);
            nodes.add(normalized);
            changed |= normalized != node;
        }
        return changed ? new WorkflowTemplateDefinition(definition.schemaVersion(), nodes,
                definition.sourceProjectNodeKey(), definition.sourceTopicNodeKey()) : definition;
    }

    private static WorkflowNodeDefinition normalizeNode(WorkflowNodeDefinition node) {
        List<WorkflowFieldDefinition> fields = node.fields() == null ? List.of() : node.fields();
        boolean changed = false;
        List<WorkflowFieldDefinition> normalizedFields = new ArrayList<>();
        for (WorkflowFieldDefinition field : fields) {
            WorkflowFieldDefinition normalized = normalizeField(field);
            normalizedFields.add(normalized);
            changed |= normalized != field;
        }

        List<String> contentOrder = normalizeContentOrder(node.contentOrder(), normalizedFields);
        changed |= !contentOrder.equals(node.contentOrder() == null ? List.of() : node.contentOrder());
        if (!changed) return node;
        return new WorkflowNodeDefinition(node.key(), node.name(), node.description(), node.deliverable(),
                node.roles(), node.components(), normalizedFields, node.projectBasicInfo(),
                node.projectBasicInfoFields(), contentOrder);
    }

    private static WorkflowFieldDefinition normalizeField(WorkflowFieldDefinition field) {
        if (field == null) return null;
        if ("requirement.priority".equals(field.binding()) && field.type() == WorkflowFieldType.NUMBER) {
            return new WorkflowFieldDefinition(field.key(), field.label(), WorkflowFieldType.SINGLE_SELECT,
                    field.required(), field.options(), field.visible(), field.binding(), field.fullWidth());
        }
        if (field.binding() != null || (field.options() != null && !field.options().isEmpty())) return field;
        RequirementBinding binding = bindingFor(field.label(), field.key());
        if (binding == null || field.type() != binding.type()) return field;
        return new WorkflowFieldDefinition(field.key(), field.label(), field.type(), field.required(),
                field.options(), field.visible(), binding.binding(), field.fullWidth());
    }

    private static List<String> normalizeContentOrder(
            List<String> originalOrder, List<WorkflowFieldDefinition> fields) {
        List<String> order = new ArrayList<>(originalOrder == null ? List.of() : originalOrder);
        boolean hasBoundFields = fields.stream().anyMatch(field -> field.binding() != null);
        boolean hasUnboundFields = fields.stream().anyMatch(field -> field.binding() == null);
        int legacyIndex = order.indexOf("legacy-custom-fields");
        if (hasBoundFields && !order.contains("fields")) {
            if (legacyIndex >= 0) {
                order.set(legacyIndex, "fields");
                if (hasUnboundFields) order.add(legacyIndex + 1, "legacy-custom-fields");
            } else {
                order.add("fields");
            }
        }
        if (!hasUnboundFields) order.removeIf("legacy-custom-fields"::equals);
        return order;
    }

    private static RequirementBinding bindingFor(String label, String key) {
        String normalizedLabel = normalize(label);
        String normalizedKey = normalize(key);
        if (matches(normalizedLabel, normalizedKey, "需求名称", "需求标题", "requirementname", "requirementtitle")) {
            return new RequirementBinding("requirement.title", WorkflowFieldType.TEXT);
        }
        if (matches(normalizedLabel, normalizedKey, "需求描述", "requirementdescription")) {
            return new RequirementBinding("requirement.description", WorkflowFieldType.TEXTAREA);
        }
        if (matches(normalizedLabel, normalizedKey, "需求优先级", "requirementpriority")) {
            return new RequirementBinding("requirement.priority", WorkflowFieldType.SINGLE_SELECT);
        }
        if (matches(normalizedLabel, normalizedKey, "需求负责人", "requirementowner")) {
            return new RequirementBinding("requirement.owner", WorkflowFieldType.PERSON);
        }
        return null;
    }

    private static boolean matches(String label, String key, String... candidates) {
        for (String candidate : candidates) {
            String normalized = normalize(candidate);
            if (normalized.equals(label) || normalized.equals(key)) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
    }

    private record RequirementBinding(String binding, WorkflowFieldType type) { }
}
