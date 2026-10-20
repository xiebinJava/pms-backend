package com.brad.pms.ai.command;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** The user-visible diff produced by a non-mutating command preview. */
public record CommandPreview(
        String operationId,
        CommandName command,
        Instant expiresAt,
        String contextVersion,
        Map<String, Object> expectedVersions,
        List<String> warnings,
        List<Map<String, Object>> changes,
        List<String> refreshScopes,
        List<Precondition> preconditions,
        String provenanceRef,
        String draftDigest) {

    public CommandPreview(
            String operationId,
            CommandName command,
            Instant expiresAt,
            String contextVersion,
            List<String> warnings,
            List<Map<String, Object>> changes,
            List<String> refreshScopes) {
        this(operationId, command, expiresAt, contextVersion, expectedVersions(contextVersion, command, changes),
                warnings, changes, refreshScopes, List.of(), null, null);
    }

    public CommandPreview(
            String operationId,
            CommandName command,
            Instant expiresAt,
            String contextVersion,
            List<String> warnings,
            List<Map<String, Object>> changes,
            List<String> refreshScopes,
            List<Precondition> preconditions) {
        this(operationId, command, expiresAt, contextVersion, expectedVersions(contextVersion, command, changes),
                warnings, changes, refreshScopes, preconditions, null, null);
    }

    public CommandPreview(
            String operationId,
            CommandName command,
            Instant expiresAt,
            String contextVersion,
            List<String> warnings,
            List<Map<String, Object>> changes,
            List<String> refreshScopes,
            List<Precondition> preconditions,
            String provenanceRef,
            String draftDigest) {
        this(operationId, command, expiresAt, contextVersion, expectedVersions(contextVersion, command, changes),
                warnings, changes, refreshScopes, preconditions, provenanceRef, draftDigest);
    }

    public CommandPreview {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        changes = changes == null ? List.of() : changes.stream()
                .map(change -> change == null ? Map.<String, Object>of()
                        : Collections.unmodifiableMap(new LinkedHashMap<>(change)))
                .toList();
        refreshScopes = refreshScopes == null ? List.of() : List.copyOf(refreshScopes);
        preconditions = preconditions == null ? List.of() : List.copyOf(preconditions);
        expectedVersions = expectedVersions == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(expectedVersions));
    }

    /**
     * Extracts the versions that PMS read while building a command preview.
     * The individual command changes remain the legacy execution guard, while
     * this stable envelope gives clients one server-owned snapshot to bind to
     * their draft digest.
     */
    public static Map<String, Object> expectedVersions(String contextVersion,
                                                        CommandName command,
                                                        List<Map<String, Object>> changes) {
        Map<String, Object> versions = new LinkedHashMap<>();
        versions.put("entityVersion", null);
        versions.put("nodeVersion", null);
        versions.put("workflowInstanceVersion", null);
        versions.put("componentVersion", null);
        if (contextVersion != null && !contextVersion.isBlank()) {
            versions.put("contextVersion", contextVersion);
        } else {
            versions.put("contextVersion", null);
        }
        if (changes == null) return versions;
        Set<String> versionKeys = Set.of(
                "version", "entityVersion", "nodeVersion", "workflowInstanceVersion", "componentVersion",
                "projectVersion", "requirementVersion", "topicVersion", "storyVersion", "workbenchVersion",
                "fieldVersions", "fieldValueVersions", "expectedVersion", "taskVersion", "memberVersion");
        for (Map<String, Object> change : changes) {
            if (change == null) continue;
            for (Map.Entry<String, Object> entry : change.entrySet()) {
                if (versionKeys.contains(entry.getKey())) {
                    if ("projectVersion".equals(entry.getKey())
                            || "requirementVersion".equals(entry.getKey())
                            || "topicVersion".equals(entry.getKey())
                            || "storyVersion".equals(entry.getKey())
                            || "expectedVersion".equals(entry.getKey())
                            || "taskVersion".equals(entry.getKey())
                            || "memberVersion".equals(entry.getKey())) {
                        versions.putIfAbsent("entityVersion", entry.getValue());
                    } else if ("version".equals(entry.getKey())) {
                        String commandCode = command == null ? "" : command.code();
                        String versionTarget = commandCode.contains("node")
                                ? "nodeVersion" : "entityVersion";
                        versions.putIfAbsent(versionTarget, entry.getValue());
                    } else if ("workbenchVersion".equals(entry.getKey())) {
                        versions.putIfAbsent("componentVersion", entry.getValue());
                    } else if ("fieldVersions".equals(entry.getKey())
                            || "fieldValueVersions".equals(entry.getKey())) {
                        versions.putIfAbsent(entry.getKey(), entry.getValue());
                    } else {
                        versions.putIfAbsent(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
        return versions;
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
