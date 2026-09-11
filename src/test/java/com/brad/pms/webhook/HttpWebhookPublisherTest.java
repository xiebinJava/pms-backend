package com.brad.pms.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HttpWebhookPublisherTest {

    @Test
    void disabledPublisherDoesNotCallTheEndpoint() {
        WebhookProperties properties = new WebhookProperties();
        properties.setEnabled(false);
        AtomicReference<URI> called = new AtomicReference<>();
        HttpWebhookPublisher publisher = new HttpWebhookPublisher(
                properties, (uri, body, headers, timeoutMs) -> {
                    called.set(uri);
                    return 200;
                }, mapper());

        publisher.publish(WebhookEvent.of("TASK_ASSIGNED", 9L, 3L, 7L, List.of(8L), "任务已指派给你", "接口"));
        assertThat(called.get()).isNull();
    }

    @Test
    void signsTheJsonBodyAndSendsEventHeaders() {
        WebhookProperties properties = new WebhookProperties();
        properties.setEnabled(true);
        properties.setUrl("https://hooks.example.com/pms");
        properties.setSecret("webhook-secret-16");
        properties.setTimeoutMs(1500);

        AtomicReference<URI> uri = new AtomicReference<>();
        AtomicReference<byte[]> body = new AtomicReference<>();
        AtomicReference<Map<String, String>> headers = new AtomicReference<>();
        HttpWebhookPublisher publisher = new HttpWebhookPublisher(
                properties, (target, payload, sentHeaders, timeoutMs) -> {
                    uri.set(target);
                    body.set(payload);
                    headers.set(sentHeaders);
                    assertThat(timeoutMs).isEqualTo(1500);
                    return 204;
                }, mapper());

        WebhookEvent event = WebhookEvent.of("TASK_COMMENTED", 9L, 3L, 7L, List.of(8L), "张伟 评论了任务", "先对齐");
        publisher.publish(event);

        assertThat(uri.get()).isEqualTo(URI.create("https://hooks.example.com/pms"));
        assertThat(headers.get()).containsEntry("X-PMS-Event", "TASK_COMMENTED");
        assertThat(headers.get()).containsEntry("X-PMS-Delivery", event.id());
        assertThat(headers.get().get("X-PMS-Signature"))
                .isEqualTo("sha256=" + HttpWebhookPublisher.sign("webhook-secret-16", body.get()));
        assertThat(new String(body.get(), StandardCharsets.UTF_8)).contains("\"type\":\"TASK_COMMENTED\"");
    }

    @Test
    void deliveryErrorsDoNotEscapeToTheCaller() {
        WebhookProperties properties = new WebhookProperties();
        properties.setEnabled(true);
        properties.setUrl("https://hooks.example.com/pms");
        properties.setSecret("webhook-secret-16");
        HttpWebhookPublisher publisher = new HttpWebhookPublisher(
                properties, (uri, body, headers, timeoutMs) -> {
                    throw new java.io.IOException("down");
                }, mapper());

        publisher.publish(WebhookEvent.of("PROJECT_COMMENTED", 9L, null, 7L, List.of(8L), "评论了项目", "先对齐"));
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
