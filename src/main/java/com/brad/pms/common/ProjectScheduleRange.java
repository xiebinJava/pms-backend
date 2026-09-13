package com.brad.pms.common;

import java.time.LocalDate;

public final class ProjectScheduleRange {
    private ProjectScheduleRange() { }

    public static boolean isOrdered(LocalDate startDate, LocalDate endDate) {
        return startDate == null || endDate == null || !startDate.isAfter(endDate);
    }

    public static boolean isCompleteAndOrdered(LocalDate startDate, LocalDate endDate) {
        return startDate != null && endDate != null && isOrdered(startDate, endDate);
    }
}
