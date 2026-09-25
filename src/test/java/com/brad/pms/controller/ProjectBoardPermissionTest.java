package com.brad.pms.controller;

import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectBoardPermissionTest {
    @Test
    void boardIsAProjectReadEndpoint() {
        var method = Arrays.stream(ProjectController.class.getDeclaredMethods())
                .filter(candidate -> candidate.isAnnotationPresent(GetMapping.class))
                .filter(candidate -> Arrays.asList(candidate.getAnnotation(GetMapping.class).value()).contains("/board"))
                .findFirst();
        assertThat(method).as("GET /projects/board must exist").isPresent();
        assertThat(method.orElseThrow().getAnnotation(RequirePermission.class)).isNotNull();
        assertThat(method.orElseThrow().getAnnotation(RequirePermission.class).value())
                .isEqualTo(PermissionCode.PROJECT_READ);
    }

    @Test
    @SuppressWarnings("unchecked")
    void openApiParsesAndDocumentsTheBoardEnvelopeAndNullableSources() throws Exception {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try (var source = getClass().getResourceAsStream("/openapi/pms-api.yaml")) {
            Map<String, Object> api = new Yaml(new SafeConstructor(options)).load(source);
            Map<String, Object> paths = (Map<String, Object>) api.get("paths");
            Map<String, Object> route = (Map<String, Object>) paths.get("/projects/board");
            assertThat(route).containsKey("get");
            Map<String, Object> get = (Map<String, Object>) route.get("get");
            assertThat((Map<String, Object>) get.get("responses")).containsKeys("200", "401", "403");
            Map<String, Object> components = (Map<String, Object>) api.get("components");
            Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
            Map<String, Object> row = (Map<String, Object>) schemas.get("ProjectBoardRow");
            Map<String, Object> properties = (Map<String, Object>) row.get("properties");
            for (String name : List.of("expectedProgress", "progressVariance", "openRiskCount", "highRiskCount",
                    "mediumRiskCount", "nextNode", "storySummary", "acceptanceSummary")) {
                assertThat((Map<String, Object>) properties.get(name)).as(name).containsEntry("nullable", true);
            }
            assertThat(schemas).containsKeys("ProjectBoardResponse", "ProjectDTO");
            Map<String, Object> responseProperties = (Map<String, Object>) ((Map<String, Object>) schemas.get("ApiResponse")).get("properties");
            assertThat((Map<String, Object>) responseProperties.get("code")).containsEntry("example", 200);
            Map<String, Object> projectProperties = (Map<String, Object>) ((Map<String, Object>) schemas.get("ProjectDTO")).get("properties");
            for (String name : List.of("createdAt", "updatedAt")) {
                Map<String, Object> timestamp = (Map<String, Object>) projectProperties.get(name);
                assertThat(timestamp).as(name).containsEntry("type", "string")
                        .containsEntry("nullable", true)
                        .containsEntry("example", "2026-09-13T20:15:00")
                        .doesNotContainKey("format");
                assertThat(timestamp.get("description").toString()).contains("不含时区偏移");
            }
        }
    }
}
