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

    @Test
    void peopleDirectoryRequiresTheProjectReadScope() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/integration/dsh/v1/people");
        when(request.getContextPath()).thenReturn("/api");
        when(request.getMethod()).thenReturn("GET");
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        when(tokenProvider.isDshDelegationToken("token")).thenReturn(true);
        when(tokenProvider.hasAiDelegationScope("token", "pms:task:read")).thenReturn(true);

        assertThat(new AiDelegationRoutePolicy().isAllowed(request, "token", tokenProvider)).isFalse();

        when(tokenProvider.hasAiDelegationScope("token", "pms:project:read")).thenReturn(true);
        assertThat(new AiDelegationRoutePolicy().isAllowed(request, "token", tokenProvider)).isTrue();
    }

    @Test
    void neutralConnectorExecutionRequiresTheCommandExecuteScope() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/integration/ai/v1/operations/execute");
        when(request.getContextPath()).thenReturn("");
        when(request.getMethod()).thenReturn("POST");
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        when(tokenProvider.isDshDelegationToken("token")).thenReturn(true);
        when(tokenProvider.hasAiDelegationScope("token", "pms:query:read")).thenReturn(true);

        assertThat(new AiDelegationRoutePolicy().isAllowed(request, "token", tokenProvider)).isFalse();

        when(tokenProvider.hasAiDelegationScope("token", "pms:command:execute")).thenReturn(true);
        assertThat(new AiDelegationRoutePolicy().isAllowed(request, "token", tokenProvider)).isTrue();
    }

    @Test
    void contextAndFourResourceReadsUseTheQueryScope() {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        when(tokenProvider.isDshDelegationToken("token")).thenReturn(true);
        when(tokenProvider.hasAiDelegationScope("token", "pms:query:read")).thenReturn(true);
        AiDelegationRoutePolicy policy = new AiDelegationRoutePolicy();

        HttpServletRequest context = mock(HttpServletRequest.class);
        when(context.getRequestURI()).thenReturn("/integration/ai/v1/context/project/7");
        when(context.getContextPath()).thenReturn("");
        when(context.getMethod()).thenReturn("GET");
        assertThat(policy.isAllowed(context, "token", tokenProvider)).isTrue();

        for (String type : java.util.List.of("project", "requirement", "topic", "story")) {
            HttpServletRequest resource = mock(HttpServletRequest.class);
            when(resource.getRequestURI()).thenReturn("/integration/ai/v1/resources/" + type + "/7");
            when(resource.getContextPath()).thenReturn("");
            when(resource.getMethod()).thenReturn("GET");
            assertThat(policy.isAllowed(resource, "token", tokenProvider)).isTrue();
        }

        HttpServletRequest iterationResource = mock(HttpServletRequest.class);
        when(iterationResource.getRequestURI()).thenReturn("/integration/ai/v1/resources/iteration_plan/7");
        when(iterationResource.getContextPath()).thenReturn("");
        when(iterationResource.getMethod()).thenReturn("GET");
        assertThat(policy.isAllowed(iterationResource, "token", tokenProvider)).isFalse();

        HttpServletRequest deletion = mock(HttpServletRequest.class);
        when(deletion.getRequestURI()).thenReturn("/integration/ai/v1/resources/project/7");
        when(deletion.getContextPath()).thenReturn("");
        when(deletion.getMethod()).thenReturn("DELETE");
        assertThat(policy.isAllowed(deletion, "token", tokenProvider)).isFalse();
    }
}
