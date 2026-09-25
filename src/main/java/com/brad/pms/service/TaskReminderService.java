package com.brad.pms.service;

import com.brad.pms.common.enums.ProjectStatus;
import com.brad.pms.common.enums.TaskStatus;
import com.brad.pms.config.TaskReminderProperties;
import com.brad.pms.dto.TaskReminderCandidate;
import com.brad.pms.mapper.ProjectTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskReminderService {

    static final int BATCH_SIZE = 500;

    private final ProjectTaskMapper taskMapper;
    private final NotificationService notificationService;
    private final TaskReminderProperties properties;

    public int run(LocalDate today) {
        LocalDate dueSoonDate = today.plusDays(properties.getDueSoonDays());
        LocalDate overdueDate = today.minusDays(1);
        int inserted = 0;
        long offset = 0;
        while (true) {
            List<TaskReminderCandidate> candidates = taskMapper.findReminderCandidates(
                    dueSoonDate, overdueDate, TaskStatus.DONE.getCode(),
                    ProjectStatus.ACTIVE.getCode(), offset, BATCH_SIZE);
            if (candidates == null || candidates.isEmpty()) break;
            for (TaskReminderCandidate candidate : candidates) {
                if (candidate == null || candidate.getDueDate() == null) continue;
                String type;
                String dedupeKey;
                String title;
                if (dueSoonDate.equals(candidate.getDueDate())) {
                    type = NotificationService.TASK_DUE_SOON;
                    dedupeKey = type + ":" + candidate.getTaskId() + ":" + candidate.getDueDate();
                    title = "任务即将到期";
                } else if (overdueDate.equals(candidate.getDueDate())) {
                    type = NotificationService.TASK_OVERDUE;
                    dedupeKey = type + ":" + candidate.getTaskId() + ":" + today;
                    title = "任务已逾期";
                } else {
                    continue;
                }
                String content = content(candidate, type);
                if (notificationService.emitInApp(candidate.getAssigneeId(), type, title, content,
                        candidate.getProjectId(), candidate.getTaskId(), null, null, dedupeKey)) {
                    inserted++;
                }
            }
            if (candidates.size() < BATCH_SIZE) break;
            offset += candidates.size();
        }
        log.info("Task reminder scan completed: date={}, inserted={}", today, inserted);
        return inserted;
    }

    private String content(TaskReminderCandidate candidate, String type) {
        String project = safe(candidate.getProjectName(), "项目");
        String task = safe(candidate.getTaskTitle(), "未命名任务");
        String state = NotificationService.TASK_OVERDUE.equals(type) ? "已逾期" : "临期";
        return project + " · " + task + "，截止日期：" + candidate.getDueDate() + "（" + state + "）";
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
