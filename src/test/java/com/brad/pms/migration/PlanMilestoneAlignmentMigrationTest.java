package com.brad.pms.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlanMilestoneAlignmentMigrationTest {

    @Test
    void removesNodePlanItemsAndLinksDevelopmentTopicsToProjectMilestones() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V32__align_plan_and_development_milestones.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).containsIgnoringCase("DROP TABLE project_node_plan_item");
        assertThat(sql).containsIgnoringCase("ALTER TABLE project_node_development_topic");
        assertThat(sql).containsIgnoringCase("milestone_id");
        assertThat(sql).containsIgnoringCase("DROP COLUMN iteration");
        assertThat(sql).containsIgnoringCase("idx_node_development_topic_milestone");
    }
}
