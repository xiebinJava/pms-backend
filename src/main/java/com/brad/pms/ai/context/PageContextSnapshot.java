package com.brad.pms.ai.context;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Backend-owned, versioned snapshot used as the AI conversation context.
 */
public record PageContextSnapshot(
        String contextId,
        PageContextType pageType,
        String route,
        Long projectId,
        Long nodeId,
        Instant capturedAt,
        String version,
        Map<String, Object> data) {

    public PageContextSnapshot {
        contextId = requireText(contextId, "contextId");
        pageType = Objects.requireNonNull(pageType, "pageType");
        route = route == null ? "" : route;
        capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
        version = requireText(version, "version");
        Map<String, Object> copy = data == null
                ? Map.of()
                : new LinkedHashMap<>(data);
        data = Collections.unmodifiableMap(copy);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
