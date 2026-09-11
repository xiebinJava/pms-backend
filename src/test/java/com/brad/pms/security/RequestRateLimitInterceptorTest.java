package com.brad.pms.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RequestRateLimitInterceptorTest {

    @Test
    void limitsSensitiveEndpointAndReturnsRetryAfter() throws Exception {
        RequestRateLimitInterceptor interceptor = new RequestRateLimitInterceptor(
                2, Duration.ofMinutes(1), Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC));

        assertThat(interceptor.preHandle(request("/api/auth/login"), new MockHttpServletResponse(), null)).isTrue();
        assertThat(interceptor.preHandle(request("/api/auth/login"), new MockHttpServletResponse(), null)).isTrue();

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request("/api/auth/login"), blocked, null)).isFalse();
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
    }

    @Test
    void ignoresReadOnlyEndpoints() throws Exception {
        RequestRateLimitInterceptor interceptor = new RequestRateLimitInterceptor(
                1, Duration.ofMinutes(1), Clock.systemUTC());

        assertThat(interceptor.preHandle(request("/api/projects"), new MockHttpServletResponse(), null)).isTrue();
        assertThat(interceptor.preHandle(request("/api/projects"), new MockHttpServletResponse(), null)).isTrue();
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr("192.0.2.10");
        return request;
    }
}
