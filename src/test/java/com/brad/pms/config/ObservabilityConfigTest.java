package com.brad.pms.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityConfigTest {

    @Test
    void unmatchedUrisUseOneBoundedMetricLabel() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObservabilityConfig.RequestMetricsFilter filter = new ObservabilityConfig.RequestMetricsFilter(registry);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/random/" + java.util.UUID.randomUUID());
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        filter.doFilter(request, response, chain);

        assertThat(registry.get("pms.http.requests").tag("path", "UNMATCHED").timer()).isNotNull();
    }

    @Test
    void mappedUrisKeepTheirFiniteRouteTemplate() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObservabilityConfig.RequestMetricsFilter filter = new ObservabilityConfig.RequestMetricsFilter(registry);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/projects/42");
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                "/projects/{id}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(registry.get("pms.http.requests").tag("path", "/projects/{id}").timer()).isNotNull();
    }
}
