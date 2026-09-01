package com.brad.pms.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class HttpWebhookPublisher implements WebhookPublisher {

    private static final Logger log = LoggerFactory.getLogger(HttpWebhookPublisher.class);

    private final WebhookProperties properties;
    private final WebhookHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpWebhookPublisher(WebhookProperties properties, WebhookHttpClient httpClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(WebhookEvent event) {
        if (!properties.isEnabled() || event == null) return;
        try {
            byte[] body = objectMapper.writeValueAsBytes(event);
            String signature = sign(properties.getSecret().trim(), body);
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("User-Agent", "pms-backend");
            headers.put("X-PMS-Event", event.type());
            headers.put("X-PMS-Delivery", event.id());
            headers.put("X-PMS-Signature", "sha256=" + signature);
            int status = httpClient.post(URI.create(properties.getUrl().trim()), body, headers, properties.getTimeoutMs());
            if (status < 200 || status >= 300) {
                log.warn("webhook delivery failed event={} delivery={} status={}", event.type(), event.id(), status);
            }
        } catch (Exception ex) {
            log.warn("webhook delivery failed event={} delivery={}", event.type(), event.id());
        }
    }

    static String sign(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception ex) {
            throw new IllegalStateException("unable to sign webhook payload");
        }
    }
}
