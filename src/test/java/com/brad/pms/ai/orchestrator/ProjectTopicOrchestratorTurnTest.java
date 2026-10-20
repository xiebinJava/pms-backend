package com.brad.pms.ai.orchestrator;

import com.brad.pms.ai.connector.context.ContextReadOptions;
import com.brad.pms.ai.orchestrator.adapter.ProjectOrchestratorAdapter;
import com.brad.pms.ai.orchestrator.adapter.ResourceOrchestratorAdapterRegistry;
import com.brad.pms.ai.orchestrator.adapter.TopicOrchestratorAdapter;
import com.brad.pms.ai.orchestrator.api.AiOrchestratorRequest;
import com.brad.pms.ai.orchestrator.api.AiOrchestratorResponse;
import com.brad.pms.ai.orchestrator.connector.AiConnectorPort;
import com.brad.pms.ai.orchestrator.contract.AgentDraft;
import com.brad.pms.ai.orchestrator.contract.AgentDraftItem;
import com.brad.pms.ai.orchestrator.contract.AiAgentDraftValidator;
import com.brad.pms.ai.orchestrator.persistence.AiConversationDO;
import com.brad.pms.ai.orchestrator.persistence.AiConversationMapper;
import com.brad.pms.ai.orchestrator.persistence.AiConversationTurnDO;
import com.brad.pms.ai.orchestrator.persistence.AiConversationTurnMapper;
import com.brad.pms.ai.orchestrator.persistence.AiOperationEventMapper;
import com.brad.pms.ai.orchestrator.persistence.AiPlanDO;
import com.brad.pms.ai.orchestrator.persistence.AiPlanMapper;
import com.brad.pms.ai.orchestrator.service.AiOrchestratorService;
import com.brad.pms.ai.orchestrator.service.AiOrchestratorVerificationService;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectTopicOrchestratorTurnTest {

    private final AiConversationMapper conversationMapper = mock(AiConversationMapper.class);
    private final AiConversationTurnMapper turnMapper = mock(AiConversationTurnMapper.class);
    private final AiPlanMapper planMapper = mock(AiPlanMapper.class);
    private final AiOperationEventMapper eventMapper = mock(AiOperationEventMapper.class);
    private final com.brad.pms.mapper.AiOperationMapper operationMapper = mock(com.brad.pms.mapper.AiOperationMapper.class);
    private final AiConnectorPort connectorPort = mock(AiConnectorPort.class);
    private final AiOrchestratorVerificationService verificationService = mock(AiOrchestratorVerificationService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final List<AiPlanDO> storedPlans = new ArrayList<>();
    private AiConversationDO storedConversation;
    private AiOrchestratorService service;

    @BeforeEach
    void setUp() {
        AiAgentDraftValidator validator = new AiAgentDraftValidator();
        ResourceOrchestratorAdapterRegistry registry = new ResourceOrchestratorAdapterRegistry(List.of(
                new ProjectOrchestratorAdapter(validator), new TopicOrchestratorAdapter(validator)));
        service = new AiOrchestratorService(
                conversationMapper, turnMapper, planMapper, eventMapper, operationMapper,
                connectorPort, validator, verificationService, objectMapper, registry);

        when(conversationMapper.selectByIdAndUserId(anyLong(), eq(7L)))
                .thenAnswer(invocation -> storedConversation);
        doAnswer(invocation -> {
            storedConversation = invocation.getArgument(0, AiConversationDO.class);
            storedConversation.setId(100L);
            return 1;
        }).when(conversationMapper).insert(any(AiConversationDO.class));
        doAnswer(invocation -> {
            invocation.getArgument(0, AiConversationTurnDO.class).setId(200L);
            return 1;
        }).when(turnMapper).insert(any(AiConversationTurnDO.class));
        AtomicLong planIds = new AtomicLong(300L);
        doAnswer(invocation -> {
            AiPlanDO plan = invocation.getArgument(0, AiPlanDO.class);
            plan.setId(planIds.getAndIncrement());
            storedPlans.add(plan);
            return 1;
        }).when(planMapper).insert(any(AiPlanDO.class));
        when(planMapper.selectLatestByConversationUser(anyLong(), eq(7L)))
                .thenAnswer(invocation -> storedPlans.isEmpty() ? null : storedPlans.get(storedPlans.size() - 1));
        when(eventMapper.appendNext(any(), any(), any(), any(), any())).thenReturn(1);
    }

    @Test
    void createsProjectConversationAndPlansAValidFieldUpdate() {
        AiWorkflowContextDTO context = projectContext(List.of("evidence"));
        when(connectorPort.readContext(eq("project"), eq(42L), any(ContextReadOptions.class))).thenReturn(context);

        AiOrchestratorResponse.ConversationView conversation = service.createConversation(7L,
                new AiOrchestratorRequest.CreateConversationCommand("project", 42L));
        AiOrchestratorResponse.PlanView result = service.appendTurn(7L, conversation.conversationId(),
                command("project", projectDraft(Map.of("evidence", "通过"), false), 301L, 3L, 6L,
                        "solution-design"));

        assertThat(conversation.resourceType()).isEqualTo("project");
        assertThat(result.status()).isEqualTo("PLAN_READY");
        assertThat(result.intent()).isEqualTo("RESOURCE_NODE_FIELD_UPDATE");
        assertThat(result.nodeVersion()).isEqualTo(6L);
        assertThat(result.componentKey()).isEqualTo("solution-design");
        assertThat(result.nodeContract().label()).isEqualTo("方案");
        assertThat(result.missingFields()).isEmpty();
        verify(eventMapper, org.mockito.Mockito.atLeastOnce())
                .appendNext(any(), any(), any(), eq("PLAN_CREATED"), any());
    }

    @Test
    void plansTopicCompletionWithResourceNodeIntent() {
        AiWorkflowContextDTO context = topicContext(List.of("result"));
        when(connectorPort.readContext(eq("topic"), eq(43L), any(ContextReadOptions.class))).thenReturn(context);

        AiOrchestratorResponse.ConversationView conversation = service.createConversation(7L,
                new AiOrchestratorRequest.CreateConversationCommand("topic", 43L));
        AiOrchestratorResponse.PlanView result = service.appendTurn(7L, conversation.conversationId(),
                command("topic", resourceDraft("topic", Map.of("result", "通过"), true), 511L, null, 4L,
                        "topic-design-review"));

        assertThat(result.status()).isEqualTo("PLAN_READY");
        assertThat(result.intent()).isEqualTo("RESOURCE_NODE_COMPLETE");
        assertThat(result.resourceType()).isEqualTo("topic");
    }

    @Test
    void returnsNeedsInputForBlankRequiredField() {
        AiWorkflowContextDTO context = projectContext(List.of("evidence"));
        when(connectorPort.readContext(eq("project"), eq(42L), any(ContextReadOptions.class))).thenReturn(context);
        AiOrchestratorResponse.ConversationView conversation = createProjectConversation();

        AiOrchestratorResponse.PlanView result = service.appendTurn(7L, conversation.conversationId(),
                command("project", projectDraft(Map.of("evidence", ""), false), 301L, 3L, 6L,
                        "solution-design"));

        assertThat(result.status()).isEqualTo("NEEDS_INPUT");
        assertThat(result.errorCode()).isEqualTo("RESOURCE_NODE_MISSING_INPUT");
        assertThat(result.missingFields()).containsExactly("evidence");
        verify(eventMapper).appendNext(any(), any(), any(), eq("INPUT_REQUIRED"), any());
    }

    @Test
    void rejectsMissingSourceBeforePersistingTurn() {
        AiWorkflowContextDTO context = projectContext(List.of("evidence"));
        when(connectorPort.readContext(eq("project"), eq(42L), any(ContextReadOptions.class))).thenReturn(context);
        AiOrchestratorResponse.ConversationView conversation = createProjectConversation();

        assertThatThrownBy(() -> service.appendTurn(7L, conversation.conversationId(),
                command("project", draftWithSource("project:999"), 301L, 3L, 6L,
                        "solution-design")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("UNKNOWN_SOURCE_REF");
        verify(turnMapper, never()).insert(any(AiConversationTurnDO.class));
    }

    @Test
    void rejectsUnsupportedComponent() {
        AiWorkflowContextDTO context = projectContext(List.of("evidence"));
        when(connectorPort.readContext(eq("project"), eq(42L), any(ContextReadOptions.class))).thenReturn(context);
        AiOrchestratorResponse.ConversationView conversation = createProjectConversation();

        assertThatThrownBy(() -> service.appendTurn(7L, conversation.conversationId(),
                command("project", projectDraft(Map.of("evidence", "通过"), false), 301L, 3L, 6L,
                        "missing-component")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CAPABILITY_UNAVAILABLE");
    }

    @Test
    void rejectsCurrentNodeConflict() {
        AiWorkflowContextDTO context = projectContext(List.of("evidence"));
        when(connectorPort.readContext(eq("project"), eq(42L), any(ContextReadOptions.class))).thenReturn(context);
        AiOrchestratorResponse.ConversationView conversation = createProjectConversation();

        assertThatThrownBy(() -> service.appendTurn(7L, conversation.conversationId(),
                command("project", projectDraft(Map.of("evidence", "通过"), false), 302L, 3L, 6L,
                        "solution-design")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前节点不一致");
    }

    @Test
    void needsInputContinuationMergesOnlyValidPreviousPatchAndKeepsOldPlanImmutable() throws Exception {
        AiWorkflowContextDTO context = projectContext(List.of("evidence"));
        when(connectorPort.readContext(eq("project"), eq(42L), any(ContextReadOptions.class))).thenReturn(context);
        AiOrchestratorResponse.ConversationView conversation = createProjectConversation();

        AiOrchestratorResponse.PlanView first = service.appendTurn(7L, conversation.conversationId(),
                command("project", projectDraft(Map.of("result", "通过"), false), 301L, 3L, 6L,
                        "solution-design"));
        AiPlanDO oldPlan = storedPlans.get(0);
        String oldDraftJson = oldPlan.getDraftJson();
        String oldStatus = oldPlan.getStatus();

        AiOrchestratorResponse.PlanView second = service.appendTurn(7L, conversation.conversationId(),
                command("project", projectDraft(Map.of("evidence", "已核对"), false), 301L, 3L, 6L,
                        "solution-design"));

        assertThat(first.status()).isEqualTo("NEEDS_INPUT");
        assertThat(second.status()).isEqualTo("PLAN_READY");
        assertThat(second.draft().draft().patch())
                .containsEntry("result", "通过")
                .containsEntry("evidence", "已核对");
        assertThat(oldPlan.getStatus()).isEqualTo(oldStatus);
        assertThat(oldPlan.getDraftJson()).isEqualTo(oldDraftJson);
        assertThat(storedPlans).hasSize(2);
    }

    private AiOrchestratorResponse.ConversationView createProjectConversation() {
        return service.createConversation(7L,
                new AiOrchestratorRequest.CreateConversationCommand("project", 42L));
    }

    private AiOrchestratorRequest.AppendTurnCommand command(
            String resourceType, AgentDraft draft, Long nodeId, Long contextVersion,
            Long nodeVersion, String componentKey) {
        return new AiOrchestratorRequest.AppendTurnCommand(
                "推进资源节点", "resource-node/v1", draft, nodeId, contextVersion,
                resourceType + "-turn", nodeVersion, componentKey);
    }

    private AgentDraft projectDraft(Map<String, Object> patch, boolean completion) {
        return resourceDraft("project", patch, completion);
    }

    private AgentDraft resourceDraft(String resourceType, Map<String, Object> patch, boolean completion) {
        long nodeId = "topic".equals(resourceType) ? 511L : 301L;
        String componentKey = "topic".equals(resourceType) ? "topic-design-review" : "solution-design";
        return new AgentDraft("resource-node/v1", new AgentDraft.DraftPayload(
                List.of(new AgentDraftItem("节点事实",
                        List.of(resourceType + "-node:" + nodeId + "#component:" + componentKey), "high")),
                List.of(), List.of(), List.of(), patch, completion, Map.of()));
    }

    private AgentDraft draftWithSource(String sourceRef) {
        return new AgentDraft("resource-node/v1", new AgentDraft.DraftPayload(
                List.of(new AgentDraftItem("节点事实", List.of(sourceRef), "high")),
                List.of(), List.of(), List.of(), Map.of("evidence", "通过"), false, Map.of()));
    }

    private AiWorkflowContextDTO projectContext(List<String> requiredFields) {
        Map<String, Object> contract = Map.of(
                "command", "node.field.update",
                "nodeVersion", 6,
                "componentKey", "solution-design",
                "writableFields", List.of("result", "evidence"),
                "requiredFields", requiredFields,
                "protectedFields", List.of("projectId", "nodeId", "status"));
        AiWorkflowContextDTO.Component component = new AiWorkflowContextDTO.Component(
                "solution-design", "方案", List.of(), List.of(), null,
                objectMapper.valueToTree(Map.of()), "LOADED", Map.of("content", 4), contract);
        AiWorkflowContextDTO.Node node = new AiWorkflowContextDTO.Node(301L, "solution", "方案", 0, 1,
                List.of(component), List.of(), Map.of(), 6, null, null, null, null,
                null, null, null, "LOADED", null, null);
        return new AiWorkflowContextDTO("project", 42L, 3,
                new AiWorkflowContextDTO.CurrentNode(301L, "solution", "方案"),
                new AiWorkflowContextDTO.Workflow(1L, 1, List.of(node)),
                List.of("node.field.update"), 3, Map.of(), List.of(),
                new AiWorkflowContextDTO.ContextMeta(1, true, null, null, "focused", null,
                        "component", 301L, "solution-design", true, "ACTIVE", null, 2,
                        null, null, null, List.of()));
    }

    private AiWorkflowContextDTO topicContext(List<String> requiredFields) {
        Map<String, Object> contract = Map.of(
                "command", "development-item.node.field.update",
                "version", 4,
                "componentKey", "topic-design-review",
                "writableFields", List.of("result"),
                "requiredFields", requiredFields,
                "protectedFields", List.of("status"));
        AiWorkflowContextDTO.Component component = new AiWorkflowContextDTO.Component(
                "topic-design-review", "设计评审", List.of(), List.of(), null,
                objectMapper.valueToTree(Map.of()), "LOADED", Map.of("content", 2), contract);
        AiWorkflowContextDTO.Node node = new AiWorkflowContextDTO.Node(511L, "design", "设计", 0, 1,
                List.of(component), List.of(), Map.of(), 4, null, null, null, null,
                null, null, null, "LOADED", null, null);
        return new AiWorkflowContextDTO("topic", 43L, 4,
                new AiWorkflowContextDTO.CurrentNode(511L, "design", "设计"),
                new AiWorkflowContextDTO.Workflow(1L, 1, List.of(node)),
                List.of("development-item.node.field.update"), null, Map.of(), List.of(),
                new AiWorkflowContextDTO.ContextMeta(1, true, null, null, "focused", null,
                        "component", 511L, "topic-design-review", true, "ACTIVE", null, null,
                        null, null, null, List.of()));
    }
}
