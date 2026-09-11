package com.brad.pms.job;

import com.brad.pms.config.TaskReminderProperties;
import com.brad.pms.service.TaskReminderService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TaskReminderJobTest {

    @Test
    void usesConfiguredZoneInsteadOfJvmDefaultZone() {
        TaskReminderProperties properties = new TaskReminderProperties();
        properties.setZone("Asia/Shanghai");
        TaskReminderService service = mock(TaskReminderService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-03T16:30:00Z"), ZoneId.of("UTC"));

        TaskReminderJob job = new TaskReminderJob(service, properties, clock);
        job.run();

        verify(service).run(LocalDate.of(2026, 9, 4));
    }
}
