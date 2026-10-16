package com.brad.pms.ai.orchestrator;

import com.brad.pms.ai.orchestrator.contract.StoryNodeContractResolver;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StoryNodeContractResolverTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final StoryNodeContractResolver resolver = new StoryNodeContractResolver();

    @Test
    void resolvesFocusedStoryNodeContractAndCurrentMissingFields() {
        StoryNodeContractResolver.Resolution result = resolver.resolve(context(Map.of()));

        assertThat(result.contract().nodeId()).isEqualTo(702L);
        assertThat(result.contract().variant()).isEqualTo("development");
        assertThat(result.contract().writableFields())
                .containsExactly("implementationNote", "selfTestResult", "testCases");
        assertThat(result.contract().requiredFields()).extracting("key")
                .containsExactly("implementationNote", "selfTestResult");
        assertThat(result.contract().allowedActions())
                .containsExactly("development-item.node.field.update", "development-item.node.complete-with-data");
        assertThat(result.missingFields()).containsExactly("implementationNote", "selfTestResult");
    }

    @Test
    void computesMissingFieldsAfterApplyingAnIncrementalPatch() {
        var focused = context(Map.of("implementationNote", "已完成"));
        var contract = resolver.resolve(focused).contract();

        assertThat(resolver.missingFields(focused, contract, Map.of("selfTestResult", "通过"))).isEmpty();
    }

    private AiWorkflowContextDTO context(Map<String, Object> content) {
        var component = new AiWorkflowContextDTO.Component(
                "story-node-workbench", "开发工作台", List.of(), List.of(),
                mapper.valueToTree(Map.of("variant", "development")), mapper.valueToTree(content), "LOADED",
                Map.of("node", 8), Map.of(
                "command", "development-item.node.field.update",
                "completionCommand", "development-item.node.complete-with-data",
                "completionSupported", true,
                "completionRequiredMetadata", List.of("ownerId", "startDate", "endDate"),
                "variant", "development",
                "writableFields", List.of("implementationNote", "selfTestResult", "testCases"),
                "protectedFields", List.of("status", "ownerId"),
                "fieldContracts", List.of(
                        Map.of("key", "implementationNote", "type", "string", "required", true,
                                "nullable", false, "collection", false),
                        Map.of("key", "selfTestResult", "type", "string", "required", true,
                                "nullable", false, "collection", false),
                        Map.of("key", "testCases", "type", "array", "required", false,
                                "nullable", true, "collection", true))));
        var node = new AiWorkflowContextDTO.Node(702L, "development", "开发", 0, 1,
                List.of(component), List.of(), Map.of(), 8, null, null, null, null,
                null, null, null, "LOADED", null, null);
        return new AiWorkflowContextDTO("story", 34L, 8,
                new AiWorkflowContextDTO.CurrentNode(702L, "development", "开发"),
                new AiWorkflowContextDTO.Workflow(1L, 1, List.of(node)),
                List.of("development-item.node.field.update"), null, Map.of(), List.of(), null);
    }
}
