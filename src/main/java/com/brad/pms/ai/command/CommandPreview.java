package com.brad.pms.ai.command;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The user-visible diff produced by a non-mutating command preview. */
public record CommandPreview(
        String operationId,
        CommandName command,
        Instant expiresAt,
        String contextVersion,
        List<String> warnings,
        List<Map<String, Object>> changes,
        List<String> refreshScopes) {

    public CommandPreview {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        changes = changes == null ? List.of() : changes.stream()
                .map(change -> change == null ? Map.<String, Object>of()
                        : Collections.unmodifiableMap(new LinkedHashMap<>(change)))
                .toList();
        refreshScopes = refreshScopes == null ? List.of() : List.copyOf(refreshScopes);
    }
}
