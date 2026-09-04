package com.brad.pms.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.support.CronExpression;

import java.time.DateTimeException;
import java.time.ZoneId;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "pms.notification.task-reminder")
public class TaskReminderProperties {

    private boolean enabled = false;
    private String cron = "0 0 9 * * *";
    private String zone = "Asia/Shanghai";
    private int dueSoonDays = 7;

    @PostConstruct
    public void validate() {
        validateDueSoonDays(dueSoonDays);
        validateCron(cron);
        validateZone(zone);
    }

    public void setDueSoonDays(int dueSoonDays) {
        validateDueSoonDays(dueSoonDays);
        this.dueSoonDays = dueSoonDays;
    }

    public void setCron(String cron) {
        validateCron(cron);
        this.cron = cron;
    }

    public void setZone(String zone) {
        validateZone(zone);
        this.zone = zone;
    }

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }

    private static void validateDueSoonDays(int value) {
        if (value < 1 || value > 30) {
            throw new IllegalArgumentException("dueSoonDays must be between 1 and 30");
        }
    }

    private static void validateCron(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("cron must not be blank");
        }
        try {
            CronExpression.parse(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("cron is invalid", ex);
        }
    }

    private static void validateZone(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("zone must not be blank");
        }
        try {
            ZoneId.of(value);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("zone is invalid", ex);
        }
    }
}
