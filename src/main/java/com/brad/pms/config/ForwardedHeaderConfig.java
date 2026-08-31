package com.brad.pms.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Makes servlet request security properties reflect the TLS-terminating proxy.
 * The deployment must only trust this header path from its configured proxy.
 */
@Configuration
public class ForwardedHeaderConfig {

    @Bean
    public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
        FilterRegistrationBean<ForwardedHeaderFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ForwardedHeaderFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
