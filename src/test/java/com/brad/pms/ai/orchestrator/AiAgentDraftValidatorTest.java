package com.brad.pms.ai.orchestrator;

import com.brad.pms.ai.connector.context.ContextReadOptions;
import com.brad.pms.ai.orchestrator.contract.AgentDraft;
import com.brad.pms.ai.orchestrator.contract.AgentDraftItem;
import com.brad.pms.ai.orchestrator.contract.AiAgentDraftValidator;
import com.brad.pms.ai.orchestrator.contract.DraftValidationResult;
import com.brad.pms.ai.orchestrator.contract.StoryNodeContract;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiAgentDraftValidatorTest {

    private final AiAgentDraftValidator validator = new AiAgentDraftValidator();
    private final AiWorkflowContextDTO context = new AiWorkflowContextDTO(
            "requirement", 33L, 4,
            new AiWorkflowContextDTO.CurrentNode(375L, "analysis", "分析"),
            null,
            List.of("requirement.analysis.save"),
            4,
            Map.of("title", "需求"),
            List.of(new AiWorkflowContextDTO.Relation("project", 7L, "项目", true),
                    new AiWorkflowContextDTO.Relation("story", 99L, "无权故事", false)),
            null);

    @Test
    void acceptsDraftWithReadableSourcesAndNonEmptyPatch() {
        DraftValidationResult result = validator.validate(validDraft(), context);

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void rejectsMissingArraysAndEmptyPatch() {
        AgentDraft.DraftPayload draft = new AgentDraft.DraftPayload(
                null, List.of(), List.of(), List.of(), Map.of());

        DraftValidationResult result = validator.validate(new AgentDraft(
                "requirement-analysis/v1", draft), context);

        assertThat(result.errorCodes()).contains("MISSING_FIELD", "EMPTY_PATCH");
    }

    @Test
    void rejectsUnknownAndUnreadableSources() {
        AgentDraft.DraftPayload draft = new AgentDraft.DraftPayload(
                List.of(new AgentDraftItem("事实", List.of("requirement:999"), "high")),
                List.of(new AgentDraftItem("推断", List.of("story:99"), "medium")),
                List.of(), List.of(), Map.of("analysis", "内容"));

        DraftValidationResult result = validator.validate(new AgentDraft(
                "requirement-analysis/v1", draft), context);

        assertThat(result.errorCodes()).containsOnly("UNKNOWN_SOURCE_REF");
        assertThat(result.errors()).extracting(DraftValidationResult.ValidationError::path)
                .containsExactly("draft.facts[0].sourceRefs[0]", "draft.inferences[0].sourceRefs[0]");
    }

    @Test
    void rejectsProtectedPatchFieldsAndUnsupportedContractVersion() {
        AgentDraft.DraftPayload draft = new AgentDraft.DraftPayload(
                List.of(), List.of(), List.of(), List.of(), Map.of("status", "DONE"));

        DraftValidationResult result = validator.validate(new AgentDraft(
                "requirement-analysis/v0", draft), context);

        assertThat(result.errorCodes()).containsExactlyInAnyOrder(
                "CONTRACT_VERSION_UNSUPPORTED", "PROTECTED_FIELD");
    }

    @Test
    void parsesTheOuterVersionWrapperAndRejectsUnknownFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        String json = """
                {"contractVersion":"requirement-analysis/v1","draft":{
                  "facts":[],"inferences":[],"assumptions":[],"unresolvedDecisions":[],"patch":{"analysis":"x"}
                }}
                """;

        assertThat(AgentDraft.parse(mapper, json).contractVersion())
                .isEqualTo("requirement-analysis/v1");
        assertThatThrownBy(() -> AgentDraft.parse(mapper,
                json.replace("\"patch\":{", "\"unexpected\":true,\"patch\":{")))
                .isInstanceOf(Exception.class);
    }

    @Test
    void validatesStoryDraftAgainstTheFocusedNodeContractAndReferences() {
        var storyContext = new AiWorkflowContextDTO("story", 34L, 8,
                new AiWorkflowContextDTO.CurrentNode(702L, "development", "开发"), null,
                List.of("development-item.node.field.update"), null, Map.of(), List.of(), null);
        var contract = new StoryNodeContract(702L, "development", "开发", 8, "development",
                List.of(new StoryNodeContract.Field("implementationNote", "实现说明", "string", true, false, false)),
                List.of("implementationNote"), List.of("status"),
                List.of("development-item.node.field.update"), List.of(), List.of(), true);
        AgentDraft draft = new AgentDraft("story-workbench/v1", new AgentDraft.DraftPayload(
                List.of(new AgentDraftItem("节点事实", List.of("story-node:702#component:story-node-workbench"), null)),
                List.of(), List.of(), List.of(), Map.of("implementationNote", "已完成")));

        assertThat(validator.validateStory(draft, storyContext, contract).valid()).isTrue();
    }

    @Test
    void rejectsNode定位FieldsAndUnknownStoryFieldsInTheDraftPatch() {
        var storyContext = new AiWorkflowContextDTO("story", 34L, 8,
                new AiWorkflowContextDTO.CurrentNode(702L, "development", "开发"), null,
                List.of(), null, Map.of(), List.of(), null);
        var contract = new StoryNodeContract(702L, "development", "开发", 8, "development",
                List.of(), List.of("implementationNote"), List.of("status"), List.of(), List.of(), List.of(), true);
        AgentDraft draft = new AgentDraft("story-workbench/v1", new AgentDraft.DraftPayload(
                List.of(), List.of(), List.of(), List.of(),
                Map.of("nodeId", 702L, "unknown", "x", "status", "DONE")));

        assertThat(validator.validateStory(draft, storyContext, contract).errorCodes())
                .containsExactlyInAnyOrder("NODE_FIELD_IN_DRAFT", "UNKNOWN_FIELD", "PROTECTED_FIELD");
    }

    @Test
    void rejectsStoryPatchValuesBeforePreviewWhenPolicyValidationFails() {
        var storyContext = new AiWorkflowContextDTO("story", 34L, 8,
                new AiWorkflowContextDTO.CurrentNode(702L, "development", "开发"), null,
                List.of("development-item.node.field.update"), null, Map.of(), List.of(), null);
        var contract = new StoryNodeContract(702L, "development", "开发", 8, "development",
                List.of(), List.of("implementationNote", "mergeStatus"), List.of("status"), List.of(),
                List.of(), List.of(), true);
        AgentDraft draft = new AgentDraft("story-workbench/v1", new AgentDraft.DraftPayload(
                List.of(), List.of(), List.of(), List.of(),
                Map.of("implementationNote", "完成", "mergeStatus", "INVALID")));

        assertThat(validator.validateStory(draft, storyContext, contract).errorCodes())
                .containsExactly("FIELD_VALUE_INVALID");
    }

    private AgentDraft validDraft() {
        return new AgentDraft("requirement-analysis/v1", new AgentDraft.DraftPayload(
                List.of(new AgentDraftItem("事实", List.of("requirement:33"), "high")),
                List.of(new AgentDraftItem("推断", List.of("project:7"), "medium")),
                List.of(new AgentDraftItem("假设", List.of(), "low")),
                List.of(new AgentDraftItem("待决策", List.of(), null)),
                Map.of("analysis", "内容")));
    }
}
