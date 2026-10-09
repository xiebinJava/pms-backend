package com.brad.pms.integration.ai.api;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Stable resource list envelope returned to the PMS CLI. */
public record AiQueryResultDTO(
        String resourceType,
        List<Item> items,
        long total,
        long page,
        long pageSize,
        long totalPage,
        String queryScope) {

    public AiQueryResultDTO {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record Item(
            String type,
            Long id,
            String name,
            String status,
            Integer version,
            Map<String, Object> summary) {
        public Item {
            summary = summary == null ? Map.of() : new LinkedHashMap<>(summary);
        }
    }
}
