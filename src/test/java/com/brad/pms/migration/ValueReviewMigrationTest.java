package com.brad.pms.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ValueReviewMigrationTest {

    @Test
    void createsValueReviewBaselineWithOptimisticLockingFields() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V31__value_validation_project_retrospective.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).containsIgnoringCase("CREATE TABLE project_node_value_review");
        assertThat(sql).containsIgnoringCase("result_status");
        assertThat(sql).containsIgnoringCase("actual_result");
        assertThat(sql).containsIgnoringCase("retrospective_conclusion");
        assertThat(sql).containsIgnoringCase("follow_up_actions");
        assertThat(sql).containsIgnoringCase("version");
        assertThat(sql).containsIgnoringCase("UNIQUE KEY");
    }
}
