package com.brad.pms.webhook;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WebhookEvent(
        String id,
        String type,
        Instant occurredAt,
        Long projectId,
        Long taskId,
        Long actorId,
        List<Long> recipientIds,
        String title,
        String content
) {
    public static WebhookEvent of(String type, Long projectId, Long taskId, Long actorId,
                                  List<Long> recipientIds, String title, String content) {
        return new WebhookEvent(
                UUID.randomUUID().toString(),
                type,
                Instant.now(),
                projectId,
                taskId,
                actorId,
                recipientIds == null ? List.of() : List.copyOf(recipientIds),
                title,
                content);
    }
}
