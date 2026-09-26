package com.brad.pms.migration;

import com.brad.pms.dto.request.TaskCreateCmd;
import com.brad.pms.dto.request.TaskUpdateCmd;
import com.brad.pms.dto.response.ProjectTaskDTO;
import com.brad.pms.entity.ProjectTaskDO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class IterationPlanTaskContractTest {

    @Test
    void taskContractsExposeOptionalIterationAssociation() {
        assertThat(fieldNames(TaskCreateCmd.class)).contains("iterationPlanId");
        assertThat(fieldNames(TaskUpdateCmd.class)).contains("iterationPlanId", "clearIterationPlan");
        assertThat(fieldNames(ProjectTaskDO.class)).contains("iterationPlanId");
        assertThat(fieldNames(ProjectTaskDTO.class)).contains("iterationPlanId", "iterationPlanName");
    }

    @Test
    void migrationAddsAnIndexedNullableTaskIterationAssociation() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V57__task_iteration_plan.sql");
        assertThat(Files.exists(migration)).isTrue();
        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        assertThat(sql).containsIgnoringCase("ALTER TABLE project_task");
        assertThat(sql).containsIgnoringCase("ADD COLUMN iteration_plan_id BIGINT NULL");
        assertThat(sql).containsIgnoringCase("idx_task_iteration_plan");

        String schema = Files.readString(Path.of("src/main/resources/schema.sql"), StandardCharsets.UTF_8);
        assertThat(schema).containsIgnoringCase("iteration_plan_id BIGINT");
        assertThat(schema).containsIgnoringCase("idx_task_iteration_plan");
    }

    private static String[] fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).toArray(String[]::new);
    }
}
