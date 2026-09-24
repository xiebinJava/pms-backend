package com.brad.pms.workflow;

import java.util.List;

public record WorkflowTemplateDefinition(int schemaVersion, List<WorkflowNodeDefinition> nodes,
                                         String sourceProjectNodeKey) {
    public WorkflowTemplateDefinition(int schemaVersion, List<WorkflowNodeDefinition> nodes) {
        this(schemaVersion, nodes, null);
    }
}
