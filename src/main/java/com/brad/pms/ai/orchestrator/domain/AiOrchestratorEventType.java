package com.brad.pms.ai.orchestrator.domain;

public enum AiOrchestratorEventType {
    CONTEXT_READ,
    DRAFT_VALIDATED,
    PLAN_CREATED,
    PREVIEW_CREATED,
    WAITING_CONFIRMATION,
    EXECUTION_STARTED,
    EXECUTION_FINISHED,
    VERIFICATION_FINISHED,
    FAILED,
    CONFLICT,
    CANCELLED
}
