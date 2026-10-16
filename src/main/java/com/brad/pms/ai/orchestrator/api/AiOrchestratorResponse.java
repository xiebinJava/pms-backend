package com.brad.pms.ai.orchestrator.api;

import com.brad.pms.ai.orchestrator.contract.AgentDraft;
import com.brad.pms.ai.orchestrator.contract.DraftValidationResult;
import com.brad.pms.ai.orchestrator.contract.StoryNodeContract;
import com.fasterxml.jackson.annotation.JsonInclude;
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
            String errorMessage,
            @JsonInclude(JsonInclude.Include.NON_NULL) StoryNodeContract nodeContract,
            @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> missingFields,
            @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> questions,
            @JsonInclude(JsonInclude.Include.NON_NULL) String idempotencyKey,
            @JsonInclude(JsonInclude.Include.NON_NULL) LocalDateTime confirmedAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) LocalDateTime executedAt) {

        public PlanView {
            validationErrors = validationErrors == null ? List.of() : List.copyOf(validationErrors);
            missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
            questions = questions == null ? List.of() : List.copyOf(questions);
        }

        public PlanView(
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
            this(planId, turnId, intent, status, resourceType, resourceId, nodeId, contextVersion,
                    contextSnapshot, draft, preview, operation, verification, validationErrors,
                    errorCode, errorMessage, null, List.of(), List.of(), null, null, null);
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
