package com.brad.pms.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiDelegationRoutePolicyTest {

    @Test
    void genericQueryRequiresItsDedicatedScope() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/integration/dsh/v1/query");
        when(request.getContextPath()).thenReturn("");
        when(request.getMethod()).thenReturn("POST");
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        when(tokenProvider.isDshDelegationToken("token")).thenReturn(true);
        when(tokenProvider.hasAiDelegationScope("token", "pms:project:read")).thenReturn(true);

        assertThat(new AiDelegationRoutePolicy().isAllowed(request, "token", tokenProvider)).isFalse();

        when(tokenProvider.hasAiDelegationScope("token", "pms:query:read")).thenReturn(true);
        assertThat(new AiDelegationRoutePolicy().isAllowed(request, "token", tokenProvider)).isTrue();
    }

    @Test
    void agentContractDiscoveryRequiresTheReadQueryScope() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(
                "/integration/dsh/v1/agent-contracts/project_assistant/project-kickoff");
        when(request.getContextPath()).thenReturn("");
        when(request.getMethod()).thenReturn("GET");
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        when(tokenProvider.isDshDelegationToken("token")).thenReturn(true);
        when(tokenProvider.hasAiDelegationScope("token", "pms:project:read")).thenReturn(true);

        assertThat(new AiDelegationRoutePolicy().isAllowed(request, "token", tokenProvider)).isFalse();

        when(tokenProvider.hasAiDelegationScope("token", "pms:query:read")).thenReturn(true);
        assertThat(new AiDelegationRoutePolicy().isAllowed(request, "token", tokenProvider)).isTrue();
    }
}
