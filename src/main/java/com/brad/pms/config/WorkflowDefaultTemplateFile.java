package com.brad.pms.config;

import com.brad.pms.workflow.WorkflowTemplateDefinition;

public record WorkflowDefaultTemplateFile(
        String processTypeCode,
        String templateCode,
        String name,
        String description,
        Integer versionNo,
        WorkflowTemplateDefinition definition) {
}
