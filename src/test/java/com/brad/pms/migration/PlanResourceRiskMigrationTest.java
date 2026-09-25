package com.brad.pms.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlanResourceRiskMigrationTest {

    @Test
    void storesTheSolutionDecisionVersionUsedByThePlanBaseline() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V26__plan_resource_risk_source_version.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).containsIgnoringCase("ALTER TABLE project_node_plan_baseline");
        assertThat(sql).containsIgnoringCase("solution_decision_version");
    }
}
