package com.brad.pms.ai.orchestrator.api;

import com.brad.pms.ai.orchestrator.contract.AgentDraft;

public final class AiOrchestratorRequest {

    private AiOrchestratorRequest() {
    }

    public record CreateConversationRequest(String resourceType, Long resourceId) {
    }

    public record AppendTurnRequest(
            String message,
            String contractVersion,
            AgentDraft draft,
            Long nodeId,
            Long contextVersion,
            String requestId) {
    }

    public record ConfirmPlanRequest(String idempotencyKey) {
    }

    public record CreateConversationCommand(String resourceType, Long resourceId) {
    }

    public record AppendTurnCommand(
            String message,
            String contractVersion,
            AgentDraft draft,
            Long nodeId,
            Long contextVersion,
            String requestId) {
    }
}
