package com.brad.pms.ai.command;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A safe, non-mutating command proposal request. */
public record CommandPreviewRequest(
        CommandName name,
        Map<String, Object> arguments,
        String contextId,
        String contextVersion) {

    public CommandPreviewRequest {
        name = Objects.requireNonNull(name, "name");
        Map<String, Object> copy = arguments == null
                ? Map.of()
                : new LinkedHashMap<>(arguments);
        arguments = Collections.unmodifiableMap(copy);
        contextId = requireText(contextId, "contextId");
        contextVersion = requireText(contextVersion, "contextVersion");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
