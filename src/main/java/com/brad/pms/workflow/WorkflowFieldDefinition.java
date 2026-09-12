package com.brad.pms.workflow;

import java.util.List;

public record WorkflowFieldDefinition(
        String key,
        String label,
        WorkflowFieldType type,
        boolean required,
        List<String> options) {
}
