package com.brad.pms.webhook;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "pms.webhook")
public class WebhookProperties {

    private boolean enabled = false;
    private String url = "";
    private String secret = "";
    private int timeoutMs = 5000;

    public void validate(String deploymentEnvironment) {
        if (!enabled) return;
        String target = url == null ? "" : url.trim();
        if (target.isEmpty()) {
            throw new IllegalStateException("Webhook requires PMS_WEBHOOK_URL when PMS_WEBHOOK_ENABLED=true");
        }
        boolean production = deploymentEnvironment != null
                && "production".equalsIgnoreCase(deploymentEnvironment.trim());
        if (production && !target.startsWith("https://")) {
            throw new IllegalStateException("Production webhook URL must use https://");
        }
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            throw new IllegalStateException("PMS_WEBHOOK_URL must be an http(s) URL");
        }
        String key = secret == null ? "" : secret.trim();
        if (key.length() < 16) {
            throw new IllegalStateException("PMS_WEBHOOK_SECRET must contain at least 16 characters");
        }
        if (timeoutMs < 500 || timeoutMs > 30_000) {
            throw new IllegalStateException("PMS_WEBHOOK_TIMEOUT_MS must be between 500 and 30000");
        }
    }
}
