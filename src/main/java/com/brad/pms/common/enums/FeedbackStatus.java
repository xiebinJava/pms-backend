package com.brad.pms.common.enums;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Feedback ticket lifecycle. Values are persisted as stable upper-case codes. */
public enum FeedbackStatus {
    PENDING_TRIAGE,
    ASSIGNED,
    IN_PROGRESS,
    PENDING_CONFIRMATION,
    RESOLVED,
    CLOSED,
    REJECTED,
    DUPLICATE,
    UNREPRODUCIBLE;

    private static final Map<FeedbackStatus, Set<FeedbackStatus>> TRANSITIONS = Map.of(
            PENDING_TRIAGE, EnumSet.of(ASSIGNED, REJECTED, DUPLICATE, UNREPRODUCIBLE),
            ASSIGNED, EnumSet.of(IN_PROGRESS, REJECTED, DUPLICATE, UNREPRODUCIBLE),
            IN_PROGRESS, EnumSet.of(PENDING_CONFIRMATION, REJECTED, DUPLICATE, UNREPRODUCIBLE),
            PENDING_CONFIRMATION, EnumSet.of(RESOLVED, IN_PROGRESS),
            RESOLVED, EnumSet.of(CLOSED, IN_PROGRESS),
            CLOSED, EnumSet.of(IN_PROGRESS),
            REJECTED, EnumSet.of(IN_PROGRESS),
            DUPLICATE, EnumSet.of(IN_PROGRESS),
            UNREPRODUCIBLE, EnumSet.of(IN_PROGRESS));

    public static boolean canTransition(String from, String to) {
        FeedbackStatus source = parse(from);
        FeedbackStatus target = parse(to);
        return source != null && target != null && TRANSITIONS.getOrDefault(source, Set.of()).contains(target);
    }

    public static FeedbackStatus parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
