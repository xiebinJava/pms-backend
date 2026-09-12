package com.brad.pms.workflow;

import java.util.List;

public record WorkflowFieldDefinition(
        String key,
        String label,
        WorkflowFieldType type,
        boolean required,
        List<String> options,
        Boolean visible,
        String binding) {

    public WorkflowFieldDefinition(String key, String label, WorkflowFieldType type, boolean required,
                                   List<String> options) {
        this(key, label, type, required, options, null, null);
    }
}
