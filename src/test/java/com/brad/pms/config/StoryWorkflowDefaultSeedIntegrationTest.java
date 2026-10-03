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
