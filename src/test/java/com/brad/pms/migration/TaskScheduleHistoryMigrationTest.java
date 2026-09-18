package com.brad.pms.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TaskScheduleHistoryMigrationTest {

    @Test
    void migrationDefinesTaskScheduleHistoryAndIndexes() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V45__task_schedule_history.sql"));

        assertThat(sql).contains("CREATE TABLE project_task_schedule_history");
        assertThat(sql).contains("previous_due_date  DATE NULL");
        assertThat(sql).contains("next_due_date      DATE NULL");
        assertThat(sql).contains("idx_task_schedule_history_task");
        assertThat(sql).contains("task_schedule_history_task_fk");
    }

    @Test
    void repairMigrationCanRestoreTheTableWhenVersionHistoryAlreadyExists() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V46__repair_task_schedule_history.sql"));

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS project_task_schedule_history");
        assertThat(sql).contains("idx_task_schedule_history_task");
        assertThat(sql).contains("task_schedule_history_task_fk");
    }
}
