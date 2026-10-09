package com.brad.pms.common.enums;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** System version lifecycle and its explicitly allowed transitions. */
public enum SystemVersionStatus {
    PLANNED,
    DEVELOPING,
    RELEASED,
    ARCHIVED;

    private static final Map<SystemVersionStatus, Set<SystemVersionStatus>> TRANSITIONS = Map.of(
            PLANNED, EnumSet.of(DEVELOPING),
            DEVELOPING, EnumSet.of(RELEASED),
            RELEASED, EnumSet.of(ARCHIVED),
            ARCHIVED, Set.of());

    public static SystemVersionStatus parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static boolean canTransition(String from, String to) {
        SystemVersionStatus source = parse(from);
        return source != null && source.canTransitionTo(parse(to));
    }

    public boolean canTransitionTo(SystemVersionStatus target) {
        return target != null && TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
