package com.brad.pms.webhook;

public interface WebhookPublisher {
    void publish(WebhookEvent event);
}
