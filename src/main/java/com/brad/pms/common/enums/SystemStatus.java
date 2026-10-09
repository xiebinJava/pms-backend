package com.brad.pms.common.enums;

import java.util.Locale;

/** System lifecycle values persisted as stable upper-case codes. */
public enum SystemStatus {
    ACTIVE,
    INACTIVE;

    public static SystemStatus parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
