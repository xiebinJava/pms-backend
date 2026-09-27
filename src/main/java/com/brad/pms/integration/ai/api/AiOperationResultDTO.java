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
        List<String> refreshScopes) {

    public static AiOperationResultDTO from(CommandResult result) {
        return new AiOperationResultDTO(
                result.operationId(),
                result.status(),
                result.message(),
                result.data(),
                result.refreshScopes());
    }
}
