package com.brad.pms.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestTraceFilterTest {
    private final RequestTraceFilter filter = new RequestTraceFilter();

    @AfterEach
    void clearMdc() {
        org.slf4j.MDC.clear();
    }

    @Test
    void preservesSafeIncomingRequestIdAndReturnsItToCaller() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestTraceFilter.HEADER, "support-42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestTraceFilter.HEADER)).isEqualTo("support-42");
    }

    @Test
    void replacesUnsafeRequestIdWithGeneratedValue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestTraceFilter.HEADER, "bad value\r\n");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestTraceFilter.HEADER))
                .matches("[a-f0-9-]{36}");
    }
}
