package com.brad.pms.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebhookConfig {

    @Bean
    WebhookHttpClient webhookHttpClient() {
        return new JdkWebhookHttpClient();
    }

    @Bean
    WebhookPropertiesValidator webhookPropertiesValidator(
            WebhookProperties properties,
            @Value("${pms.deployment.environment:production}") String environment) {
        properties.validate(environment);
        return new WebhookPropertiesValidator();
    }

    static final class WebhookPropertiesValidator {
    }
}
