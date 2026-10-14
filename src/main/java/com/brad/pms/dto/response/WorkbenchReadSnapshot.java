package com.brad.pms.dto.response;

import java.util.List;
import java.util.Map;

/**
 * Side-effect-free read snapshot of one workbench component: the display DTO for projection,
 * whether the component has any persisted content at all, and per-part persistence verdicts.
 * It never offers ensure-exists/save behaviour.
 */
public record WorkbenchReadSnapshot<T>(
        T display,
        boolean hasPersistedContent,
        Map<String, PersistedContentVersion> parts) {

    public WorkbenchReadSnapshot {
        parts = parts == null ? Map.of() : Map.copyOf(parts);
    }
}
