package com.brad.pms.ai.connector.context;

import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Resolves workflow status strictly by the design status table, keeping facts instead of repairing them. */
class AiContextWorkflowStateResolverTest {

    private final AiContextWorkflowStateResolver resolver = new AiContextWorkflowStateResolver();

    @Test
    void notConfiguredStaysNotConfiguredWithoutWarnings() {
        AiContextWorkflowStateResolver.State state = resolver.resolve(
                "requirement", "ACTIVE", null, false, List.of());

        assertThat(state.status()).isEqualTo("NOT_CONFIGURED");
        assertThat(state.terminalStatus()).isNull();
        assertThat(state.warnings()).isEmpty();
    }

    @Test
    void projectTerminalLifecycleWithoutNodesKeepsTheEntityTerminalFact() {
        assertThat(resolver.resolve("project", "COMPLETED", null, false, List.of()))
                .satisfies(state -> {
                    assertThat(state.status()).isEqualTo("NOT_CONFIGURED");
                    assertThat(state.terminalStatus()).isEqualTo("COMPLETED");
                    assertThat(codes(state)).containsExactly("INCONSISTENT_WORKFLOW_STATE");
                });
        assertThat(resolver.resolve("project", "TERMINATED", null, false, List.of()))
                .satisfies(state -> {
                    assertThat(state.status()).isEqualTo("NOT_CONFIGURED");
                    assertThat(state.terminalStatus()).isEqualTo("TERMINATED");
                    assertThat(codes(state)).containsExactly("INCONSISTENT_WORKFLOW_STATE");
                });
    }

    @Test
    void requirementRejectedWithoutInstanceKeepsTheRejectedFact() {
        AiContextWorkflowStateResolver.State state = resolver.resolve(
                "requirement", "REJECTED", null, false, List.of());

        assertThat(state.status()).isEqualTo("NOT_CONFIGURED");
        assertThat(state.terminalStatus()).isEqualTo("REJECTED");
        assertThat(codes(state)).containsExactly("INCONSISTENT_WORKFLOW_STATE");
    }

    @Test
    void devItemStatusesNeverBecomeWorkflowTerminals() {
        assertThat(resolver.resolve("topic", "DONE", null, true,
                List.of(active(1)))).satisfies(state -> {
                    assertThat(state.status()).isEqualTo("IN_PROGRESS");
                    assertThat(state.terminalStatus()).isNull();
                });
        assertThat(resolver.resolve("story", "NOT_STARTED", null, true,
                List.of(locked(1)))).satisfies(state ->
                assertThat(state.status()).isEqualTo("NOT_STARTED"));
    }

    @Test
    void configuredWorkflowsWithoutNodesAreInconsistent() {
        AiContextWorkflowStateResolver.State state = resolver.resolve(
                "topic", "IN_PROGRESS", null, true, null);

        assertThat(state.status()).isEqualTo("INCONSISTENT");
        assertThat(codes(state)).containsExactly("INCONSISTENT_WORKFLOW_STATE", "NO_ACTIVE_NODE");
    }

    @Test
    void nodeFactsResolveToTheDocumentedStatuses() {
        assertThat(resolver.resolve("requirement", "ACTIVE", null, true,
                List.of(completed(1), active(2)))).satisfies(state -> {
                    assertThat(state.status()).isEqualTo("IN_PROGRESS");
                    assertThat(state.warnings()).isEmpty();
                });
        assertThat(resolver.resolve("requirement", "ACTIVE", null, true,
                List.of(locked(1)))).satisfies(state ->
                assertThat(state.status()).isEqualTo("NOT_STARTED"));
        assertThat(resolver.resolve("requirement", "ACTIVE", null, true,
                List.of(completed(1), completed(2), completed(3)))).satisfies(state ->
                assertThat(state.status()).isEqualTo("COMPLETED"));
        assertThat(resolver.resolve("project", "ACTIVE", null, true,
                List.of(completed(1), completed(2)))).satisfies(state ->
                assertThat(state.status()).isEqualTo("COMPLETED"));
    }

    @Test
    void projectCompletionConflictStaysInconsistent() {
        AiContextWorkflowStateResolver.State state = resolver.resolve(
                "project", "COMPLETED", null, true, List.of(completed(1), active(2)));

        assertThat(state.status()).isEqualTo("INCONSISTENT");
        assertThat(state.terminalStatus()).isEqualTo("COMPLETED");
        assertThat(codes(state)).containsExactly("INCONSISTENT_WORKFLOW_STATE");
    }

    @Test
    void projectTerminationResolvesWithoutNodeCompletionRequirements() {
        AiContextWorkflowStateResolver.State state = resolver.resolve(
                "project", "TERMINATED", null, true, List.of(completed(1), locked(2)));

        assertThat(state.status()).isEqualTo("TERMINATED");
        assertThat(state.terminalStatus()).isEqualTo("TERMINATED");
        assertThat(state.warnings()).isEmpty();
    }

