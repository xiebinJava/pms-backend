package com.brad.pms.workflow;

import java.util.List;
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
        List<String> contentOrder) {

    public WorkflowNodeDefinition(String key, String name, String description, String deliverable, String roles,
                                  List<String> components, List<WorkflowFieldDefinition> fields,
                                  boolean projectBasicInfo,
                                  List<WorkflowProjectFieldDefinition> projectBasicInfoFields) {
        this(key, name, description, deliverable, roles, components, fields, projectBasicInfo,
                projectBasicInfoFields, null);
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
