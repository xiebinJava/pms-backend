package com.brad.pms.ai.connector.context;

import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.dto.response.ProjectNodeDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Partial project-profile saves reuse the existing high-risk, version-checked command. */
final class ProjectProfileSaveContract {
    private ProjectProfileSaveContract() { }

    static Map<String, Object> create(ProjectDTO project, ProjectNodeDTO node, List<String> actions) {
        if (project.getId() == null || project.getVersion() == null || node.getId() == null
                || node.getVersion() == null || !Objects.equals(project.getId(), node.getProjectId())
                || (node.getComponents() != null && !node.getComponents().isEmpty())
                || !actions.contains("project.update")
                || com.brad.pms.common.enums.ProjectStatus.isReadOnly(project.getStatus())
                || com.brad.pms.common.enums.NodeStatus.isReadOnly(node.getStatus())
                || (node.getPermissions() != null && node.getPermissions().isReadOnly())) return null;
        Map<String, Map<String, Object>> fields = new LinkedHashMap<>();
        if (node.getContentOrder() != null && node.getFields() != null) {
            for (var field : node.getFields()) {
                if (Boolean.FALSE.equals(field.visible()) || field.binding() == null) continue;
                addBinding(fields, field.binding(), field.label(), field.required());
            }
        }
        // Legacy template representation: only use explicitly configured profile fields.
        if (node.getContentOrder() == null && Boolean.TRUE.equals(node.getProjectBasicInfo())
                && node.getProjectBasicInfoFields() != null) {
            for (var field : node.getProjectBasicInfoFields()) {
                if (field.visible()) addBinding(fields, "project." + field.key(), field.label(), field.required());
            }
        }
        if (fields.isEmpty()) return null;
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("contractVersion", "project-profile/v1");
        contract.put("command", "project.update");
        contract.put("projectId", project.getId()); contract.put("projectVersion", project.getVersion());
        contract.put("nodeId", node.getId()); contract.put("nodeVersion", node.getVersion());
        contract.put("fieldValuesPath", "$");
        contract.put("writableFields", new ArrayList<>(fields.keySet()));
        contract.put("effectiveWritableFields", new ArrayList<>(fields.keySet()));
        contract.put("fieldContracts", new ArrayList<>(fields.values()));
        contract.put("requiredFields", List.of());
        contract.put("protectedFields", List.of("name", "status", "ownerId", "memberIds", "followerIds",
                "projectId", "nodeId", "version"));
        contract.put("completionSupported", false);
        return contract;
    }

    private static void addBinding(Map<String, Map<String, Object>> fields, String binding,
                                   String label, boolean requiredForCompletion) {
        switch (binding) {
            case "project.description" -> add(fields, "description", label, "textarea", requiredForCompletion);
            case "project.priority" -> add(fields, "priority", label, "number", requiredForCompletion);
            case "project.projectLevel" -> add(fields, "projectLevel", label, "number", requiredForCompletion);
            case "project.businessLine" -> add(fields, "orgUnitId", label, "integer", requiredForCompletion);
            case "project.projectManager" -> add(fields, "projectManagerId", label, "person", requiredForCompletion);
            case "project.schedule" -> {
                add(fields, "startDate", label + "（开始）", "date", requiredForCompletion);
                add(fields, "endDate", label + "（结束）", "date", requiredForCompletion);
            }
            default -> { /* Membership and followers require their dedicated commands. */ }
        }
    }

    private static void add(Map<String, Map<String, Object>> fields, String key, String label,
                            String type, boolean requiredForCompletion) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("key", key); field.put("label", label); field.put("type", type);
        // A partial save does not require unrelated profile fields to be populated.
        field.put("required", false); field.put("requiredForCompletion", requiredForCompletion);
        fields.putIfAbsent(key, field);
    }

    static Map<String, Object> values(ProjectDTO project, Map<String, Object> contract) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (contract == null) return values;
        var keys = (List<?>)contract.get("writableFields");
        for (Object raw : keys) {
            String key = (String)raw;
            Object value = switch (key) {
                case "description" -> project.getDescription();
                case "priority" -> project.getPriority();
                case "projectLevel" -> project.getProjectLevel();
                case "orgUnitId" -> project.getOrgUnitId();
                case "projectManagerId" -> project.getProjectManagerId();
                case "startDate" -> project.getStartDate();
                case "endDate" -> project.getEndDate();
                default -> null;
            };
            values.put(key, value);
        }
        return values;
    }
}
