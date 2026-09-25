package com.brad.pms.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TaskRequirementLinkMigrationTest {

    @Test
    void createsTheTaskRequirementLinkTable() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V21__task_requirement_link.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).containsIgnoringCase("CREATE TABLE project_task_requirement");
        assertThat(sql).containsIgnoringCase("task_id");
        assertThat(sql).containsIgnoringCase("requirement_id");
        assertThat(sql).containsIgnoringCase("uk_task_requirement");
        assertThat(sql).containsIgnoringCase("idx_requirement_tasks");
    }
}
