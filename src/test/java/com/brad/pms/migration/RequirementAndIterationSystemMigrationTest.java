package com.brad.pms.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RequirementAndIterationSystemMigrationTest {

    @Test
    void addsOptionalSystemReferencesAndBackfillsExistingIterationPlans() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V65__bind_requirements_and_iterations_to_systems.sql"),
                StandardCharsets.UTF_8).toLowerCase();

        assertThat(sql).contains(
                "alter table pms_requirement",
                "add column system_id bigint null",
                "idx_requirement_system",
                "foreign key (system_id) references pms_system",
                "alter table project_node_iteration_plan",
                "add column system_id bigint null",
                "idx_iteration_plan_system",
                "foreign key (system_id) references pms_system",
                "update project_node_iteration_plan",
                "version.system_id");
        assertThat(sql).doesNotContain("drop table", "truncate", "delete from");
    }
}
