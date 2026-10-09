package com.brad.pms.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** Confirms a wiped database is reseeded with the seven story node workbenches. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StoryWorkflowDefaultSeedIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired com.brad.pms.service.WorkflowTemplateService templates;

    @Test
    void userPublicationsAdvanceToTwoAndThreeWithoutChangingTheInitialSnapshot() throws Exception {
        Long id = jdbc.queryForObject("SELECT t.id FROM pms_workflow_template t JOIN pms_project_type pt ON pt.id=t.project_type_id WHERE pt.code='general'", Long.class);
        String initial = jdbc.queryForObject("SELECT definition_json FROM pms_workflow_template_version WHERE template_id=? AND version_no=1", String.class, id);
        var command = new com.brad.pms.dto.request.WorkflowTemplateSaveCmd();
        command.setName("项目管理流程");
        command.setDefinition(mapper.readValue(initial, com.brad.pms.workflow.WorkflowTemplateDefinition.class));
        assertThat(templates.saveDraft(id, command).getDraftVersionNo()).isEqualTo(2);
        assertThat(templates.publish(id).getPublishedVersionNo()).isEqualTo(2);
        assertThat(templates.saveDraft(id, command).getDraftVersionNo()).isEqualTo(3);
        assertThat(templates.publish(id).getPublishedVersionNo()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT definition_json FROM pms_workflow_template_version WHERE template_id=? AND version_no=1", String.class, id)).isEqualTo(initial);
    }

    @Test
    void freshInstallationStartsEachSystemWorkflowAtVersionOneWithCanonicalName() {
        var rows = jdbc.queryForList("""
                SELECT pt.code, t.name, v.version_no
                FROM pms_project_type pt
                JOIN pms_workflow_template_version v ON v.id=pt.default_template_version_id
                JOIN pms_workflow_template t ON t.id=v.template_id
                WHERE pt.code IN ('general','topic-management','story-management','requirement-management')
                ORDER BY pt.code
                """);
        assertThat(rows).hasSize(4);
        assertThat(rows).extracting(row -> row.get("version_no")).containsOnly(1);
        assertThat(rows).extracting(row -> row.get("name")).containsExactly(
                "项目管理流程", "需求管理流程", "故事管理流程", "专题管理流程");
    }

    @Test
    void freshDatabaseSeedsStoryDefaultWithEveryNodeWorkbench() throws Exception {
        Long typeId = jdbc.queryForObject(
                "SELECT id FROM pms_project_type WHERE code='story-management' AND deleted=FALSE", Long.class);
        Long versionId = jdbc.queryForObject(
                "SELECT default_template_version_id FROM pms_project_type WHERE id=?", Long.class, typeId);
        assertThat(versionId).isNotNull();

        JsonNode definition = mapper.readTree(jdbc.queryForObject(
                "SELECT definition_json FROM pms_workflow_template_version WHERE id=?", String.class, versionId));
        JsonNode nodes = definition.path("nodes");
        assertThat(nodes).hasSize(7);
        assertThat(definition.path("sourceTopicNodeKey").asText()).isEqualTo("custom-node-3");

        boolean testing = false;
        for (JsonNode node : nodes) {
            JsonNode configs = node.path("componentConfigs");
            if (node.path("name").asText().contains("测试")) {
                assertThat(configs.path("story-testing").path("testingResultsEnabled").asBoolean()).isTrue();
                testing = true;
            } else {
                JsonNode config = configs.path("story-node-workbench");
                assertThat(config.path("variant").asText()).isNotBlank();
                assertThat(config.path("nodeKey").asText()).isEqualTo(node.path("key").asText());
            }
        }
        assertThat(testing).isTrue();
    }
}
