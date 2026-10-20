package com.brad.pms.ai.orchestrator.adapter;

import com.brad.pms.ai.orchestrator.contract.AgentDraft;
import com.brad.pms.ai.orchestrator.contract.AiAgentDraftValidator;
import com.brad.pms.ai.orchestrator.contract.DraftValidationResult;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import org.springframework.stereotype.Component;

/**
 * Maps project current-node field updates and completions into the frozen
 * resource-node binding. Project entity versions stay on the context/entity
 * axis; node versions come from the selected component saveContract.
 */
@Component
public class ProjectOrchestratorAdapter implements ResourceOrchestratorAdapter {

    private final AiAgentDraftValidator draftValidator;

    public ProjectOrchestratorAdapter(AiAgentDraftValidator draftValidator) {
        this.draftValidator = draftValidator;
    }

    @Override
    public String resourceType() {
        return "project";
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
