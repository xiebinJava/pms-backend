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
        String contextVersion,
        String contractId,
        String contractVersion) {

    public CommandPreviewRequest(
            CommandName name,
            Map<String, Object> arguments,
            String contextId,
            String contextVersion) {
        this(name, arguments, contextId, contextVersion, null, null);
    }

    public CommandPreviewRequest {
        name = Objects.requireNonNull(name, "name");
        Map<String, Object> copy = arguments == null
                ? Map.of()
                : new LinkedHashMap<>(arguments);
        arguments = Collections.unmodifiableMap(copy);
        contextId = requireText(contextId, "contextId");
        contextVersion = requireText(contextVersion, "contextVersion");
        if ((contractId == null) != (contractVersion == null)
                || (contractId != null && contractId.isBlank())
                || (contractVersion != null && contractVersion.isBlank())) {
            throw new IllegalArgumentException("contractId 与 contractVersion 必须同时提供");
        }
        contractId = contractId == null ? null : contractId.trim();
        contractVersion = contractVersion == null ? null : contractVersion.trim();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
