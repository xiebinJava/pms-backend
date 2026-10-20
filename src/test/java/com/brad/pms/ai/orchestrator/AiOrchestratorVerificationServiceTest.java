package com.brad.pms.ai.orchestrator;

import com.brad.pms.ai.orchestrator.connector.AiConnectorPort;
import com.brad.pms.ai.orchestrator.contract.AgentDraft;
import com.brad.pms.ai.orchestrator.persistence.AiPlanDO;
import com.brad.pms.ai.orchestrator.service.AiOrchestratorVerificationService;
import com.brad.pms.integration.ai.api.AiOperationResultDTO;
import com.brad.pms.integration.ai.api.AiResourceDetailDTO;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.brad.pms.ai.connector.context.ContextReadOptions;

class AiOrchestratorVerificationServiceTest {

    private final AiConnectorPort connectorPort = mock(AiConnectorPort.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final AiOrchestratorVerificationService service =
            new AiOrchestratorVerificationService(connectorPort, objectMapper);

    @Test
    void doesNotTreatOperationDataAsProofThatTheStoryPatchWasPersisted() throws Exception {
        AiPlanDO plan = storyPlan(storyContext(Map.of()), 8L);
        AgentDraft draft = storyDraft(Map.of("implementationNote", "已完成", "selfTestResult", "通过"));
        when(connectorPort.readResource("story", 34L)).thenReturn(resource());
        when(connectorPort.readContext(any(), any(), any())).thenReturn(storyContext(Map.of()));

        var result = service.verify(plan, draft, new AiOperationResultDTO(
                "op-1", "SUCCEEDED", "已保存", draft.draft().patch(), List.of()));

        assertThat(result.status()).isEqualTo("WRITE_RESULT_UNKNOWN");
        assertThat(result.resourceReadback()).isTrue();
        assertThat(result.contextReadback()).isTrue();
        assertThat(result.patchApplied()).isFalse();
    }

    @Test
    void verifiesThePatchFromTheSameStoryNodeWorkbenchComponent() throws Exception {
        AiPlanDO plan = storyPlan(storyContext(Map.of()), 8L);
        AgentDraft draft = storyDraft(Map.of("implementationNote", "已完成", "selfTestResult", "通过"));
        when(connectorPort.readResource("story", 34L)).thenReturn(resource());
        when(connectorPort.readContext(any(), any(), any())).thenReturn(storyContext(draft.draft().patch()));

        var result = service.verify(plan, draft, new AiOperationResultDTO(
                "op-1", "SUCCEEDED", "已保存", Map.of(), List.of()));

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.resourceReadback()).isTrue();
        assertThat(result.contextReadback()).isTrue();
        assertThat(result.protectedFieldsUnchanged()).isTrue();
        assertThat(result.patchApplied()).isTrue();
        verify(connectorPort).readContext("story", 34L, new ContextReadOptions(
                ContextReadOptions.VIEW_FOCUSED, 701L, "story-node-workbench"));
    }

    @Test
    void rejectsReadbackFromAnotherWorkflowNode() throws Exception {
        AiPlanDO plan = storyPlan(storyContext(Map.of()), 8L);
        AgentDraft draft = storyDraft(Map.of("implementationNote", "已完成", "selfTestResult", "通过"));
        when(connectorPort.readResource("story", 34L)).thenReturn(resource());
        when(connectorPort.readContext(any(), any(), any())).thenReturn(storyContext(702L,
                Map.of("implementationNote", "已完成", "selfTestResult", "通过")));

        var result = service.verify(plan, draft, new AiOperationResultDTO(
                "op-1", "SUCCEEDED", "已保存", Map.of(), List.of()));

        assertThat(result.status()).isEqualTo("WRITE_RESULT_UNKNOWN");
        assertThat(result.patchApplied()).isFalse();
    }

