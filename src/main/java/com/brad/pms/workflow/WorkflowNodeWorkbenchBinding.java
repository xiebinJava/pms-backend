package com.brad.pms.workflow;

import java.util.ArrayList;
import java.util.Objects;

/** Adds a workbench by stable node key without changing any other node content. */
public final class WorkflowNodeWorkbenchBinding {
    private WorkflowNodeWorkbenchBinding() { }

    public static WorkflowTemplateDefinition bind(WorkflowTemplateDefinition definition, String nodeKey, String componentKey) {
        boolean changed = false;
        var nodes = new ArrayList<WorkflowNodeDefinition>();
        for (var node : definition.nodes()) {
            if (!Objects.equals(node.key(), nodeKey) || node.runtimeComponents().contains(componentKey)) {
                nodes.add(node);
                continue;
            }
            var components = node.components();
            var order = node.contentOrder();
            if (order == null) {
                components = new ArrayList<>(node.runtimeComponents());
                components.add(componentKey);
            } else {
                order = new ArrayList<>(order);
                order.add("component:" + componentKey);
            }
            nodes.add(new WorkflowNodeDefinition(node.key(), node.name(), node.description(), node.deliverable(),
                    node.roles(), components, node.fields(), node.projectBasicInfo(), node.projectBasicInfoFields(),
                    order, node.componentConfigs()));
            changed = true;
        }
        return changed ? new WorkflowTemplateDefinition(definition.schemaVersion(), nodes,
                definition.sourceProjectNodeKey(), definition.sourceTopicNodeKey()) : definition;
    }
}
