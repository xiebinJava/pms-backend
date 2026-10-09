package com.brad.pms.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class IterationPlanSystemVersionMigrationTest {

    @Test
    void addsAnOptionalIndexedForeignKeyWithoutRewritingIterationRows() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V64__bind_iteration_plans_to_system_versions.sql"),
                StandardCharsets.UTF_8).toLowerCase();

        assertThat(sql).contains(
                "alter table project_node_iteration_plan",
                "add column system_version_id bigint null",
                "idx_node_iteration_plan_system_version",
                "foreign key (system_version_id) references pms_system_version (id)");
        assertThat(sql).doesNotContain("drop table", "truncate", "delete from");
    }
}
