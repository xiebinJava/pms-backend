package com.brad.pms.ai.orchestrator;

import com.brad.pms.ai.orchestrator.api.AiOrchestratorRequest;
import com.brad.pms.ai.orchestrator.api.AiOrchestratorResponse;
import com.brad.pms.ai.orchestrator.contract.ResourceNodeContract;
import com.brad.pms.ai.orchestrator.contract.StoryNodeContract;
import com.brad.pms.ai.orchestrator.domain.AiOrchestratorIntent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiOrchestratorResourceContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesProjectTurnsWithNodeVersionAndComponentKey() throws Exception {
        AiOrchestratorRequest.AppendTurnRequest request = objectMapper.readValue("""
                {
                  "message": "填写方案设计测试结果",
                  "contractVersion": "resource-node/v1",
                  "draft": {"contractVersion": "resource-node/v1", "draft": {
                    "facts": [], "inferences": [], "assumptions": [], "unresolvedDecisions": [],
                    "patch": {"testResult": "PASS"}
                  }},
                  "nodeId": 301,
                  "nodeVersion": 6,
                  "componentKey": "solution-design",
                  "contextVersion": 3
                }
                """, AiOrchestratorRequest.AppendTurnRequest.class);

        assertThat(request.nodeVersion()).isEqualTo(6L);
        assertThat(request.componentKey()).isEqualTo("solution-design");
        assertThat(request.nodeId()).isEqualTo(301L);
        assertThat(request.contextVersion()).isEqualTo(3L);
    }

    @Test
    void keepsRequirementTurnsUnchangedWithoutNodeBindings() throws Exception {
        AiOrchestratorRequest.AppendTurnRequest request = objectMapper.readValue("""
                {
                  "message": "请分析并保存",
                  "contractVersion": "requirement-analysis/v1",
                  "draft": {"contractVersion": "requirement-analysis/v1", "draft": {
                    "facts": [], "inferences": [], "assumptions": [], "unresolvedDecisions": [],
                    "patch": {"analysis": "补充后的分析"}
                  }},
                  "nodeId": 375,
                  "contextVersion": 4
                }
                """, AiOrchestratorRequest.AppendTurnRequest.class);

        assertThat(request.nodeVersion()).isNull();
        assertThat(request.componentKey()).isNull();
        assertThat(request.contextVersion()).isEqualTo(4L);
    }

    @Test
    void projectsOnlyDeclaredFieldsForProjectTopicContracts() throws Exception {
        ResourceNodeContract contract = new ResourceNodeContract(
                301L, "solution-design", "方案设计", 6, "solution-design",
                null, null, null,
                List.of("testResult"), List.of("projectId", "nodeId", "workbench"),
                null, null, null, true);

        ObjectNode json = (ObjectNode) objectMapper.valueToTree(contract);
        assertThat(json.get("nodeId").asLong()).isEqualTo(301L);
        assertThat(json.get("writeCapable").asBoolean()).isTrue();
        assertThat(json.get("componentKey").asText()).isEqualTo("solution-design");
        assertThat(json.has("variant")).isFalse();
        assertThat(json.has("allowedActions")).isFalse();
        assertThat(json.has("allowedTransitions")).isFalse();
        assertThat(json.has("missingInputRules")).isFalse();
        assertThat(json.has("requiredFields")).isFalse();
        assertThat(json.has("fieldContracts")).isFalse();
    }

    @Test
    void keepsStoryNodeContractShapeThroughTheResourceNeutralProjection() throws Exception {
        StoryNodeContract story = new StoryNodeContract(
                702L, "development", "开发", 8, "development",
                List.of(new StoryNodeContract.Field(
                        "implementationNote", "实现说明", "string", true, false, false)),
                List.of("implementationNote", "selfTestResult"),
                List.of("ownerId", "status"),
                List.of("development-item.node.field.update"),
                List.of(),
                List.of("必填字段不能为空"),
                true);

        ObjectNode json = (ObjectNode) objectMapper.valueToTree(ResourceNodeContract.from(story));
        assertThat(json.get("variant").asText()).isEqualTo("development");
        assertThat(json.get("requiredFields").get(0).get("key").asText()).isEqualTo("implementationNote");
        assertThat(json.get("allowedActions").get(0).asText())
                .isEqualTo("development-item.node.field.update");
        assertThat(json.has("componentKey")).isFalse();
        assertThat(json.has("fieldContracts")).isFalse();
    }

    @Test
    void planViewSerializesNodeBindingsOnlyForResourceNodePlans() throws Exception {
        AiOrchestratorResponse.PlanView projectPlan = new AiOrchestratorResponse.PlanView(
                31L, 21L, "RESOURCE_NODE_FIELD_UPDATE", "PLAN_READY", "project", 42L,
                301L, 3L, null, null, null, null, null, List.of(), null, null,
                new ResourceNodeContract(301L, "solution-design", "方案设计", 6, "solution-design",
                        null, null, null, List.of("testResult"),
                        List.of("projectId", "nodeId", "workbench"), null, null, null, true),
                List.of(), List.of(), null, null, null, 6L, "solution-design");
        String json = objectMapper.writeValueAsString(projectPlan);
        assertThat(json).contains("\"nodeVersion\":6").contains("\"componentKey\":\"solution-design\"");

        AiOrchestratorResponse.PlanView requirementPlan = new AiOrchestratorResponse.PlanView(
                30L, 20L, "REQUIREMENT_ANALYSIS_SAVE", "PLAN_READY", "requirement", 33L,
                375L, 4L, null, null, null, null, null, List.of(), null, null);
        String requirementJson = objectMapper.writeValueAsString(requirementPlan);
        assertThat(requirementJson).doesNotContain("nodeVersion", "componentKey");
    }

    @Test
    void declaresTheTwoResourceNodeIntentsBesideTheExistingOnes() {
        assertThat(AiOrchestratorIntent.values())
                .containsExactlyInAnyOrder(
                        AiOrchestratorIntent.REQUIREMENT_ANALYSIS_SAVE,
                        AiOrchestratorIntent.STORY_NODE_FIELD_UPDATE,
                        AiOrchestratorIntent.RESOURCE_NODE_FIELD_UPDATE,
                        AiOrchestratorIntent.RESOURCE_NODE_COMPLETE);
    }
}
