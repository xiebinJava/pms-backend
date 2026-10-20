package com.brad.pms.ai.orchestrator.adapter;

import com.brad.pms.ai.orchestrator.contract.AgentDraft;
import com.brad.pms.ai.orchestrator.contract.DraftValidationResult;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;

/**
 * Maps a project/topic resource-node intent into the existing focused context,
 * saveContract and draft validation flow. Adapters never read business Mapper
 * classes and never select components implicitly; PMS stays the authority for
 * field rules, versions and permissions.
 */
public interface ResourceOrchestratorAdapter {

    String resourceType();

    /**
     * Resolves the server-owned binding from the focused context. Throws
     * in-band BusinessException codes: CONFLICT for current-node/version
     * mismatches and CAPABILITY_UNAVAILABLE for ambiguous or non-writable
     * component selections.
     */
    ResourceNodeBinding resolveBinding(AiWorkflowContextDTO context, Long nodeId,
                                       String componentKey, Long nodeVersion);

    DraftValidationResult validateDraft(AgentDraft draft, AiWorkflowContextDTO context,
                                        ResourceNodeBinding binding);
}
