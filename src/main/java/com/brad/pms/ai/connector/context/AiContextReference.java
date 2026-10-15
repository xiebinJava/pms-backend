package com.brad.pms.ai.connector.context;

/** Stable identity for a structured reference emitted in an AI context snapshot. */
public record AiContextReference(String resourceType, Long resourceId) {

    public AiContextReference {
        resourceType = resourceType == null ? "" : resourceType.trim().toLowerCase(java.util.Locale.ROOT)
                .replace('-', '_');
    }
}
