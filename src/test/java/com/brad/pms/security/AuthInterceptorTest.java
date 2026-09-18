package com.brad.pms.security;

import com.brad.pms.mapper.AuthSessionMapper;
import com.brad.pms.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthInterceptorTest {

    private final JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final AuthSessionMapper authSessionMapper = mock(AuthSessionMapper.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final AuthInterceptor interceptor = new AuthInterceptor(
            tokenProvider, userMapper, authSessionMapper, authorizationService);

    @AfterEach
    void clearContext() {
        UserContext.clear();
        org.slf4j.MDC.remove("requestId");
    }

    @Test
    void missingTokenReturnsUnauthorizedWithRequestId() throws Exception {
        org.slf4j.MDC.put("requestId", "req-auth");
        MockHttpServletResponse response = invoke("secured", null);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"requestId\":\"req-auth\"");
    }

    @Test
    void invalidTokenDoesNotExposeParserDetails() throws Exception {
        when(tokenProvider.parseToken("bad-token")).thenThrow(new IllegalArgumentException("jwt secret details"));
        MockHttpServletResponse response = invoke("secured", "Bearer bad-token");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).doesNotContain("jwt secret details");
    }

    @Test
    void delegationTokenMayReadTaskQueryOnlyWithQueryScope() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ai/query/tasks");
        request.setContextPath("/api");
        when(tokenProvider.hasAiDelegationScope("token", "ai:query:read")).thenReturn(true);

        Boolean allowed = ReflectionTestUtils.invokeMethod(
                interceptor, "allowedAiDelegationRoute", request, "token");
        assertThat(allowed).isTrue();

        request.setMethod("GET");
        Boolean getAllowed = ReflectionTestUtils.invokeMethod(
                interceptor, "allowedAiDelegationRoute", request, "token");
        assertThat(getAllowed).isFalse();
    }

    @Test
    void delegationTokenMayReadDshProjectListWithPmsProjectScope() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/integration/dsh/v1/projects");
        request.setContextPath("/api");
        when(tokenProvider.isDshDelegationToken("token")).thenReturn(true);
        when(tokenProvider.hasAiDelegationScope("token", "pms:project:read")).thenReturn(true);

        Boolean allowed = ReflectionTestUtils.invokeMethod(
                interceptor, "allowedAiDelegationRoute", request, "token");

        assertThat(allowed).isTrue();
    }

    @Test
    void legacyAiDelegationTokenCannotEnterDshIntegrationFacade() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/integration/dsh/v1/projects");
        request.setContextPath("/api");
        when(tokenProvider.isDshDelegationToken("legacy-token")).thenReturn(false);
        when(tokenProvider.hasAiDelegationScope("legacy-token", "pms:project:read")).thenReturn(true);

        Boolean allowed = ReflectionTestUtils.invokeMethod(
                interceptor, "allowedAiDelegationRoute", request, "legacy-token");

        assertThat(allowed).isFalse();
    }

    private MockHttpServletResponse invoke(String methodName, String authorization) throws Exception {
        Method method = TestController.class.getDeclaredMethod(methodName);
        HandlerMethod handler = new HandlerMethod(new TestController(), method);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/secure");
        if (authorization != null) request.addHeader("Authorization", authorization);
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, response, handler)).isFalse();
        return response;
    }

    static class TestController {
        public void secured() {
        }
    }
}
