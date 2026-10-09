package com.brad.pms.migration;

import com.brad.pms.common.enums.RequirementExecutionTargetType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequirementManagementMigrationTest {

    @Test
    void createsRequirementAndTargetHistoryTablesWithSingleTargetConstraints() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V56__requirement_management.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql.toLowerCase()).contains(
                "create table pms_requirement",
                "create table pms_requirement_execution_target_history",
                "execution_target_type",
                "execution_target_id",
                "check",
                "idx_requirement_execution_target",
                "previous_target_type",
                "previous_target_id");
    }

    @Test
    void preservesNullableGenericWorkflowContextFromV54() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V54__optional_development_item_project_context.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql.toLowerCase()).contains(
                "modify column project_id bigint null",
                "modify column source_node_id bigint null");
        assertThat(Files.exists(Path.of("src/main/resources/db/migration/V56__requirement_management.sql")))
                .isTrue();
    }

    @Test
    void supportsAllClosedExecutionTargetTypes() {
        assertThat(Arrays.stream(RequirementExecutionTargetType.values()).toList())
                .containsExactly(
                        RequirementExecutionTargetType.PROJECT,
                        RequirementExecutionTargetType.TOPIC,
                        RequirementExecutionTargetType.STORY);
    }

    @Test
    void rejectsUnknownExecutionTargetType() {
        assertThatThrownBy(() -> RequirementExecutionTargetType.valueOf("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