    @Test
    void verifiesRequirementAnalysisFromItsReadbackComponent() throws Exception {
        AiWorkflowContextDTO before = requirementContext(Map.of("analysis", "旧"), 4, 4);
        AiWorkflowContextDTO after = requirementContext(Map.of("analysis", "新"), 5, 5);
        AiPlanDO plan = new AiPlanDO();
        plan.setResourceType("requirement");
        plan.setResourceId(33L);
        plan.setNodeId(375L);
        plan.setContextVersion(4L);
        plan.setContextSnapshotJson(objectMapper.writeValueAsString(before));
        AgentDraft draft = new AgentDraft("requirement-analysis/v1", new AgentDraft.DraftPayload(
                List.of(), List.of(), List.of(), List.of(), Map.of("analysis", "新")));
        when(connectorPort.readResource("requirement", 33L)).thenReturn(
                new AiResourceDetailDTO("requirement", 33L, 5, Map.of("title", "需求", "status", "OPEN")));
        when(connectorPort.readContext(any(), any(), any())).thenReturn(after);

        var result = service.verify(plan, draft, new AiOperationResultDTO(
                "op-1", "SUCCEEDED", "已保存", Map.of(), List.of()));

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.patchApplied()).isTrue();
    }

    @Test
    void rejectsAStoryReadbackWhoseTopLevelVersionDiffersFromTheNodeVersion() throws Exception {
        AiWorkflowContextDTO before = storyContext(Map.of());
        AiWorkflowContextDTO baseAfter = storyContext(Map.of(
                "implementationNote", "已完成", "selfTestResult", "通过"));
        AiWorkflowContextDTO after = new AiWorkflowContextDTO(
                baseAfter.resourceType(), baseAfter.resourceId(), 9, baseAfter.currentNode(),
                baseAfter.workflow(), baseAfter.allowedActions(), baseAfter.entityVersion(),
                baseAfter.resourceDetails(), baseAfter.relations(), baseAfter.meta());
        AiPlanDO plan = storyPlan(before, 8L);
        AgentDraft draft = storyDraft(Map.of("implementationNote", "已完成", "selfTestResult", "通过"));
        when(connectorPort.readResource("story", 34L)).thenReturn(resource());
        when(connectorPort.readContext(any(), any(), any())).thenReturn(after);

        var result = service.verify(plan, draft, new AiOperationResultDTO(
                "op-1", "SUCCEEDED", "已保存", Map.of(), List.of()));

        assertThat(result.status()).isEqualTo("WRITE_RESULT_UNKNOWN");
    }

    @Test
    void rejectsAStoryReadbackThatChangesAProtectedComponentField() throws Exception {
        AiPlanDO plan = storyPlan(storyContext(Map.of("status", "OPEN")), 8L);
        AgentDraft draft = storyDraft(Map.of("implementationNote", "已完成", "selfTestResult", "通过"));
        when(connectorPort.readResource("story", 34L)).thenReturn(resource());
        when(connectorPort.readContext(any(), any(), any())).thenReturn(storyContext(Map.of(
                "status", "CLOSED", "implementationNote", "已完成", "selfTestResult", "通过")));

        var result = service.verify(plan, draft, new AiOperationResultDTO(
                "op-1", "SUCCEEDED", "已保存", Map.of(), List.of()));

        assertThat(result.status()).isEqualTo("WRITE_RESULT_UNKNOWN");
        assertThat(result.protectedFieldsUnchanged()).isFalse();
    }

    @Test
    void rejectsAStoryReadbackThatChangesAProtectedNodeField() throws Exception {
        AiWorkflowContextDTO before = storyContext(Map.of());
        AiWorkflowContextDTO.Node beforeNode = before.workflow().nodes().get(0);
        AiWorkflowContextDTO.Node changedNode = new AiWorkflowContextDTO.Node(
                beforeNode.id(), beforeNode.key(), beforeNode.label(), beforeNode.position(), beforeNode.status(),
                beforeNode.components(), beforeNode.fields(), beforeNode.fieldValues(), beforeNode.version(),
                beforeNode.description(), beforeNode.deliverable(), 99L, beforeNode.ownerName(),
                beforeNode.startDate(), beforeNode.endDate(), beforeNode.fieldValueVersions(),
                beforeNode.fieldValuesState(), beforeNode.permissions(), beforeNode.taskSummary());
        AiWorkflowContextDTO after = new AiWorkflowContextDTO(
                before.resourceType(), before.resourceId(), before.version(), before.currentNode(),
                new AiWorkflowContextDTO.Workflow(before.workflow().templateVersionId(),
                        before.workflow().templateVersionNo(), List.of(changedNode)),
                before.allowedActions(), before.entityVersion(), before.resourceDetails(), before.relations(), before.meta());
        AiPlanDO plan = storyPlan(before, 8L);
        AgentDraft draft = storyDraft(Map.of("implementationNote", "已完成", "selfTestResult", "通过"));
        when(connectorPort.readResource("story", 34L)).thenReturn(resource());
        when(connectorPort.readContext(any(), any(), any())).thenReturn(after);

        var result = service.verify(plan, draft, new AiOperationResultDTO(
                "op-1", "SUCCEEDED", "已保存", Map.of(), List.of()));

        assertThat(result.status()).isEqualTo("WRITE_RESULT_UNKNOWN");
        assertThat(result.protectedFieldsUnchanged()).isFalse();
    }

    @Test
    void verifiesAProjectPatchOnlyFromTheSelectedNodeComponent() throws Exception {
        AiWorkflowContextDTO before = projectContext(Map.of(), Map.of("status", "OPEN"), 6);
        AiWorkflowContextDTO after = projectContext(Map.of("evidence", "已完成"),
                Map.of("status", "OPEN"), 7);
        AiPlanDO plan = projectPlan(before, 6L, "solution-design");
        AgentDraft draft = resourceDraft(Map.of("evidence", "已完成"));
        when(connectorPort.readResource("project", 42L)).thenReturn(
                new AiResourceDetailDTO("project", 42L, 4, Map.of("title", "项目", "status", "OPEN")));
        when(connectorPort.readContext(any(), any(), any())).thenReturn(after);

        var result = service.verify(plan, draft, new AiOperationResultDTO(
                "op-project", "SUCCEEDED", "已保存", Map.of(), List.of()));

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        verify(connectorPort).readContext("project", 42L, new ContextReadOptions(
                ContextReadOptions.VIEW_FOCUSED, 301L, "solution-design"));
    }

    @Test
    void rejectsAProjectPatchReadbackFromAnotherComponent() throws Exception {
        AiWorkflowContextDTO before = projectContext(Map.of(), Map.of("status", "OPEN"), 6);
        AiWorkflowContextDTO after = projectContext(Map.of(), Map.of("status", "OPEN"), 7,
                Map.of("evidence", "已完成"));
        AiPlanDO plan = projectPlan(before, 6L, "solution-design");
        AgentDraft draft = resourceDraft(Map.of("evidence", "已完成"));
        when(connectorPort.readResource("project", 42L)).thenReturn(
                new AiResourceDetailDTO("project", 42L, 4, Map.of("title", "项目", "status", "OPEN")));
        when(connectorPort.readContext(any(), any(), any())).thenReturn(after);

        var result = service.verify(plan, draft, new AiOperationResultDTO(
                "op-project", "SUCCEEDED", "已保存", Map.of(), List.of()));

        assertThat(result.status()).isEqualTo("WRITE_RESULT_UNKNOWN");
        assertThat(result.patchApplied()).isFalse();
    }

    @Test
    void rejectsAProjectReadbackFromAStaleNodeVersion() throws Exception {
        AiWorkflowContextDTO before = projectContext(Map.of(), Map.of("status", "OPEN"), 6);
        AiWorkflowContextDTO after = projectContext(
                Map.of("evidence", "已完成"), Map.of("status", "OPEN"), 5);
        AiPlanDO plan = projectPlan(before, 6L, "solution-design");
        AgentDraft draft = resourceDraft(Map.of("evidence", "已完成"));
        when(connectorPort.readResource("project", 42L)).thenReturn(
                new AiResourceDetailDTO("project", 42L, 4, Map.of("title", "项目", "status", "OPEN")));
        when(connectorPort.readContext(any(), any(), any())).thenReturn(after);

        var result = service.verify(plan, draft, new AiOperationResultDTO(
                "op-project", "SUCCEEDED", "已保存", Map.of(), List.of()));

        assertThat(result.status()).isEqualTo("WRITE_RESULT_UNKNOWN");
        assertThat(result.patchApplied()).isFalse();
    }

    private AiPlanDO storyPlan(AiWorkflowContextDTO context, long version) throws Exception {
        AiPlanDO plan = new AiPlanDO();
        plan.setResourceType("story");
        plan.setResourceId(34L);
        plan.setNodeId(701L);
        plan.setContextVersion(version);
        plan.setContextSnapshotJson(objectMapper.writeValueAsString(context));
        return plan;
    }

    private AiPlanDO projectPlan(AiWorkflowContextDTO context, long nodeVersion, String componentKey)
            throws Exception {
        AiPlanDO plan = new AiPlanDO();
        plan.setResourceType("project");
        plan.setResourceId(42L);
        plan.setNodeId(301L);
        plan.setNodeVersion(nodeVersion);
        plan.setComponentKey(componentKey);
        plan.setContextVersion(3L);
        plan.setContextSnapshotJson(objectMapper.writeValueAsString(context));
        return plan;
    }

    private AiResourceDetailDTO resource() {
        return new AiResourceDetailDTO("story", 34L, 8, Map.of("title", "故事", "status", "OPEN"));
    }

    private AiWorkflowContextDTO storyContext(Map<String, Object> content) {
        return storyContext(701L, content);
    }

    private AiWorkflowContextDTO storyContext(long nodeId, Map<String, Object> content) {
        var component = new AiWorkflowContextDTO.Component(
                "story-node-workbench", "开发工作台", List.of(), List.of(), null,
                objectMapper.valueToTree(content), "LOADED", Map.of("node", 8),
                Map.of("variant", "development", "version", 8, "protectedFields", List.of("status")));
        var node = new AiWorkflowContextDTO.Node(nodeId, "development", "开发", 0, 1,
                List.of(component), List.of(), Map.of(), 8, null, null, null, null,
                null, null, null, null, null, null);
        return new AiWorkflowContextDTO("story", 34L, 8,
                new AiWorkflowContextDTO.CurrentNode(nodeId, "development", "开发"),
                new AiWorkflowContextDTO.Workflow(1L, 1, List.of(node)),
                List.of("development-item.node.field.update"), 8,
                Map.of("title", "故事", "status", "OPEN"), List.of(), null);
    }

    private AgentDraft storyDraft(Map<String, Object> patch) {
        return new AgentDraft("story-workbench/v1", new AgentDraft.DraftPayload(
                List.of(), List.of(), List.of(), List.of(), patch));
    }

    private AgentDraft resourceDraft(Map<String, Object> patch) {
        return new AgentDraft("resource-node/v1", new AgentDraft.DraftPayload(
                List.of(), List.of(), List.of(), List.of(), patch, false,
                Map.of("evidence", Map.of("sourceType", "pms_context", "sourceRefs", List.of(
                        "project-node:301#component:solution-design"), "confidence", "high"))));
    }

    private AiWorkflowContextDTO requirementContext(Map<String, Object> content,
                                                    int contextVersion, int nodeVersion) {
        var component = new AiWorkflowContextDTO.Component(
                "requirement-receiving-analysis", "需求分析", List.of(), List.of(), null,
                objectMapper.valueToTree(content), "LOADED", Map.of("node", nodeVersion), null);
        var node = new AiWorkflowContextDTO.Node(375L, "analysis", "分析", 0, 1,
                List.of(component), List.of(), Map.of(), nodeVersion, null, null, null, null,
                null, null, null, null, null, null);
        return new AiWorkflowContextDTO("requirement", 33L, contextVersion,
                new AiWorkflowContextDTO.CurrentNode(375L, "analysis", "分析"),
                new AiWorkflowContextDTO.Workflow(1L, 1, List.of(node)),
                List.of("requirement.analysis.save"), 5,
                Map.of("title", "需求", "status", "OPEN"), List.of(), null);
    }

    private AiWorkflowContextDTO projectContext(Map<String, Object> selectedContent,
                                                Map<String, Object> resourceDetails,
                                                int nodeVersion) {
        return projectContext(selectedContent, resourceDetails, nodeVersion, Map.of());
    }

    private AiWorkflowContextDTO projectContext(Map<String, Object> selectedContent,
                                                Map<String, Object> resourceDetails,
                                                int nodeVersion,
                                                Map<String, Object> otherContent) {
        var selected = new AiWorkflowContextDTO.Component(
                "solution-design", "方案", List.of(), List.of(), null,
                objectMapper.valueToTree(selectedContent), "LOADED", Map.of("content", nodeVersion),
                Map.of("nodeVersion", 6, "componentKey", "solution-design",
                        "writableFields", List.of("evidence"), "protectedFields", List.of("status")));
        var other = new AiWorkflowContextDTO.Component(
                "other", "其他", List.of(), List.of(), null,
                objectMapper.valueToTree(otherContent), "LOADED", Map.of("content", nodeVersion),
                Map.of("nodeVersion", 6, "componentKey", "other",
                        "writableFields", List.of("evidence"), "protectedFields", List.of("status")));
        var node = new AiWorkflowContextDTO.Node(301L, "solution", "方案", 0, 1,
                List.of(selected, other), List.of(), Map.of(), nodeVersion, null, null, null, null,
                null, null, null, "LOADED", null, null);
        return new AiWorkflowContextDTO("project", 42L, 3,
                new AiWorkflowContextDTO.CurrentNode(301L, "solution", "方案"),
                new AiWorkflowContextDTO.Workflow(1L, 1, List.of(node)),
                List.of("node.field.update"), 4, resourceDetails, List.of(), null);
    }
}
