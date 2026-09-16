package com.brad.pms.common;

import com.brad.pms.common.enums.TaskScheduleState;
import com.brad.pms.common.enums.TaskStatus;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public final class TaskScheduleCalculator {
    private TaskScheduleCalculator() {
    }

    public record TaskScheduleSnapshot(TaskScheduleState state, int overdueDays) {
    }

    public static TaskScheduleSnapshot calculate(Integer taskStatus, LocalDate dueDate, LocalDate today) {
        if (Objects.equals(taskStatus, TaskStatus.DONE.getCode())) {
            return new TaskScheduleSnapshot(TaskScheduleState.COMPLETED, 0);
        }
        if (dueDate == null) {
            return new TaskScheduleSnapshot(TaskScheduleState.NO_DUE_DATE, 0);
        }
        if (dueDate.isBefore(today)) {
            return new TaskScheduleSnapshot(TaskScheduleState.OVERDUE,
                    Math.toIntExact(ChronoUnit.DAYS.between(dueDate, today)));
        }
        if (dueDate.equals(today)) {
            return new TaskScheduleSnapshot(TaskScheduleState.DUE_TODAY, 0);
        }
        return new TaskScheduleSnapshot(TaskScheduleState.ON_TIME, 0);
    }

    public static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Shanghai"));
    }
}
