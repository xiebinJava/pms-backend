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
        assertThat(source).contains("@PostMapping(\"/iteration-plans/page\")", "@GetMapping(\"/iteration-plans/{id}\")");
        assertThat(source).doesNotContain("WorkflowTemplateService", "DevelopmentItemWorkflowService");
    }
}
