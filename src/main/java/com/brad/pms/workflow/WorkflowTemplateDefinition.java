package com.brad.pms.workflow;

import java.util.List;

public record WorkflowTemplateDefinition(int schemaVersion, List<WorkflowNodeDefinition> nodes,
                                         String sourceProjectNodeKey,
                                         String sourceTopicNodeKey) {
    public WorkflowTemplateDefinition(int schemaVersion, List<WorkflowNodeDefinition> nodes) {
        this(schemaVersion, nodes, null, null);
    }

    public WorkflowTemplateDefinition(int schemaVersion, List<WorkflowNodeDefinition> nodes,
                                      String sourceProjectNodeKey) {
        this(schemaVersion, nodes, sourceProjectNodeKey, null);
    }
}
