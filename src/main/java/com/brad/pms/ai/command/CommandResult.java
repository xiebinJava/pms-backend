package com.brad.pms.ai.command;

import java.util.List;
import java.util.Map;

/** Result returned after a confirmed PMS operation is committed. */
public record CommandResult(
        String operationId,
        String status,
        String message,
        Map<String, Object> data,
        List<String> refreshScopes) {

    public CommandResult {
        data = data == null ? Map.of() : Map.copyOf(data);
        refreshScopes = refreshScopes == null ? List.of() : List.copyOf(refreshScopes);
    }
}
