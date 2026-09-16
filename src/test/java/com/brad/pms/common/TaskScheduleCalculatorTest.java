package com.brad.pms.common;

import com.brad.pms.common.enums.TaskScheduleState;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TaskScheduleCalculatorTest {
    @Test
    void unfinishedTaskDueBeforeTodayIsOverdue() {
        var result = TaskScheduleCalculator.calculate(1,
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16));

        assertThat(result.state()).isEqualTo(TaskScheduleState.OVERDUE);
        assertThat(result.overdueDays()).isEqualTo(2);
    }

    @Test
    void dueTodayIsNotOverdueUntilTheNextCalendarDate() {
        var result = TaskScheduleCalculator.calculate(1,
                LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 16));

        assertThat(result.state()).isEqualTo(TaskScheduleState.DUE_TODAY);
        assertThat(result.overdueDays()).isZero();
    }

    @Test
    void completedTaskWithPastDueDateIsCompleted() {
        var result = TaskScheduleCalculator.calculate(2,
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16));

        assertThat(result.state()).isEqualTo(TaskScheduleState.COMPLETED);
        assertThat(result.overdueDays()).isZero();
    }

    @Test
    void unfinishedTaskWithoutDueDateHasNoDueDate() {
        assertThat(TaskScheduleCalculator.calculate(1, null, LocalDate.of(2026, 9, 16)).state())
                .isEqualTo(TaskScheduleState.NO_DUE_DATE);
    }

    @Test
    void unfinishedTaskWithFutureDueDateIsOnTime() {
        assertThat(TaskScheduleCalculator.calculate(0,
                LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 16)).state())
                .isEqualTo(TaskScheduleState.ON_TIME);
    }

    @Test
    void reopenedTaskWithPastDueDateIsOverdue() {
        assertThat(TaskScheduleCalculator.calculate(1,
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16)).state())
                .isEqualTo(TaskScheduleState.OVERDUE);
    }
}
