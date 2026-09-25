package com.brad.pms.integration.dsh.api;

import java.util.List;
import java.util.Map;

/** A discoverable PMS command with its safety and authorization contract. */
public record DshCommandCapabilityDTO(
        String name,
        String description,
        String access,
        String risk,
        boolean requiresConfirmation,
        List<String> scopes,
        Map<String, Object> parameters,
        boolean supportsPreview,
        boolean supportsExecute,
        List<String> refreshScopes) {

    public DshCommandCapabilityDTO {
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        refreshScopes = refreshScopes == null ? List.of() : List.copyOf(refreshScopes);
    }
}
