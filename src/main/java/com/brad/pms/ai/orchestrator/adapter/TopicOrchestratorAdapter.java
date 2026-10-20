package com.brad.pms.ai.orchestrator.adapter;

import com.brad.pms.ai.orchestrator.contract.AgentDraft;
import com.brad.pms.ai.orchestrator.contract.AiAgentDraftValidator;
import com.brad.pms.ai.orchestrator.contract.DraftValidationResult;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import org.springframework.stereotype.Component;

/**
 * Maps topic current-node field updates and completions into the frozen
 * resource-node binding. Topic node versions come from the selected
 * node/nodeContract version; the topic entity version may be absent.
 */
@Component
public class TopicOrchestratorAdapter implements ResourceOrchestratorAdapter {

    private final AiAgentDraftValidator draftValidator;

    public TopicOrchestratorAdapter(AiAgentDraftValidator draftValidator) {
        this.draftValidator = draftValidator;
    }

    @Override
    public String resourceType() {
        return "topic";
    }

    @Override
    public ResourceNodeBinding resolveBinding(AiWorkflowContextDTO context, Long nodeId,
                                              String componentKey, Long nodeVersion) {
        return ResourceNodeBindingResolver.resolve(context, nodeId, componentKey, nodeVersion);
    }

    @Override
    public DraftValidationResult validateDraft(AgentDraft draft, AiWorkflowContextDTO context,
                                               ResourceNodeBinding binding) {
        return draftValidator.validateResourceNode(draft, context, binding);
    }
}
