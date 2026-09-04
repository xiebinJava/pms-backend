package com.brad.pms.service;

import com.brad.pms.dto.TaskReminderCandidate;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.webhook.WebhookPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskReminderServiceTest {

    @Mock ProjectTaskMapper taskMapper;
    @Mock NotificationService notificationService;
    @Mock WebhookPublisher webhookPublisher;

    @Test
    void emitsOnlyTheTwoBoundaryRemindersWithoutWebhook() {
        TaskReminderService service = new TaskReminderService(taskMapper, notificationService,
                new com.brad.pms.config.TaskReminderProperties());
        LocalDate today = LocalDate.of(2026, 9, 4);
        when(taskMapper.findReminderCandidates(today.plusDays(7), today.minusDays(1), 2, 1, 0, 500))
                .thenReturn(List.of(
                        candidate(1L, 20L, 10L, "接口评审", "研发平台", today.plusDays(7)),
                        candidate(2L, 20L, 11L, "补齐文档", "研发平台", today.minusDays(1))));
        when(notificationService.emitInApp(any(), anyString(), anyString(), anyString(), any(), any(),
                isNull(), isNull(), anyString())).thenReturn(true);

        assertThat(service.run(today)).isEqualTo(2);

        verify(notificationService).emitInApp(eq(10L), eq(NotificationService.TASK_DUE_SOON),
                anyString(), anyString(), eq(20L), eq(1L), isNull(), isNull(),
                eq("TASK_DUE_SOON:1:2026-09-11"));
        verify(notificationService).emitInApp(eq(11L), eq(NotificationService.TASK_OVERDUE),
                anyString(), anyString(), eq(20L), eq(2L), isNull(), isNull(),
                eq("TASK_OVERDUE:2:2026-09-04"));
        verify(webhookPublisher, never()).publish(any());
    }

    @Test
    void doesNotBackfillDatesInsideWindowOrHistoricalOverdue() {
        TaskReminderService service = new TaskReminderService(taskMapper, notificationService,
                new com.brad.pms.config.TaskReminderProperties());
        LocalDate today = LocalDate.of(2026, 9, 4);
        when(taskMapper.findReminderCandidates(today.plusDays(7), today.minusDays(1), 2, 1, 0, 500))
                .thenReturn(List.of());

        assertThat(service.run(today)).isZero();
        verify(notificationService, never()).emitInApp(any(), anyString(), anyString(), anyString(),
                any(), any(), any(), any(), anyString());
    }

    private static TaskReminderCandidate candidate(Long taskId, Long projectId, Long assigneeId,
                                                   String taskTitle, String projectName, LocalDate dueDate) {
        return new TaskReminderCandidate(taskId, projectId, assigneeId, taskTitle, projectName, dueDate);
    }
}
