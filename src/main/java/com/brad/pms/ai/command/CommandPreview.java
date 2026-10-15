package com.brad.pms.ai.command;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The user-visible diff produced by a non-mutating command preview. */
public record CommandPreview(
        String operationId,
        CommandName command,
        Instant expiresAt,
        String contextVersion,
        List<String> warnings,
        List<Map<String, Object>> changes,
        List<String> refreshScopes,
        List<Precondition> preconditions) {

    public CommandPreview(
            String operationId,
            CommandName command,
            Instant expiresAt,
            String contextVersion,
            List<String> warnings,
            List<Map<String, Object>> changes,
            List<String> refreshScopes) {
        this(operationId, command, expiresAt, contextVersion, warnings, changes, refreshScopes, List.of());
    }

    public CommandPreview {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        changes = changes == null ? List.of() : changes.stream()
                .map(change -> change == null ? Map.<String, Object>of()
                        : Collections.unmodifiableMap(new LinkedHashMap<>(change)))
                .toList();
        refreshScopes = refreshScopes == null ? List.of() : List.copyOf(refreshScopes);
        preconditions = preconditions == null ? List.of() : List.copyOf(preconditions);
    }

    public record Precondition(
            String code,
            String message,
            boolean blocking,
            String suggestedAction,
            Map<String, Object> details) {

        public Precondition {
            code = Objects.requireNonNull(code, "code");
            message = Objects.requireNonNull(message, "message");
            details = details == null ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(details));
        }
    }
}
