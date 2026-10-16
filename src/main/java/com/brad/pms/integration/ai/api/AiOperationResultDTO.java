package com.brad.pms.integration.ai.api;

import com.brad.pms.ai.command.CommandResult;

import java.util.List;
import java.util.Map;

/** Stable response envelope for the PMS CLI operation facade. */
public record AiOperationResultDTO(
        String operationId,
        String status,
        String message,
        Map<String, Object> data,
        List<String> refreshScopes,
        String auditId) {

    /**
     * Keeps the response shape source-compatible for internal orchestrator callers that do not
     * need to spell out the audit identifier. The current operation table uses its primary key
     * as both the operation and audit identifier.
     */
    public AiOperationResultDTO(
            String operationId,
            String status,
            String message,
            Map<String, Object> data,
            List<String> refreshScopes) {
        this(operationId, status, message, data, refreshScopes, operationId);
    }

    public static AiOperationResultDTO from(CommandResult result) {
        return new AiOperationResultDTO(
                result.operationId(),
                result.status(),
                result.message(),
                result.data(),
                result.refreshScopes(),
                result.operationId());
    }
}
