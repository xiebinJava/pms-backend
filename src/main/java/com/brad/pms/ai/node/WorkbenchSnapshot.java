package com.brad.pms.ai.node;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One readable node workbench document: current values under their field names,
 * the Chinese label for every writable field, and the version the caller read.
 */
public record WorkbenchSnapshot(
        Map<String, Object> values,
        Map<String, String> labels,
        Integer version,
        Map<String, Integer> fieldVersions) {

    public WorkbenchSnapshot {
        // Current values are null for fields the document has never set, so the
        // copies must tolerate nulls (Map.copyOf rejects them).
        values = values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
        labels = labels == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(labels));
        fieldVersions = fieldVersions == null
                ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fieldVersions));
    }

    public static WorkbenchSnapshot of(Map<String, Object> values, Map<String, String> labels, Integer version) {
        return new WorkbenchSnapshot(values, labels, version, Map.of());
    }
}
