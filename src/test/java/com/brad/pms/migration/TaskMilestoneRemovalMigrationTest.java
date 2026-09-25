package com.brad.pms.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TaskMilestoneRemovalMigrationTest {

    @Test
    void removesTaskMilestoneAssociationFromTheDatabase() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V33__remove_task_milestone_association.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).containsIgnoringCase("ALTER TABLE project_task");
        assertThat(sql).containsIgnoringCase("DROP FOREIGN KEY task_milestone_fk");
        assertThat(sql).containsIgnoringCase("DROP COLUMN milestone_id");
    }
}
