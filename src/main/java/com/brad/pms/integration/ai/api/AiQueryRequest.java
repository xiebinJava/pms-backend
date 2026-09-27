package com.brad.pms.integration.ai.api;

import java.util.Map;
import java.util.LinkedHashMap;

/** Generic read request. Resource-specific filters are intentionally dynamic. */
public record AiQueryRequest(
        String resourceType,
        String keyword,
        Map<String, Object> filters,
        Integer page,
        Integer pageSize) {

    public AiQueryRequest {
        filters = filters == null ? Map.of() : new LinkedHashMap<>(filters);
    }
}
