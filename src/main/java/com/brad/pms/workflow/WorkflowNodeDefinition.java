package com.brad.pms.workflow;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record WorkflowNodeDefinition(
        String key,
        String name,
        String description,
        String deliverable,
        String roles,
        List<String> components,
        List<WorkflowFieldDefinition> fields,
        boolean projectBasicInfo,
        List<WorkflowProjectFieldDefinition> projectBasicInfoFields,
        List<String> contentOrder,
        Map<String, JsonNode> componentConfigs) {

    public WorkflowNodeDefinition(String key, String name, String description, String deliverable, String roles,
                                  List<String> components, List<WorkflowFieldDefinition> fields,
                                  boolean projectBasicInfo,
                                  List<WorkflowProjectFieldDefinition> projectBasicInfoFields) {
        this(key, name, description, deliverable, roles, components, fields, projectBasicInfo,
                projectBasicInfoFields, null, null);
    }

    public WorkflowNodeDefinition(String key, String name, String description, String deliverable, String roles,
                                  List<String> components, List<WorkflowFieldDefinition> fields,
                                  boolean projectBasicInfo,
                                  List<WorkflowProjectFieldDefinition> projectBasicInfoFields,
                                  List<String> contentOrder) {
        this(key, name, description, deliverable, roles, components, fields, projectBasicInfo,
                projectBasicInfoFields, contentOrder, null);
    }

    /** Returns the workbench components in either the legacy v1 or configurable v2 schema. */
    public List<String> runtimeComponents() {
        if (contentOrder == null) return components == null ? List.of() : components;
        return contentOrder.stream()
                .filter(entry -> entry != null && entry.startsWith("component:"))
                .map(entry -> entry.substring("component:".length()))
                .collect(Collectors.toList());
    }
}
