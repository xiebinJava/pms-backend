package com.brad.pms.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiDelegationRoutePolicyTest {

    @Test
    void genericQueryRequiresItsDedicatedScope() {
        HttpServletRequest request = request("POST", "/integration/ai/v1/query");
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        when(tokenProvider.isAiDelegationToken("token")).thenReturn(true);
        when(tokenProvider.hasAiDelegationScope("token", "pms:project:read")).thenReturn(true);

        assertThat(new AiDelegationRoutePolicy().isAllowed(request, "token", tokenProvider)).isFalse();

        when(tokenProvider.hasAiDelegationScope("token", "pms:query:read")).thenReturn(true);
        assertThat(new AiDelegationRoutePolicy().isAllowed(request, "token", tokenProvider)).isTrue();
    }

    @Test
    void neutralConnectorExecutionRequiresTheCommandExecuteScope() {
        HttpServletRequest request = request("POST", "/integration/ai/v1/operations/execute");
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        when(tokenProvider.isAiDelegationToken("token")).thenReturn(true);
        when(tokenProvider.hasAiDelegationScope("token", "pms:query:read")).thenReturn(true);

        assertThat(new AiDelegationRoutePolicy().isAllowed(request, "token", tokenProvider)).isFalse();

        when(tokenProvider.hasAiDelegationScope("token", "pms:command:execute")).thenReturn(true);
        assertThat(new AiDelegationRoutePolicy().isAllowed(request, "token", tokenProvider)).isTrue();
    }

    @Test
    void contextAndFourResourceReadsUseTheQueryScope() {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        when(tokenProvider.isAiDelegationToken("token")).thenReturn(true);
        when(tokenProvider.hasAiDelegationScope("token", "pms:query:read")).thenReturn(true);
        AiDelegationRoutePolicy policy = new AiDelegationRoutePolicy();

        assertThat(policy.isAllowed(request("GET", "/integration/ai/v1/context/project/7"), "token", tokenProvider))
                .isTrue();
        for (String type : java.util.List.of("project", "requirement", "topic", "story")) {
            assertThat(policy.isAllowed(
                    request("GET", "/integration/ai/v1/resources/" + type + "/7"), "token", tokenProvider))
                    .isTrue();
        }
        assertThat(policy.isAllowed(
                request("GET", "/integration/ai/v1/resources/iteration_plan/7"), "token", tokenProvider))
                .isFalse();
        assertThat(policy.isAllowed(
                request("DELETE", "/integration/ai/v1/resources/project/7"), "token", tokenProvider))
                .isFalse();
    }

    @Test
    void nonAiDelegationTokenCannotEnterIntegrationFacade() {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        when(tokenProvider.isAiDelegationToken("token")).thenReturn(false);
        when(tokenProvider.hasAiDelegationScope("token", "pms:query:read")).thenReturn(true);

        assertThat(new AiDelegationRoutePolicy().isAllowed(
                request("GET", "/integration/ai/v1/context/project/7"), "token", tokenProvider))
                .isFalse();
    }

    private static HttpServletRequest request(String method, String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getContextPath()).thenReturn("");
        when(request.getMethod()).thenReturn(method);
        return request;
    }
}
