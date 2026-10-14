package com.brad.pms.dto.response;

/**
 * Persistence verdict for one part of a workbench component: persisted means a durable record
 * exists, version is its real stored value (which may legitimately be 0 or unknown/null).
 */
public record PersistedContentVersion(boolean persisted, Integer version) {

    public static PersistedContentVersion absent() {
        return new PersistedContentVersion(false, null);
    }
}
