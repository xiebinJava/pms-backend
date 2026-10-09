package com.brad.pms.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class IterationPlanWorkbenchContractTest {

    @Test
    void exposesAStandaloneIterationPageAndDetailContract() throws Exception {
        Path controller = Path.of("src/main/java/com/brad/pms/controller/IterationPlanController.java");
        Path pageQuery = Path.of("src/main/java/com/brad/pms/dto/request/IterationPlanPageQry.java");
        Path listDto = Path.of("src/main/java/com/brad/pms/dto/response/IterationPlanListDTO.java");
        Path detailDto = Path.of("src/main/java/com/brad/pms/dto/response/IterationPlanDetailDTO.java");
        assertThat(Files.exists(controller)).isTrue();
        assertThat(Files.exists(pageQuery)).isTrue();
        assertThat(Files.exists(listDto)).isTrue();
        assertThat(Files.exists(detailDto)).isTrue();

        String source = Files.readString(controller, StandardCharsets.UTF_8);
        assertThat(source).contains("@PostMapping(\"/projects/{projectId}/iteration-plans\")",
                "@PostMapping(\"/iteration-plans/page\")", "@GetMapping(\"/iteration-plans/{id}\")",
                "@DeleteMapping(\"/iteration-plans/{id}\")");
        assertThat(source).doesNotContain("WorkflowTemplateService", "DevelopmentItemWorkflowService");

        String planDto = Files.readString(Path.of(
                "src/main/java/com/brad/pms/dto/response/NodeIterationPlanDTO.java"), StandardCharsets.UTF_8);
        assertThat(planDto).contains("systemId", "systemVersionId", "systemVersionNo", "systemVersionName", "systemName");
        String query = Files.readString(pageQuery, StandardCharsets.UTF_8);
        assertThat(query).contains("systemVersionId");

        String schema = Files.readString(Path.of("src/main/resources/schema.sql"), StandardCharsets.UTF_8);
        assertThat(schema).contains("project_id  BIGINT       NULL", "node_id     BIGINT       NULL");
        Path unboundMigration = Path.of("src/main/resources/db/migration/V66__allow_unbound_iteration_plans.sql");
        assertThat(Files.exists(unboundMigration)).isTrue();
        assertThat(Files.readString(unboundMigration, StandardCharsets.UTF_8))
                .contains("MODIFY COLUMN project_id BIGINT NULL", "MODIFY COLUMN node_id BIGINT NULL");
    }
}
