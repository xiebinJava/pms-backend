package com.brad.pms.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;

@Configuration
@EnableScheduling
public class ObservabilityConfig {

    @Bean
    Clock observabilityClock() {
        return Clock.systemUTC();
    }

    @Bean
    FilterRegistrationBean<RequestMetricsFilter> requestMetricsFilter(MeterRegistry registry) {
        FilterRegistrationBean<RequestMetricsFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestMetricsFilter(registry));
        registration.setOrder(Integer.MAX_VALUE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    static final class RequestMetricsFilter extends OncePerRequestFilter {
        private final MeterRegistry registry;

        RequestMetricsFilter(MeterRegistry registry) {
            this.registry = registry;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            Timer.Sample sample = Timer.start(registry);
            try {
                filterChain.doFilter(request, response);
            } finally {
                Timer timer = Timer.builder("pms.http.requests")
                        .description("PMS HTTP request duration")
                        .tag("method", request.getMethod())
                        .tag("path", routeTemplate(request))
                        .tag("status", String.valueOf(response.getStatus()))
                        .register(registry);
                sample.stop(timer);
            }
        }

        private String routeTemplate(HttpServletRequest request) {
            Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (pattern instanceof String && !((String) pattern).isBlank()) {
                String route = (String) pattern;
                return route.length() > 100 ? route.substring(0, 100) : route;
            }
            // Do not use the raw URI for unmatched requests: random paths and
            // upload UUIDs must not create unbounded metric time series.
            return "UNMATCHED";
        }
    }
}
