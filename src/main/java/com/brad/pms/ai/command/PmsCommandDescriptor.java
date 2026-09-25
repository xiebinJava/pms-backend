package com.brad.pms.ai.command;

import java.util.List;
import java.util.Map;

/** Server-owned metadata used to publish a command to an external Agent. */
public record PmsCommandDescriptor(
        CommandName name,
        String description,
        String access,
        String risk,
        boolean requiresConfirmation,
        List<String> scopes,
        Map<String, Object> parameters,
        boolean supportsPreview,
        boolean supportsExecute,
        List<String> refreshScopes) {

    public PmsCommandDescriptor {
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        refreshScopes = refreshScopes == null ? List.of() : List.copyOf(refreshScopes);
    }
}
