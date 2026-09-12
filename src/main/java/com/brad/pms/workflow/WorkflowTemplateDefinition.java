package com.brad.pms.workflow;

import java.util.List;

public record WorkflowTemplateDefinition(int schemaVersion, List<WorkflowNodeDefinition> nodes) {
}
