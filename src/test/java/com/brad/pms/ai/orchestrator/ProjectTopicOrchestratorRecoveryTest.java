package com.brad.pms.ai.orchestrator;

import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.connector.context.ContextReadOptions;
import com.brad.pms.ai.orchestrator.adapter.ProjectOrchestratorAdapter;
import com.brad.pms.ai.orchestrator.adapter.ResourceOrchestratorAdapterRegistry;
import com.brad.pms.ai.orchestrator.adapter.TopicOrchestratorAdapter;
import com.brad.pms.ai.orchestrator.api.AiOrchestratorResponse;
import com.brad.pms.ai.orchestrator.connector.AiConnectorPort;
import com.brad.pms.ai.orchestrator.contract.AgentDraft;
import com.brad.pms.ai.orchestrator.contract.AgentDraftItem;
import com.brad.pms.ai.orchestrator.contract.AiAgentDraftValidator;
import com.brad.pms.ai.orchestrator.persistence.AiConversationMapper;
import com.brad.pms.ai.orchestrator.persistence.AiConversationTurnMapper;
import com.brad.pms.ai.orchestrator.persistence.AiOperationEventMapper;
import com.brad.pms.ai.orchestrator.persistence.AiPlanDO;
import com.brad.pms.ai.orchestrator.persistence.AiPlanMapper;
import com.brad.pms.ai.orchestrator.service.AiOrchestratorService;
import com.brad.pms.ai.orchestrator.service.AiOrchestratorVerificationService;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.integration.ai.api.AiOperationPreviewDTO;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.brad.pms.mapper.AiOperationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectTopicOrchestratorRecoveryTest {

    private final AiConversationMapper conversationMapper = mock(AiConversationMapper.class);
    private final AiConversationTurnMapper turnMapper = mock(AiConversationTurnMapper.class);
    private final AiPlanMapper planMapper = mock(AiPlanMapper.class);
    private final AiOperationEventMapper eventMapper = mock(AiOperationEventMapper.class);
    private final AiOperationMapper operationMapper = mock(AiOperationMapper.class);
    private final AiConnectorPort connectorPort = mock(AiConnectorPort.class);
    private final AiOrchestratorVerificationService verificationService = mock(AiOrchestratorVerificationService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final AiAgentDraftValidator validator = new AiAgentDraftValidator();
    private AiOrchestratorService service;

    @BeforeEach
    void setUp() {
        ResourceOrchestratorAdapterRegistry registry = new ResourceOrchestratorAdapterRegistry(List.of(
                new ProjectOrchestratorAdapter(validator), new TopicOrchestratorAdapter(validator)));
        service = new AiOrchestratorService(conversationMapper, turnMapper, planMapper, eventMapper,
                operationMapper, connectorPort, validator, verificationService, objectMapper, registry);
        when(eventMapper.appendNext(any(), any(), any(), any(), any())).thenReturn(1);
    }

    @Test
    void previewsProjectFieldUpdateWithResourceCommandAndProvenanceBinding() throws Exception {
        AiPlanDO plan = projectPlan("RESOURCE_NODE_FIELD_UPDATE", projectDraft(false));
        when(planMapper.selectForUpdate(300L, 7L)).thenReturn(plan);
        when(connectorPort.preview(any(CommandPreviewRequest.class))).thenAnswer(invocation -> {
            CommandPreviewRequest request = invocation.getArgument(0, CommandPreviewRequest.class);
            String digest = request.provenance() == null ? zeroDigest() :
                    String.valueOf(request.provenance().get("draftDigest"));
            return preview("op-project", digest);
        });

        AiOrchestratorResponse.PlanView result = service.preview(7L, 300L);

        assertThat(result.status()).isEqualTo("WAITING_CONFIRMATION");
        assertThat(result.preview().expectedVersions()).containsEntry("nodeVersion", 6);
        assertThat(result.preview().provenanceRef()).isEqualTo("operation:op-project");
        assertThat(result.preview().draftDigest()).matches("sha256:[0-9a-f]{64}");
        var request = org.mockito.Mockito.mockingDetails(connectorPort).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("preview"))
                .findFirst().orElseThrow();
        CommandPreviewRequest sent = (CommandPreviewRequest) request.getArguments()[0];
        assertThat(sent.name()).isEqualTo(CommandName.NODE_FIELD_UPDATE);
        assertThat(sent.arguments()).containsEntry("projectId", 42L)
                .containsEntry("nodeId", 301L)
                .containsEntry("workbench", "solution-design")
                .containsEntry("fields", Map.of("evidence", "通过"));
        assertThat(sent.provenance()).containsKeys("contractVersion", "draftDigest", "fieldSources");
    }

    @Test
    void previewsTopicCompletionWithTheAtomicCompleteCommand() throws Exception {
        AiPlanDO plan = topicPlan("RESOURCE_NODE_COMPLETE", topicDraft(true));
        when(planMapper.selectForUpdate(301L, 7L)).thenReturn(plan);
        when(connectorPort.preview(any(CommandPreviewRequest.class))).thenAnswer(invocation -> {
            CommandPreviewRequest request = invocation.getArgument(0, CommandPreviewRequest.class);
            return preview("op-topic", String.valueOf(request.provenance().get("draftDigest")));
        });

        AiOrchestratorResponse.PlanView result = service.preview(7L, 301L);

        assertThat(result.status()).isEqualTo("WAITING_CONFIRMATION");
        var invocation = org.mockito.Mockito.mockingDetails(connectorPort).getInvocations().stream()
                .filter(value -> value.getMethod().getName().equals("preview"))
                .findFirst().orElseThrow();
        CommandPreviewRequest sent = (CommandPreviewRequest) invocation.getArguments()[0];
        assertThat(sent.name()).isEqualTo(CommandName.DEVELOPMENT_ITEM_NODE_COMPLETE_WITH_DATA);
        assertThat(sent.arguments()).containsEntry("itemType", "TOPIC")
                .containsEntry("itemId", 43L)
                .containsEntry("nodeId", 511L)
                .containsEntry("version", 4L)
                .containsEntry("fieldValues", Map.of("result", "通过"));
    }

    @Test
    void keepsPlanReadyWhenPmsDoesNotReturnTheVersionSnapshot() throws Exception {
        AiPlanDO plan = projectPlan("RESOURCE_NODE_FIELD_UPDATE", projectDraft(false));
        when(planMapper.selectForUpdate(300L, 7L)).thenReturn(plan);
        when(connectorPort.preview(any(CommandPreviewRequest.class))).thenReturn(
                new AiOperationPreviewDTO("op-project", "node.field.update", Instant.now(), "3",
                        Map.of(), List.of(), List.of(), List.of(), List.of(),
                        "operation:op-project", zeroDigest()));

        assertThatThrownBy(() -> service.preview(7L, 300L))
                .hasMessageContaining("expectedVersions");
        assertThat(plan.getStatus()).isEqualTo("PLAN_READY");
    }

    @Test
    void recoversAProjectPlanFromAPersistedSuccessfulOperation() throws Exception {
        AiPlanDO plan = projectPlan("RESOURCE_NODE_FIELD_UPDATE", projectDraft(false));
        plan.setStatus("EXECUTING");
        plan.setPreviewOperationId("op-project");
        plan.setIdempotencyKey("idem-project");
        when(planMapper.selectForUpdate(7_000L, 7L)).thenReturn(plan);
        AiOperationDO operation = new AiOperationDO();
        operation.setId("op-project");
        operation.setStatus("SUCCEEDED");
        operation.setIdempotencyKey("idem-project");
        operation.setResultJson(objectMapper.writeValueAsString(new CommandResult(
                "op-project", "SUCCEEDED", "已保存", Map.of(), List.of())));
        operation.setPreviewJson(null);
        when(operationMapper.selectById("op-project")).thenReturn(operation);
        when(verificationService.verify(any(), any(), any())).thenReturn(
                new AiOrchestratorResponse.VerificationView(true, true, true, true,
                        "SUCCEEDED", "回读成功"));

        AiOrchestratorResponse.PlanView result = service.confirm(7L, 7_000L, "idem-project");

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(plan.getStatus()).isEqualTo("SUCCEEDED");
        org.mockito.Mockito.verify(connectorPort, org.mockito.Mockito.never()).execute(any());
    }

    private AiPlanDO projectPlan(String intent, AgentDraft draft) throws Exception {
        AiPlanDO plan = basePlan(300L, "project", 42L, 301L, 6L, "solution-design", 3L, intent, draft,
                projectContext());
        return plan;
    }

    private AiPlanDO topicPlan(String intent, AgentDraft draft) throws Exception {
        return basePlan(301L, "topic", 43L, 511L, 4L, "topic-design-review", 4L, intent, draft,
                topicContext());
    }

    private AiPlanDO basePlan(Long id, String resourceType, Long resourceId, Long nodeId, Long nodeVersion,
                              String componentKey, Long contextVersion, String intent, AgentDraft draft,
                              AiWorkflowContextDTO context) throws Exception {
        AiPlanDO plan = new AiPlanDO();
        plan.setId(id);
        plan.setConversationId(100L);
        plan.setTurnId(200L);
        plan.setIntent(intent);
        plan.setResourceType(resourceType);
        plan.setResourceId(resourceId);
        plan.setNodeId(nodeId);
        plan.setNodeVersion(nodeVersion);
        plan.setComponentKey(componentKey);
        plan.setContextVersion(contextVersion);
        plan.setContextSnapshotJson(objectMapper.writeValueAsString(context));
        plan.setDraftJson(objectMapper.writeValueAsString(draft));
        plan.setStatus("PLAN_READY");
        return plan;
    }

    private AgentDraft projectDraft(boolean completion) {
        return draft("project-node:301#component:solution-design", Map.of("evidence", "通过"), completion);
    }

    private AgentDraft topicDraft(boolean completion) {
        return draft("topic-node:511#component:topic-design-review", Map.of("result", "通过"), completion);
    }

    private AgentDraft draft(String sourceRef, Map<String, Object> patch, boolean completion) {
        return new AgentDraft("resource-node/v1", new AgentDraft.DraftPayload(
                List.of(new AgentDraftItem("节点事实", List.of(sourceRef), "high")),
                List.of(), List.of(), List.of(), patch, completion,
                Map.of("evidence", Map.of("sourceType", "pms_context", "sourceRefs", List.of(sourceRef),
                        "confidence", "high"),
                        "result", Map.of("sourceType", "pms_context", "sourceRefs", List.of(sourceRef),
                                "confidence", "high"))));
    }

    private AiOperationPreviewDTO preview(String operationId, String draftDigest) {
        return new AiOperationPreviewDTO(operationId, "node.field.update", Instant.now().plusSeconds(600), "3",
                Map.of("nodeVersion", 6, "entityVersion", 3), List.of(), List.of(), List.of(), List.of(),
                "operation:" + operationId, draftDigest);
    }

    private AiWorkflowContextDTO projectContext() {
        Map<String, Object> contract = Map.of("command", "node.field.update", "nodeVersion", 6,
                "componentKey", "solution-design", "writableFields", List.of("evidence"),
                "protectedFields", List.of("projectId", "nodeId"));
        AiWorkflowContextDTO.Component component = new AiWorkflowContextDTO.Component(
                "solution-design", "方案", List.of(), List.of(), null, objectMapper.valueToTree(Map.of()),
                "LOADED", Map.of("content", 4), contract);
        AiWorkflowContextDTO.Node node = new AiWorkflowContextDTO.Node(301L, "solution", "方案", 0, 1,
                List.of(component), List.of(), Map.of(), 6, null, null, null, null,
                null, null, null, "LOADED", null, null);
        return new AiWorkflowContextDTO("project", 42L, 3,
                new AiWorkflowContextDTO.CurrentNode(301L, "solution", "方案"),
                new AiWorkflowContextDTO.Workflow(1L, 1, List.of(node)), List.of("node.field.update"), 3,
                Map.of(), List.of(), null);
    }

    private AiWorkflowContextDTO topicContext() {
        Map<String, Object> contract = Map.of("command", "development-item.node.field.update", "version", 4,
                "componentKey", "topic-design-review", "writableFields", List.of("result"),
                "protectedFields", List.of("status"));
        AiWorkflowContextDTO.Component component = new AiWorkflowContextDTO.Component(
                "topic-design-review", "设计评审", List.of(), List.of(), null, objectMapper.valueToTree(Map.of()),
                "LOADED", Map.of("content", 2), contract);
        AiWorkflowContextDTO.Node node = new AiWorkflowContextDTO.Node(511L, "design", "设计", 0, 1,
                List.of(component), List.of(), Map.of(), 4, null, null, null, null,
                null, null, null, "LOADED", null, null);
        return new AiWorkflowContextDTO("topic", 43L, 4,
                new AiWorkflowContextDTO.CurrentNode(511L, "design", "设计"),
                new AiWorkflowContextDTO.Workflow(1L, 1, List.of(node)),
                List.of("development-item.node.field.update"), null, Map.of(), List.of(), null);
    }

    private String zeroDigest() {
        return "sha256:0000000000000000000000000000000000000000000000000000000000000000";
    }
}
