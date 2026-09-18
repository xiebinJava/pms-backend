package com.brad.pms.ai.query;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Authoritative, structured result returned by a DSH resource query. */
public record DshQueryResult(
        String resource,
        boolean authoritative,
        Instant capturedAt,
        Map<String, Object> data,
        Map<String, Object> pagination) {

    public DshQueryResult {
        data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
        pagination = pagination == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(pagination));
    }
}
