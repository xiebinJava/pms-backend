package com.brad.pms.ai.connector.context;

import com.brad.pms.ai.connector.context.AiContextNodeSelector.Selection;
import com.brad.pms.ai.connector.context.AiContextWorkflowStateResolver.State;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO.Warning;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;

/** Builds the context meta portion shared by every resource reader. */
final class AiContextMetaFactory {

    private AiContextMetaFactory() {
    }

    static AiWorkflowContextDTO.ContextMeta build(ContextReadOptions options, Selection selection,
                                                  State state, String selectedComponentKey,
                                                  boolean workflowConfigured,
                                                  Integer workflowInstanceVersion,
                                                  Integer entityVersion,
                                                  LinkedHashSet<Warning> warnings,
                                                  Clock clock) {
        String view = options.view();
        boolean focused = ContextReadOptions.VIEW_FOCUSED.equals(view);
        String componentContentScope = selectedComponentKey == null ? "selected-node" : "selected-component";
        return new AiWorkflowContextDTO.ContextMeta(
                2, true, clock.instant(), "Asia/Shanghai", view,
                focused ? "selected-node" : "all-nodes",
                componentContentScope,
                selection.selectedNodeId(), selectedComponentKey,
                workflowConfigured, state.status(), state.terminalStatus(), workflowInstanceVersion,
                "business-user", "readable-only",
                warnings == null ? List.of() : List.copyOf(warnings));
    }

}
