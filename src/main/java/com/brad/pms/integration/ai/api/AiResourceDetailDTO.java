package com.brad.pms.integration.ai.api;

import java.util.LinkedHashMap;
import java.util.Map;

/** Stable read-only resource detail envelope consumed by the PMS CLI. */
public record AiResourceDetailDTO(
        String resourceType,
        Long resourceId,
        Integer entityVersion,
        Map<String, Object> details) {

    public AiResourceDetailDTO {
        details = details == null ? Map.of() : new LinkedHashMap<>(details);
    }
}
