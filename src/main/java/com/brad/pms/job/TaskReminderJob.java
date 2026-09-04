package com.brad.pms.job;

import com.brad.pms.config.TaskReminderProperties;
import com.brad.pms.service.TaskReminderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
@ConditionalOnProperty(
        prefix = "pms.notification.task-reminder",
        name = "enabled",
        havingValue = "true")
public class TaskReminderJob {

    private final TaskReminderService reminderService;
    private final TaskReminderProperties properties;
    private final Clock clock;

    public TaskReminderJob(TaskReminderService reminderService,
                           TaskReminderProperties properties,
                           Clock clock) {
        this.reminderService = reminderService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${pms.notification.task-reminder.cron:0 0 9 * * *}",
            zone = "${pms.notification.task-reminder.zone:Asia/Shanghai}")
    public void run() {
        reminderService.run(LocalDate.now(clock.withZone(properties.zoneId())));
    }
}
