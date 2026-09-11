package com.brad.pms.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class IterationPlanMigrationTest {

    @Test
    void addsIterationPlansWithoutDestroyingLegacyMilestones() throws Exception {
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V37__add_iteration_plans.sql"),
                StandardCharsets.UTF_8);
        String schema = Files.readString(Path.of("src/main/resources/schema.sql"), StandardCharsets.UTF_8);

        assertThat(migration).containsIgnoringCase("CREATE TABLE project_node_iteration_plan");
        assertThat(migration).containsIgnoringCase("ALTER TABLE project_node_development_story");
        assertThat(migration).containsIgnoringCase("ADD COLUMN iteration_plan_id BIGINT NULL");
        assertThat(migration).containsIgnoringCase("idx_node_development_story_iteration_plan");
        assertThat(schema).containsIgnoringCase("project_node_iteration_plan");
        assertThat(schema).containsIgnoringCase("iteration_plan_id");
        assertThat(schema).containsIgnoringCase("project_milestone");
    }

    @Test
    void refreshesTheActivePlanNodeCopyWithoutDroppingLegacyStorage() throws Exception {
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V38__refresh_plan_node_description.sql"),
                StandardCharsets.UTF_8);

        assertThat(migration).contains("node_key = 'plan'");
        assertThat(migration).contains("规划迭代计划与上线时间");
        assertThat(migration).contains("LIKE '%里程碑%'");
    }
}
