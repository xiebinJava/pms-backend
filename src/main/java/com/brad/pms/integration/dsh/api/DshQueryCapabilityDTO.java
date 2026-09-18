package com.brad.pms.integration.dsh.api;

import java.util.List;

/** A bounded, discoverable PMS read resource exposed to DSH. */
public record DshQueryCapabilityDTO(
        String resource,
        String description,
        List<String> fields,
        List<String> filters,
        List<String> scopes,
        int maxPageSize) {

    public DshQueryCapabilityDTO {
        fields = fields == null ? List.of() : List.copyOf(fields);
        filters = filters == null ? List.of() : List.copyOf(filters);
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        if (maxPageSize < 1) throw new IllegalArgumentException("maxPageSize must be positive");
    }
}
