package com.brad.pms.workflow;

import java.util.List;

public record WorkflowNodeDefinition(
        String key,
        String name,
        String description,
        String deliverable,
        String roles,
        List<String> components,
        List<WorkflowFieldDefinition> fields,
        boolean projectBasicInfo,
        List<WorkflowProjectFieldDefinition> projectBasicInfoFields) {
}
