package com.brad.pms.ai.query;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Bounded resource query contract exposed to the DSH PMS plugin. */
public record DshQueryRequest(
        String resource,
        Map<String, Object> filters,
        List<String> fields,
        Integer page,
        Integer pageSize) {

    public DshQueryRequest {
        resource = resource == null ? "" : resource.trim().toLowerCase(Locale.ROOT);
        Map<String, Object> filterCopy = filters == null ? Map.of() : new LinkedHashMap<>(filters);
        filters = Collections.unmodifiableMap(filterCopy);
        fields = fields == null ? List.of() : List.copyOf(fields);
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 50 : pageSize;
        if (resource.isBlank()) throw new IllegalArgumentException("resource must not be blank");
        if (page < 1) throw new IllegalArgumentException("page must be at least 1");
        if (pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("pageSize must be between 1 and 100");
    }
}
