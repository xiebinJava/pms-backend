package com.brad.pms.integration.dsh.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DshAuthorizationCodeContractTest {

    @Test
    void issueAndExchangeContractsCarryNoUserIdentity() {
        DshAuthorizationCodeIssueRequest issue = new DshAuthorizationCodeIssueRequest(
                "dsh-session-1", "project_assistant", Set.of("pms:project:read"));
        DshAuthorizationCodeExchangeRequest exchange = new DshAuthorizationCodeExchangeRequest(
                "one-time-code", "dsh-session-1", "project_assistant", Set.of("pms:project:read"));
        DshAuthorizationCodeIssueResponse response = new DshAuthorizationCodeIssueResponse(
                "one-time-code", 90, List.of("pms:project:read"));

        assertThat(issue.dshSessionId()).isEqualTo("dsh-session-1");
        assertThat(issue.agentId()).isEqualTo("project_assistant");
        assertThat(exchange.authorizationCode()).isEqualTo("one-time-code");
        assertThat(response.expiresInSeconds()).isEqualTo(90);
        assertThat(response.scopes()).containsExactly("pms:project:read");
    }
}
