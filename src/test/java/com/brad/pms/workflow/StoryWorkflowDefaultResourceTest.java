package com.brad.pms.workflow;

import com.brad.pms.config.WorkflowDefaultTemplateFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StoryWorkflowDefaultResourceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void storyDefaultResourceDeclaresEveryNodeWorkbench() throws Exception {
        WorkflowDefaultTemplateFile file;
        try (var input = new ClassPathResource("workflow-defaults/story-management.json").getInputStream()) {
            file = mapper.readValue(input, WorkflowDefaultTemplateFile.class);
        }

        assertThat(file.processTypeCode()).isEqualTo("story-management");
        assertThat(file.definition().sourceTopicNodeKey()).isEqualTo("custom-node-3");

        WorkflowTemplateDefinition validated = WorkflowTemplateDefinitionValidator
                .validateForPublish("story-management", file.definition());
        assertThat(validated.nodes()).hasSize(7);

        Set<String> variants = new HashSet<>();
        boolean testingConfigured = false;
        for (WorkflowNodeDefinition node : validated.nodes()) {
            if (String.valueOf(node.name()).contains("测试")) {
                assertThat(node.runtimeComponents()).contains(WorkflowComponentKey.STORY_TESTING);
                JsonNode testingConfig = node.componentConfigs().get(WorkflowComponentKey.STORY_TESTING);
                assertThat(testingConfig.path("testingResultsEnabled").asBoolean()).isTrue();
                testingConfigured = true;
            } else {
                assertThat(node.runtimeComponents()).contains(WorkflowComponentKey.STORY_NODE_WORKBENCH);
                JsonNode config = node.componentConfigs().get(WorkflowComponentKey.STORY_NODE_WORKBENCH);
                assertThat(config.path("nodeKey").asText()).isEqualTo(node.key());
                assertThat(config.path("activities")).isNotEmpty();
                variants.add(config.path("variant").asText());
            }
        }
        assertThat(testingConfigured).isTrue();
        assertThat(variants).containsExactlyInAnyOrder(
                "writing", "iteration", "development", "acceptance", "release", "launch");
    }
}
