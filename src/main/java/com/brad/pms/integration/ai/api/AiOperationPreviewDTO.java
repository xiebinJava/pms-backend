package com.brad.pms.integration.ai.api;

import com.brad.pms.ai.command.CommandPreview;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Stable preview envelope returned before a CLI write is executed. */
public record AiOperationPreviewDTO(
        String operationId,
        String command,
        Instant expiresAt,
        String contextVersion,
        List<String> warnings,
        List<Map<String, Object>> changes,
        List<String> refreshScopes,
        List<CommandPreview.Precondition> preconditions,
        String provenanceRef,
        String draftDigest) {

    public AiOperationPreviewDTO(
            String operationId,
            String command,
            Instant expiresAt,
            String contextVersion,
            List<String> warnings,
            List<Map<String, Object>> changes,
            List<String> refreshScopes) {
        this(operationId, command, expiresAt, contextVersion, warnings, changes, refreshScopes, List.of(), null, null);
    }

    public AiOperationPreviewDTO(
            String operationId,
            String command,
            Instant expiresAt,
            String contextVersion,
            List<String> warnings,
            List<Map<String, Object>> changes,
            List<String> refreshScopes,
            List<CommandPreview.Precondition> preconditions) {
        this(operationId, command, expiresAt, contextVersion, warnings, changes, refreshScopes, preconditions, null, null);
    }

    public static AiOperationPreviewDTO from(CommandPreview preview) {
        return new AiOperationPreviewDTO(
                preview.operationId(),
                preview.command().code(),
                preview.expiresAt(),
                preview.contextVersion(),
                preview.warnings(),
                preview.changes(),
                preview.refreshScopes(),
                preview.preconditions(),
                preview.provenanceRef(),
                preview.draftDigest());
    }
}
