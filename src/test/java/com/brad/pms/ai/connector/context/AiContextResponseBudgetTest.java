package com.brad.pms.ai.connector.context;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiContextResponseBudgetTest {

    private final AiContextResponseBudget budget =
            new AiContextResponseBudget(new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test
    void legacyResponsesAreNeverBudgeted() {
        AiWorkflowContextDTO legacy = legacyContext(paddedBody(600_000));
        budget.requireWithinFocusedLimit(legacy);
    }

    @Test
    void focusedWithinTheLimitIsAccepted() {
        budget.requireWithinFocusedLimit(focusedContext(paddedBody(200)));
    }

    @Test
    void focusedOverTheLimitIsRejectedAs422WithoutPretendingSuccess() {
        AiWorkflowContextDTO tooLarge = focusedContext(paddedBody(600_000));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> budget.requireWithinFocusedLimit(tooLarge))
                .isInstanceOfSatisfying(BusinessException.class, thrown ->
                        org.assertj.core.api.Assertions.assertThat(thrown.getCode()).isEqualTo(422))
                .hasMessageContaining("CONTEXT_TOO_LARGE:");
    }

    @Test
    void multiByteTextCountsUtf8BytesNotCharacters() {
        String repeated = "统一订单中心。".repeat(120_000);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        budget.requireWithinFocusedLimit(focusedContext(Map.of("body", repeated))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CONTEXT_TOO_LARGE:");
    }

    private AiWorkflowContextDTO legacyContext(Map<String, Object> details) {
        AiWorkflowContextDTO base = baseContext(details);
        return new AiWorkflowContextDTO(base.resourceType(), base.resourceId(), base.version(),
                base.currentNode(), base.workflow(), base.allowedActions(), base.entityVersion(),
                base.resourceDetails(), base.relations(), null);
    }

    private AiWorkflowContextDTO focusedContext(Map<String, Object> details) {
        AiWorkflowContextDTO base = baseContext(details);
        AiWorkflowContextDTO.ContextMeta meta = new AiWorkflowContextDTO.ContextMeta(2, true,
                Instant.EPOCH, "Asia/Shanghai", "focused", "selected-node",
                "selected-node", 301L, null, true, "IN_PROGRESS", null, 5,
                "business-user", "readable-only", null);
        return new AiWorkflowContextDTO(base.resourceType(), base.resourceId(), base.version(),
                base.currentNode(), base.workflow(), base.allowedActions(), base.entityVersion(),
                base.resourceDetails(), base.relations(), meta);
    }

    private AiWorkflowContextDTO baseContext(Map<String, Object> details) {
        return new AiWorkflowContextDTO("project", 7L, 3, null,
                new AiWorkflowContextDTO.Workflow(88L, 2, java.util.List.of()),
                java.util.List.of(), 3, new java.util.LinkedHashMap<>(details),
                java.util.List.of(), null);
    }

    private static java.util.Map<String, Object> paddedBody(int chars) {
        return java.util.Map.of("body", "x".repeat(chars));
    }
}
