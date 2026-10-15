package com.brad.pms.ai.orchestrator.domain;

import java.util.EnumMap;
import java.util.Map;

public enum AiOrchestratorState {
    RECEIVED,
    CONTEXT_READY,
    DRAFT_VALIDATED,
    PLAN_READY,
    PREVIEWED,
    WAITING_CONFIRMATION,
    EXECUTING,
    VERIFYING,
    SUCCEEDED,
    FAILED,
    CONFLICT,
    CANCELLED;

    private static final Map<AiOrchestratorState, Map<AiOrchestratorEventType, AiOrchestratorState>> TRANSITIONS = transitions();

    public AiOrchestratorState transition(AiOrchestratorEventType eventType) {
        AiOrchestratorState next = TRANSITIONS.getOrDefault(this, Map.of()).get(eventType);
        if (next == null) {
            throw new IllegalStateException("非法编排状态转换: " + this + " + " + eventType);
        }
        return next;
    }

    private static Map<AiOrchestratorState, Map<AiOrchestratorEventType, AiOrchestratorState>> transitions() {
        Map<AiOrchestratorState, Map<AiOrchestratorEventType, AiOrchestratorState>> result =
                new EnumMap<>(AiOrchestratorState.class);
        result.put(RECEIVED, Map.of(
                AiOrchestratorEventType.CONTEXT_READ, CONTEXT_READY,
                AiOrchestratorEventType.FAILED, FAILED));
        result.put(CONTEXT_READY, Map.of(
                AiOrchestratorEventType.DRAFT_VALIDATED, DRAFT_VALIDATED,
                AiOrchestratorEventType.FAILED, FAILED));
        result.put(DRAFT_VALIDATED, Map.of(
                AiOrchestratorEventType.PLAN_CREATED, PLAN_READY,
                AiOrchestratorEventType.FAILED, FAILED));
        result.put(PLAN_READY, Map.of(
                AiOrchestratorEventType.PREVIEW_CREATED, PREVIEWED,
                AiOrchestratorEventType.CONFLICT, CONFLICT,
                AiOrchestratorEventType.FAILED, FAILED));
        result.put(PREVIEWED, Map.of(
                AiOrchestratorEventType.WAITING_CONFIRMATION, WAITING_CONFIRMATION,
                AiOrchestratorEventType.CONFLICT, CONFLICT,
                AiOrchestratorEventType.FAILED, FAILED));
        result.put(WAITING_CONFIRMATION, Map.of(
                AiOrchestratorEventType.EXECUTION_STARTED, EXECUTING,
                AiOrchestratorEventType.CONFLICT, CONFLICT,
                AiOrchestratorEventType.CANCELLED, CANCELLED,
                AiOrchestratorEventType.FAILED, FAILED));
        result.put(EXECUTING, Map.of(
                AiOrchestratorEventType.EXECUTION_FINISHED, VERIFYING,
                AiOrchestratorEventType.CONFLICT, CONFLICT,
                AiOrchestratorEventType.CANCELLED, CANCELLED,
                AiOrchestratorEventType.FAILED, FAILED));
        result.put(VERIFYING, Map.of(
                AiOrchestratorEventType.VERIFICATION_FINISHED, SUCCEEDED,
                AiOrchestratorEventType.FAILED, FAILED));
        return Map.copyOf(result);
    }
}
