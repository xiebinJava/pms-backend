package com.brad.pms.ai.orchestrator.api;

import com.brad.pms.ai.orchestrator.contract.AgentDraft;
import com.brad.pms.ai.orchestrator.contract.DraftValidationResult;
import com.brad.pms.integration.ai.api.AiOperationPreviewDTO;
import com.brad.pms.integration.ai.api.AiOperationResultDTO;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class AiOrchestratorResponse {

    private AiOrchestratorResponse() {
    }

    public record ConversationView(
            Long conversationId,
            String resourceType,
            Long resourceId,
            String status,
            PlanView latestPlan) {
    }

    public record PlanView(
            Long planId,
            Long turnId,
            String intent,
            String status,
            String resourceType,
            Long resourceId,
            Long nodeId,
            Long contextVersion,
            AiWorkflowContextDTO contextSnapshot,
            AgentDraft draft,
            AiOperationPreviewDTO preview,
            AiOperationResultDTO operation,
            VerificationView verification,
            List<DraftValidationResult.ValidationError> validationErrors,
            String errorCode,
            String errorMessage) {

        public PlanView {
            validationErrors = validationErrors == null ? List.of() : List.copyOf(validationErrors);
        }
    }

    public record VerificationView(
            boolean resourceReadback,
            boolean contextReadback,
            boolean protectedFieldsUnchanged,
            boolean patchApplied,
            String status,
            String message) {
    }

    public record OperationEventView(
            Long sequenceNo,
            String eventType,
            String operationId,
            Map<String, Object> detail,
            LocalDateTime createdAt) {
    }

    public record SessionView(
            Long sessionId,
            ConversationView conversation,
            PlanView latestPlan,
            List<OperationEventView> events,
            long afterSequence,
            long nextSequence) {

        public SessionView {
            events = events == null ? List.of() : List.copyOf(events);
        }
    }
}
