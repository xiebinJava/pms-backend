package com.brad.pms.ai.orchestrator;

import com.brad.pms.ai.orchestrator.domain.AiOrchestratorEventType;
import com.brad.pms.ai.orchestrator.domain.AiOrchestratorState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiOrchestratorStateTest {

    @Test
    void followsThePreviewFirstLifecycle() {
        AiOrchestratorState state = AiOrchestratorState.RECEIVED;

        state = state.transition(AiOrchestratorEventType.CONTEXT_READ);
        state = state.transition(AiOrchestratorEventType.DRAFT_VALIDATED);
        state = state.transition(AiOrchestratorEventType.PLAN_CREATED);
        state = state.transition(AiOrchestratorEventType.PREVIEW_CREATED);
        state = state.transition(AiOrchestratorEventType.WAITING_CONFIRMATION);
        state = state.transition(AiOrchestratorEventType.EXECUTION_STARTED);
        state = state.transition(AiOrchestratorEventType.EXECUTION_FINISHED);
        state = state.transition(AiOrchestratorEventType.VERIFICATION_FINISHED);

        assertThat(state).isEqualTo(AiOrchestratorState.SUCCEEDED);
    }

    @Test
    void rejectsExecutionBeforePreviewAndConfirmation() {
        assertThatThrownBy(() -> AiOrchestratorState.PLAN_READY.transition(
                AiOrchestratorEventType.EXECUTION_STARTED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PLAN_READY")
                .hasMessageContaining("EXECUTION_STARTED");
    }

    @Test
    void mapsVersionConflictsAndCancellationToTerminalStates() {
        assertThat(AiOrchestratorState.WAITING_CONFIRMATION.transition(
                AiOrchestratorEventType.CONFLICT))
                .isEqualTo(AiOrchestratorState.CONFLICT);
        assertThat(AiOrchestratorState.WAITING_CONFIRMATION.transition(
                AiOrchestratorEventType.CANCELLED))
                .isEqualTo(AiOrchestratorState.CANCELLED);
    }

    @Test
    void recordsFailuresAndConflictsBeforeExecutionStarts() {
        assertThat(AiOrchestratorState.PLAN_READY.transition(AiOrchestratorEventType.FAILED))
                .isEqualTo(AiOrchestratorState.FAILED);
        assertThat(AiOrchestratorState.PLAN_READY.transition(AiOrchestratorEventType.CONFLICT))
                .isEqualTo(AiOrchestratorState.CONFLICT);
        assertThat(AiOrchestratorState.PREVIEWED.transition(AiOrchestratorEventType.FAILED))
                .isEqualTo(AiOrchestratorState.FAILED);
    }

    @Test
    void recordsAnUnknownWriteResultWhenReadbackCannotProveTheWrite() {
        assertThat(AiOrchestratorState.VERIFYING.transition(AiOrchestratorEventType.READBACK_UNKNOWN))
                .isEqualTo(AiOrchestratorState.WRITE_RESULT_UNKNOWN);
        assertThat(AiOrchestratorState.WRITE_RESULT_UNKNOWN.transition(
                AiOrchestratorEventType.VERIFICATION_FINISHED))
                .isEqualTo(AiOrchestratorState.SUCCEEDED);
        assertThat(AiOrchestratorState.WRITE_RESULT_UNKNOWN.transition(AiOrchestratorEventType.CONFLICT))
                .isEqualTo(AiOrchestratorState.CONFLICT);
    }

    @Test
    void representsMissingStoryInputAsATerminalPlanState() {
        assertThat(AiOrchestratorState.DRAFT_VALIDATED.transition(AiOrchestratorEventType.INPUT_REQUIRED))
                .isEqualTo(AiOrchestratorState.NEEDS_INPUT);
        assertThatThrownBy(() -> AiOrchestratorState.NEEDS_INPUT.transition(
                AiOrchestratorEventType.PLAN_CREATED)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsEveryTransitionFromTerminalStates() {
        assertThatThrownBy(() -> AiOrchestratorState.SUCCEEDED.transition(
                AiOrchestratorEventType.CONTEXT_READ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUCCEEDED")
                .hasMessageContaining("CONTEXT_READ");
    }

    @Test
    void keepsTheFrozenStateSetWithoutActOrRejectedStates() {
        assertThat(AiOrchestratorState.values())
                .extracting(AiOrchestratorState::name)
                .containsExactly(
                        "RECEIVED",
                        "CONTEXT_READY",
                        "DRAFT_VALIDATED",
                        "NEEDS_INPUT",
                        "PLAN_READY",
                        "PREVIEWED",
                        "WAITING_CONFIRMATION",
                        "EXECUTING",
                        "VERIFYING",
                        "WRITE_RESULT_UNKNOWN",
                        "SUCCEEDED",
                        "FAILED",
                        "CONFLICT",
                        "CANCELLED")
                .doesNotContain("ACTIVE", "REJECTED");
    }

    @Test
    void needsInputNeverLoopsBackIntoTheStateMachine() {
        assertThatThrownBy(() -> AiOrchestratorState.NEEDS_INPUT.transition(
                AiOrchestratorEventType.CONTEXT_READ))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AiOrchestratorState.NEEDS_INPUT.transition(
                AiOrchestratorEventType.DRAFT_VALIDATED))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AiOrchestratorState.NEEDS_INPUT.transition(
                AiOrchestratorEventType.VERIFICATION_FINISHED))
                .isInstanceOf(IllegalStateException.class);
        assertThat(AiOrchestratorState.NEEDS_INPUT.transition(AiOrchestratorEventType.CONFLICT))
                .isEqualTo(AiOrchestratorState.CONFLICT);
    }
}
