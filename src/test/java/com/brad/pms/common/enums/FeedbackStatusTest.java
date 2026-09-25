package com.brad.pms.common.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackStatusTest {

    @Test
    void allowsOnlyTheDocumentedWorkflowTransitions() {
        assertThat(FeedbackStatus.canTransition("PENDING_TRIAGE", "ASSIGNED")).isTrue();
        assertThat(FeedbackStatus.canTransition("ASSIGNED", "IN_PROGRESS")).isTrue();
        assertThat(FeedbackStatus.canTransition("IN_PROGRESS", "PENDING_CONFIRMATION")).isTrue();
        assertThat(FeedbackStatus.canTransition("PENDING_CONFIRMATION", "RESOLVED")).isTrue();
        assertThat(FeedbackStatus.canTransition("RESOLVED", "CLOSED")).isTrue();
        assertThat(FeedbackStatus.canTransition("CLOSED", "IN_PROGRESS")).isTrue();
        assertThat(FeedbackStatus.canTransition("pending_triage", "assigned")).isTrue();
        assertThat(FeedbackStatus.canTransition("PENDING_TRIAGE", "CLOSED")).isFalse();
        assertThat(FeedbackStatus.canTransition("unknown", "CLOSED")).isFalse();
    }
}