    @Test
    void projectTerminationWithActiveNodesIsInconsistent() {
        AiContextWorkflowStateResolver.State state = resolver.resolve(
                "project", "TERMINATED", null, true, List.of(active(1)));

        assertThat(state.status()).isEqualTo("INCONSISTENT");
        assertThat(state.terminalStatus()).isEqualTo("TERMINATED");
        assertThat(codes(state)).containsExactly("INCONSISTENT_WORKFLOW_STATE");
    }

    @Test
    void rejectedTerminalWithoutConflictWinsAndWarnsNothing() {
        assertThat(resolver.resolve("requirement", "ACTIVE", "REJECTED", true,
                List.of(completed(1), locked(2)))).satisfies(state -> {
                    assertThat(state.status()).isEqualTo("REJECTED");
                    assertThat(state.terminalStatus()).isEqualTo("REJECTED");
                    assertThat(state.warnings()).isEmpty();
                });
    }

    @Test
    void requirementRejectionConflictsWithActiveNodesAreInconsistent() {
        AiContextWorkflowStateResolver.State state = resolver.resolve(
                "requirement", "REJECTED", null, true, List.of(active(1)));

        assertThat(state.status()).isEqualTo("INCONSISTENT");
        assertThat(state.terminalStatus()).isEqualTo("REJECTED");
        assertThat(codes(state)).containsExactly("INCONSISTENT_WORKFLOW_STATE");
    }

    @Test
    void terminalConflictCannotHideActiveProgressAndUnknownStatesAreInconsistent() {
        assertThat(resolver.resolve("story", "IN_PROGRESS", "REJECTED", true,
                List.of(active(1)))).satisfies(state -> {
                    assertThat(state.status()).isEqualTo("INCONSISTENT");
                    assertThat(state.terminalStatus()).isEqualTo("REJECTED");
                    assertThat(codes(state)).containsExactly("INCONSISTENT_WORKFLOW_STATE");
                });
        assertThat(resolver.resolve("story", "IN_PROGRESS", "REJECTED", true,
                List.of(completed(1), completed(2)))).satisfies(state ->
                assertThat(state.status()).isEqualTo("REJECTED"));
        assertThat(resolver.resolve("story", "IN_PROGRESS", "BROKEN", true,
                List.of(completed(1)))).satisfies(state ->
                assertThat(state.status()).isEqualTo("INCONSISTENT"));
        assertThat(resolver.resolve("story", "IN_PROGRESS", null, true,
                List.of(new NodeRef(1L, 7, 0)))).satisfies(state ->
                assertThat(state.status()).isEqualTo("INCONSISTENT"));
    }

    @Test
    void multipleActiveNodesAreInconsistentButKeepFacts() {
        AiContextWorkflowStateResolver.State state = resolver.resolve(
                "story", "IN_PROGRESS", null, true, List.of(active(1), active(2)));

        assertThat(state.status()).isEqualTo("INCONSISTENT");
        assertThat(codes(state)).containsExactly("INCONSISTENT_WORKFLOW_STATE");
    }

    @Test
    void terminatedNodesWithoutTerminalStateAndPartialCompletionWithoutActiveAreInconsistent() {
        assertThat(resolver.resolve("story", "IN_PROGRESS", null, true,
                List.of(new NodeRef(1L, 3, 0)))).satisfies(state -> {
                    assertThat(state.status()).isEqualTo("INCONSISTENT");
                    assertThat(codes(state)).containsExactly("INCONSISTENT_WORKFLOW_STATE", "NO_ACTIVE_NODE");
                });
        assertThat(resolver.resolve("story", "IN_PROGRESS", null, true,
                List.of(completed(1), locked(2)))).satisfies(state -> {
                    assertThat(state.status()).isEqualTo("INCONSISTENT");
                    assertThat(codes(state)).containsExactly("INCONSISTENT_WORKFLOW_STATE", "NO_ACTIVE_NODE");
                });
    }

    @Test
    void normalTerminalsNeverEmitTheNoActiveWarning() {
        assertThat(resolver.resolve("project", "COMPLETED", null, true,
                List.of(completed(1), completed(2))).warnings()).isEmpty();
        assertThat(resolver.resolve("project", "TERMINATED", null, true,
                List.of(completed(1), locked(2))).warnings()).isEmpty();
        assertThat(resolver.resolve("requirement", "ACTIVE", "REJECTED", true,
                List.of(completed(1))).warnings()).isEmpty();
    }

    @Test
    void unknownProjectLifecyclesAreInconsistent() {
        AiContextWorkflowStateResolver.State state = resolver.resolve(
                "project", "9", null, true, List.of(active(1)));

        assertThat(state.status()).isEqualTo("INCONSISTENT");
        assertThat(codes(state)).containsExactly("INCONSISTENT_WORKFLOW_STATE");
    }

    private static List<String> codes(AiContextWorkflowStateResolver.State state) {
        return state.warnings().stream().map(AiWorkflowContextDTO.Warning::code).toList();
    }

    private static NodeRef active(long id) {
        return new NodeRef(id, 1, 0);
    }

    private static NodeRef locked(long id) {
        return new NodeRef(id, 0, 1);
    }

    private static NodeRef completed(long id) {
        return new NodeRef(id, 2, 2);
    }
}
