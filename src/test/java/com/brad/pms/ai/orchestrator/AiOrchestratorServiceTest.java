package com.brad.pms.ai.orchestrator;

import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.OperationExecuteRequest;
import com.brad.pms.ai.connector.context.ContextReadOptions;
import com.brad.pms.ai.orchestrator.api.AiOrchestratorRequest;
import com.brad.pms.ai.orchestrator.api.AiOrchestratorResponse;
import com.brad.pms.ai.orchestrator.connector.AiConnectorPort;
import com.brad.pms.ai.orchestrator.contract.AgentDraft;
import com.brad.pms.ai.orchestrator.contract.AgentDraftItem;
import com.brad.pms.ai.orchestrator.contract.AiAgentDraftValidator;
import com.brad.pms.ai.orchestrator.contract.DraftValidationResult;
import com.brad.pms.ai.orchestrator.persistence.AiConversationDO;
import com.brad.pms.ai.orchestrator.persistence.AiConversationMapper;
import com.brad.pms.ai.orchestrator.persistence.AiConversationTurnMapper;
import com.brad.pms.ai.orchestrator.persistence.AiOperationEventMapper;
import com.brad.pms.ai.orchestrator.persistence.AiPlanDO;
import com.brad.pms.ai.orchestrator.persistence.AiPlanMapper;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.integration.ai.api.AiOperationPreviewDTO;
import com.brad.pms.integration.ai.api.AiOperationResultDTO;
import com.brad.pms.integration.ai.api.AiResourceDetailDTO;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.brad.pms.ai.orchestrator.service.AiOrchestratorService;
import com.brad.pms.ai.orchestrator.service.AiOrchestratorVerificationService;
import com.brad.pms.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class AiOrchestratorServiceTest {

    private final AiConversationMapper conversationMapper = mock(AiConversationMapper.class);
    private final AiConversationTurnMapper turnMapper = mock(AiConversationTurnMapper.class);
    private final AiPlanMapper planMapper = mock(AiPlanMapper.class);
    private final AiOperationEventMapper eventMapper = mock(AiOperationEventMapper.class);
    private final com.brad.pms.mapper.AiOperationMapper operationMapper = mock(com.brad.pms.mapper.AiOperationMapper.class);
    private final AiConnectorPort connectorPort = mock(AiConnectorPort.class);
    private final AiAgentDraftValidator draftValidator = mock(AiAgentDraftValidator.class);
    private final AiOrchestratorVerificationService verificationService = mock(AiOrchestratorVerificationService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final AiOrchestratorService service = new AiOrchestratorService(
            conversationMapper, turnMapper, planMapper, eventMapper, operationMapper,
            connectorPort, draftValidator, verificationService, objectMapper);

    @Test
    void runsContextDraftPreviewConfirmAndVerificationInOrder() throws Exception {
        AiConversationDO conversation = conversation(10L);
        AiWorkflowContextDTO context = context(4, "原分析");
        AiWorkflowContextDTO after = context(5, "新分析");
        AgentDraft draft = draft("新分析");
        AiPlanDO plan = plan(30L, conversation.getId(), 20L, "PLAN_READY", context, draft);
        AiOperationPreviewDTO preview = preview("op-1", "4");
        AiOperationResultDTO operation = new AiOperationResultDTO("op-1", "SUCCEEDED", "已保存", Map.of(), List.of());
        AiOrchestratorResponse.VerificationView verification = verification(true, true, true, true, "SUCCEEDED");

        when(conversationMapper.selectByIdAndUserId(7L, 7L)).thenReturn(null);
        doAnswer(invocation -> {
            AiConversationDO value = invocation.getArgument(0);
            value.setId(10L);
            return 1;
        }).when(conversationMapper).insert(any(AiConversationDO.class));
        when(conversationMapper.selectByIdAndUserId(10L, 7L)).thenReturn(conversation);
        when(connectorPort.readContext("requirement", 33L, ContextReadOptions.legacy())).thenReturn(context);
        when(draftValidator.validate(draft, context)).thenReturn(new DraftValidationResult(List.of()));
        doAnswer(invocation -> {
            invocation.getArgument(0, com.brad.pms.ai.orchestrator.persistence.AiConversationTurnDO.class).setId(20L);
            return 1;
        }).when(turnMapper).insert(any(com.brad.pms.ai.orchestrator.persistence.AiConversationTurnDO.class));
        doAnswer(invocation -> {
            invocation.getArgument(0, AiPlanDO.class).setId(30L);
            return 1;
        }).when(planMapper).insert(any(AiPlanDO.class));
        when(planMapper.selectForUpdate(30L, 7L)).thenReturn(plan);
        when(connectorPort.preview(any(CommandPreviewRequest.class))).thenReturn(preview);
        when(connectorPort.execute(any())).thenReturn(operation);
        when(connectorPort.readContext("requirement", 33L, new ContextReadOptions(
                ContextReadOptions.VIEW_FOCUSED, 375L, null))).thenReturn(after);
        when(connectorPort.readResource("requirement", 33L)).thenReturn(
                new AiResourceDetailDTO("requirement", 33L, 5, Map.of("analysis", "新分析")));
        when(verificationService.verify(any(), any(), any())).thenReturn(verification);
        when(eventMapper.appendNext(any(), any(), any(), any(), any())).thenReturn(1);

        AiOrchestratorResponse.ConversationView created = service.createConversation(7L,
                new AiOrchestratorRequest.CreateConversationCommand("requirement", 33L));
        AiOrchestratorResponse.PlanView prepared = service.appendTurn(7L, created.conversationId(),
                new AiOrchestratorRequest.AppendTurnCommand("请分析并保存", "requirement-analysis/v1",
                        draft, 375L, 4L, "request-1"));
        AiOrchestratorResponse.PlanView planned = service.preview(7L, prepared.planId());
        AiOrchestratorResponse.PlanView executed = service.confirm(7L, planned.planId(), "idem-1");

        assertThat(prepared.status()).isEqualTo("PLAN_READY");
        assertThat(planned.status()).isEqualTo("WAITING_CONFIRMATION");
        assertThat(planned.preview().operationId()).isEqualTo("op-1");
        assertThat(executed.status()).isEqualTo("SUCCEEDED");
        assertThat(executed.verification()).isEqualTo(verification);
        verify(connectorPort).preview(any(CommandPreviewRequest.class));
        ArgumentCaptor<OperationExecuteRequest> executeRequest = ArgumentCaptor.forClass(OperationExecuteRequest.class);
        verify(connectorPort).execute(executeRequest.capture());
        assertThat(executeRequest.getValue().operationId()).isEqualTo("op-1");
        verify(verificationService).verify(any(), any(), any());
    }

    @Test
    void rejectsInvalidDraftBeforePreview() {
        AiConversationDO conversation = conversation(10L);
        AiWorkflowContextDTO context = context(4, "原分析");
        AgentDraft draft = draft("新分析");
        when(conversationMapper.selectByIdAndUserId(10L, 7L)).thenReturn(conversation);
        when(connectorPort.readContext("requirement", 33L, ContextReadOptions.legacy())).thenReturn(context);
        when(draftValidator.validate(draft, context)).thenReturn(new DraftValidationResult(List.of(
                new DraftValidationResult.ValidationError("UNKNOWN_SOURCE_REF", "draft.facts[0]", "不可读"))));

        assertThatThrownBy(() -> service.appendTurn(7L, 10L,
                new AiOrchestratorRequest.AppendTurnCommand("分析", "requirement-analysis/v1", draft,
                        375L, 4L, "request-1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("UNKNOWN_SOURCE_REF");
        verify(connectorPort, never()).preview(any());
    }

    @Test
    void rejectsNegativeEventCursor() {
        assertThatThrownBy(() -> service.listEvents(7L, 10L, -1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("事件游标不能为负数");
        assertThatThrownBy(() -> service.getSession(7L, 10L, -1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("事件游标不能为负数");
    }

    @Test
    void returnsConflictWithoutVerificationWhenExecutionReportsStaleVersion() {
        AiWorkflowContextDTO context = context(4, "原分析");
        AgentDraft draft = draft("新分析");
        AiPlanDO plan = plan(30L, 10L, 20L, "WAITING_CONFIRMATION", context, draft);
        plan.setPreviewOperationId("op-1");
        when(planMapper.selectForUpdate(30L, 7L)).thenReturn(plan);
        when(connectorPort.execute(any())).thenReturn(
                new AiOperationResultDTO("op-1", "CONFLICT", "版本已变化", Map.of(), List.of()));
        when(eventMapper.appendNext(any(), any(), any(), any(), any())).thenReturn(1);

        AiOrchestratorResponse.PlanView result = service.confirm(7L, 30L, "idem-1");

        assertThat(result.status()).isEqualTo("CONFLICT");
        verify(verificationService, never()).verify(any(), any(), any());
    }

    @Test
    void refusesToConfirmWhenThePlanHasNoPreviewOperation() {
        AiWorkflowContextDTO context = context(4, "原分析");
        AiPlanDO plan = plan(30L, 10L, 20L, "WAITING_CONFIRMATION", context, draft("新分析"));
        plan.setPreviewOperationId(null);
        when(planMapper.selectForUpdate(30L, 7L)).thenReturn(plan);

        assertThatThrownBy(() -> service.confirm(7L, 30L, "idem-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("预览操作");
        verify(connectorPort, never()).execute(any());
    }

    @Test
    void recordsUnknownWriteResultWhenReadbackFails() {
        AiWorkflowContextDTO context = context(4, "原分析");
        AgentDraft draft = draft("新分析");
        AiPlanDO plan = plan(30L, 10L, 20L, "WAITING_CONFIRMATION", context, draft);
        plan.setPreviewOperationId("op-1");
        when(planMapper.selectForUpdate(30L, 7L)).thenReturn(plan);
        when(connectorPort.execute(any())).thenReturn(
                new AiOperationResultDTO("op-1", "SUCCEEDED", "已保存", Map.of(), List.of()));
        when(verificationService.verify(any(), any(), any())).thenReturn(
                verification(false, false, false, false, "WRITE_RESULT_UNKNOWN"));
        when(eventMapper.appendNext(any(), any(), any(), any(), any())).thenReturn(1);

        AiOrchestratorResponse.PlanView result = service.confirm(7L, 30L, "idem-1");

        assertThat(result.status()).isEqualTo("WRITE_RESULT_UNKNOWN");
        assertThat(result.errorCode()).isEqualTo("WRITE_RESULT_UNKNOWN");
        ArgumentCaptor<String> eventType = ArgumentCaptor.forClass(String.class);
        verify(eventMapper, org.mockito.Mockito.atLeastOnce())
                .appendNext(any(), any(), any(), eventType.capture(), any());
        assertThat(eventType.getAllValues()).contains("READBACK_UNKNOWN");
    }

    @Test
    void retriesUnknownWriteWithTheSameIdempotencyKeyWithoutExecutingAgain() throws Exception {
        AiWorkflowContextDTO context = context(4, "原分析");
        AgentDraft draft = draft("新分析");
        AiPlanDO plan = plan(30L, 10L, 20L, "WRITE_RESULT_UNKNOWN", context, draft);
        plan.setPreviewOperationId("op-1");
        plan.setIdempotencyKey("idem-1");
        AiOperationDO operation = storedOperation("op-1", "SUCCEEDED", context(5, "新分析"));
        when(planMapper.selectForUpdate(30L, 7L)).thenReturn(plan);
        when(operationMapper.selectById("op-1")).thenReturn(operation);
        AiOrchestratorResponse.VerificationView verification = verification(true, true, true, true, "SUCCEEDED");
        when(verificationService.verify(any(), any(), any())).thenReturn(verification);
        when(eventMapper.appendNext(any(), any(), any(), any(), any())).thenReturn(1);

        AiOrchestratorResponse.PlanView result = service.confirm(7L, 30L, "idem-1");

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.operation().operationId()).isEqualTo("op-1");
        verify(verificationService).verify(any(), any(), any());
        verify(connectorPort, never()).execute(any());
    }

    @Test
    void refusesUnknownWriteRetryWithADifferentIdempotencyKey() {
        AiWorkflowContextDTO context = context(4, "原分析");
        AiPlanDO plan = plan(30L, 10L, 20L, "WRITE_RESULT_UNKNOWN", context, draft("新分析"));
        plan.setPreviewOperationId("op-1");
        plan.setIdempotencyKey("idem-1");
        when(planMapper.selectForUpdate(30L, 7L)).thenReturn(plan);

        assertThatThrownBy(() -> service.confirm(7L, 30L, "idem-2"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("幂等键");
        verify(connectorPort, never()).execute(any());
        verify(verificationService, never()).verify(any(), any(), any());
    }

    @Test
    void restoresTheStoredOperationWhenASeparateProcessReadsTheSession() throws Exception {
        AiWorkflowContextDTO context = context(5, "新分析");
        AgentDraft draft = draft("新分析");
        AiPlanDO plan = plan(30L, 10L, 20L, "SUCCEEDED", context, draft);
        plan.setPreviewOperationId("op-1");
        plan.setIdempotencyKey("idem-1");
        plan.setVerificationJson(objectMapper.writeValueAsString(verification(true, true, true, true, "SUCCEEDED")));
        AiOperationDO operation = storedOperation("op-1", "SUCCEEDED", context);
        AiConversationDO conversation = conversation(10L);
        when(conversationMapper.selectByIdAndUserId(10L, 7L)).thenReturn(conversation);
        when(planMapper.selectLatestByConversationUser(10L, 7L)).thenReturn(plan);
        when(operationMapper.selectById("op-1")).thenReturn(operation);
        when(eventMapper.selectAfterSequence(10L, 7L, 0L, 100)).thenReturn(List.of());

        AiOrchestratorResponse.SessionView session = service.getSession(7L, 10L, 0);

        assertThat(session.latestPlan().operation().operationId()).isEqualTo("op-1");
        assertThat(session.latestPlan().verification().status()).isEqualTo("SUCCEEDED");
    }

    @Test
    void replaysTheStoredOperationForTheSameIdempotencyKey() throws Exception {
        AiWorkflowContextDTO context = context(5, "新分析");
        AgentDraft draft = draft("新分析");
        AiPlanDO plan = plan(30L, 10L, 20L, "SUCCEEDED", context, draft);
        plan.setPreviewOperationId("op-1");
        plan.setIdempotencyKey("idem-1");
        AiOperationDO operation = new AiOperationDO();
        operation.setId("op-1");
        operation.setStatus("SUCCEEDED");
        operation.setIdempotencyKey("idem-1");
        operation.setResultJson(objectMapper.writeValueAsString(
                new CommandResult("op-1", "SUCCEEDED", "已保存", Map.of(), List.of())));
        when(planMapper.selectForUpdate(30L, 7L)).thenReturn(plan);
        when(operationMapper.selectById("op-1")).thenReturn(operation);

        AiOrchestratorResponse.PlanView result = service.confirm(7L, 30L, "idem-1");

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.operation().operationId()).isEqualTo("op-1");
        verify(connectorPort, never()).execute(any());
    }

    @Test
    void recoversAProcessThatStoppedAfterExecutionBeforeVerification() throws Exception {
        AiWorkflowContextDTO context = context(4, "原分析");
        AiPlanDO plan = plan(30L, 10L, 20L, "VERIFYING", context, draft("新分析"));
        plan.setPreviewOperationId("op-1");
        plan.setIdempotencyKey("idem-1");
        AiOperationDO operation = storedOperation("op-1", "SUCCEEDED", context);
        when(planMapper.selectForUpdate(30L, 7L)).thenReturn(plan);
        when(operationMapper.selectById("op-1")).thenReturn(operation);
        when(verificationService.verify(any(), any(), any()))
                .thenReturn(verification(true, true, true, true, "SUCCEEDED"));
        when(eventMapper.appendNext(any(), any(), any(), any(), any())).thenReturn(1);

        AiOrchestratorResponse.PlanView result = service.confirm(7L, 30L, "idem-1");

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        verify(connectorPort, never()).execute(any());
        verify(verificationService).verify(any(), any(), any());
    }

    @Test
    void recoversAProcessThatStoppedBeforeConnectorExecution() throws Exception {
        AiWorkflowContextDTO context = context(4, "原分析");
        AiPlanDO plan = plan(30L, 10L, 20L, "EXECUTING", context, draft("新分析"));
        plan.setPreviewOperationId("op-1");
        plan.setIdempotencyKey("idem-1");
        AiOperationDO operation = storedOperation("op-1", "PREVIEW", context);
        when(planMapper.selectForUpdate(30L, 7L)).thenReturn(plan);
        when(operationMapper.selectById("op-1")).thenReturn(operation);
        when(connectorPort.execute(any())).thenReturn(
                new AiOperationResultDTO("op-1", "SUCCEEDED", "已保存", Map.of(), List.of()));
        when(verificationService.verify(any(), any(), any()))
                .thenReturn(verification(true, true, true, true, "SUCCEEDED"));
        when(eventMapper.appendNext(any(), any(), any(), any(), any())).thenReturn(1);

        AiOrchestratorResponse.PlanView result = service.confirm(7L, 30L, "idem-1");

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        verify(connectorPort).execute(any());
        verify(verificationService).verify(any(), any(), any());
    }

    @Test
    void refusesInFlightRecoveryWithADifferentIdempotencyKey() {
        AiWorkflowContextDTO context = context(4, "原分析");
        AiPlanDO plan = plan(30L, 10L, 20L, "VERIFYING", context, draft("新分析"));
        plan.setPreviewOperationId("op-1");
        plan.setIdempotencyKey("idem-1");
        when(planMapper.selectForUpdate(30L, 7L)).thenReturn(plan);

        assertThatThrownBy(() -> service.confirm(7L, 30L, "idem-2"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("原幂等键");
        verify(operationMapper, never()).selectById(any());
        verify(connectorPort, never()).execute(any());
    }

    @Test
    void createsStoryConversationAndReturnsNeedsInputWithoutPreview() {
        AiConversationDO conversation = storyConversation(10L);
        AiWorkflowContextDTO context = storyContext(Map.of());
        AgentDraft draft = storyDraft(Map.of("implementationNote", "已完成"));
        when(conversationMapper.selectByIdAndUserId(10L, 7L)).thenReturn(conversation);
        doAnswer(invocation -> {
            invocation.getArgument(0, AiConversationDO.class).setId(10L);
            return 1;
        }).when(conversationMapper).insert(any(AiConversationDO.class));
        when(connectorPort.readContext("story", 34L, new ContextReadOptions(
                ContextReadOptions.VIEW_FOCUSED, 702L, "story-node-workbench"))).thenReturn(context);
        when(draftValidator.validateStory(any(), any(), any())).thenReturn(new DraftValidationResult(List.of()));
        doAnswer(invocation -> {
            invocation.getArgument(0, com.brad.pms.ai.orchestrator.persistence.AiConversationTurnDO.class).setId(20L);
            return 1;
        }).when(turnMapper).insert(any(com.brad.pms.ai.orchestrator.persistence.AiConversationTurnDO.class));
        doAnswer(invocation -> {
            invocation.getArgument(0, AiPlanDO.class).setId(30L);
            return 1;
        }).when(planMapper).insert(any(AiPlanDO.class));
        when(eventMapper.appendNext(any(), any(), any(), any(), any())).thenReturn(1);

        AiOrchestratorResponse.ConversationView created = service.createConversation(7L,
                new AiOrchestratorRequest.CreateConversationCommand("story", 34L));
        AiOrchestratorResponse.PlanView result = service.appendTurn(7L, created.conversationId(),
                new AiOrchestratorRequest.AppendTurnCommand("补充开发信息", "story-workbench/v1",
                        draft, 702L, 8L, "story-turn-1"));

        assertThat(result.status()).isEqualTo("NEEDS_INPUT");
        assertThat(result.errorCode()).isEqualTo("STORY_MISSING_INPUT");
        assertThat(result.missingFields()).containsExactly("selfTestResult");
        assertThat(result.questions()).singleElement().isEqualTo("请补充自测结果：验证了哪些场景，结果如何？");
        verify(connectorPort, never()).preview(any());
    }

    @Test
    void needsInputContinuationMergesThePreviousPlanPatchAndCreatesANewPlan() {
        AiConversationDO conversation = storyConversation(10L);
        AiWorkflowContextDTO context = storyContext(Map.of());
        when(conversationMapper.selectByIdAndUserId(10L, 7L)).thenReturn(conversation);
        doAnswer(invocation -> {
            invocation.getArgument(0, AiConversationDO.class).setId(10L);
            return 1;
        }).when(conversationMapper).insert(any(AiConversationDO.class));
        when(connectorPort.readContext("story", 34L, new ContextReadOptions(
                ContextReadOptions.VIEW_FOCUSED, 702L, "story-node-workbench"))).thenReturn(context);
        when(draftValidator.validateStory(any(), any(), any())).thenReturn(new DraftValidationResult(List.of()));
        AtomicInteger turnId = new AtomicInteger(19);
        doAnswer(invocation -> {
            invocation.getArgument(0, com.brad.pms.ai.orchestrator.persistence.AiConversationTurnDO.class)
                    .setId((long) turnId.incrementAndGet());
            return 1;
        }).when(turnMapper).insert(any(com.brad.pms.ai.orchestrator.persistence.AiConversationTurnDO.class));
        List<AiPlanDO> storedPlans = new ArrayList<>();
        doAnswer(invocation -> {
            AiPlanDO inserted = invocation.getArgument(0, AiPlanDO.class);
            inserted.setId((long) (400 + storedPlans.size()));
            storedPlans.add(inserted);
            return 1;
        }).when(planMapper).insert(any(AiPlanDO.class));
        when(planMapper.selectLatestByConversationUser(10L, 7L)).thenAnswer(
                invocation -> storedPlans.isEmpty() ? null : storedPlans.get(storedPlans.size() - 1));
        when(eventMapper.appendNext(any(), any(), any(), any(), any())).thenReturn(1);

        AiOrchestratorResponse.ConversationView created = service.createConversation(7L,
                new AiOrchestratorRequest.CreateConversationCommand("story", 34L));
        AiOrchestratorResponse.PlanView firstTurn = service.appendTurn(7L, created.conversationId(),
                new AiOrchestratorRequest.AppendTurnCommand("补充实现说明", "story-workbench/v1",
                        storyDraft(Map.of("implementationNote", "先写实现说明")), 702L, 8L, "story-turn-first"));
        assertThat(firstTurn.status()).isEqualTo("NEEDS_INPUT");
        AiPlanDO needsInputPlan = storedPlans.get(0);
        String needsInputDraftJson = needsInputPlan.getDraftJson();

        AiOrchestratorResponse.PlanView secondTurn = service.appendTurn(7L, created.conversationId(),
                new AiOrchestratorRequest.AppendTurnCommand("补充自测结果", "story-workbench/v1",
                        storyDraft(Map.of("selfTestResult", "通过")), 702L, 8L, "story-turn-second"));

        assertThat(secondTurn.status()).isEqualTo("PLAN_READY");
        assertThat(secondTurn.errorCode()).isNull();
        assertThat(secondTurn.draft().draft().patch())
                .containsEntry("implementationNote", "先写实现说明")
                .containsEntry("selfTestResult", "通过");
        // 新 turn 生成新 Plan，不修改原 Plan
        AiPlanDO mergedPlan = storedPlans.get(1);
        assertThat(mergedPlan.getId()).isNotEqualTo(needsInputPlan.getId());
        assertThat(needsInputPlan.getStatus()).isEqualTo("NEEDS_INPUT");
        assertThat(needsInputPlan.getDraftJson()).isEqualTo(needsInputDraftJson);
        verify(connectorPort, never()).preview(any());
    }

    @Test
    void rejectsAStoryTurnThatSwitchesNodesWhileTheConversationHasAnActivePlan() {
        AiConversationDO conversation = storyConversation(10L);
        AiPlanDO activePlan = new AiPlanDO();
        activePlan.setId(31L);
        activePlan.setNodeId(701L);
        activePlan.setStatus("WAITING_CONFIRMATION");
        when(conversationMapper.selectByIdAndUserId(10L, 7L)).thenReturn(conversation);
        when(planMapper.selectLatestByConversationUser(10L, 7L)).thenReturn(activePlan);

        assertThatThrownBy(() -> service.appendTurn(7L, 10L,
                new AiOrchestratorRequest.AppendTurnCommand("切换节点", "story-workbench/v1",
                        storyDraft(Map.of("implementationNote", "x")), 702L, 8L, "story-turn-2")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CONVERSATION_TARGET_MISMATCH");
        verify(connectorPort, never()).readContext(any(), any(), any());
    }

    @Test
    void keepsStoryOnlyPlanFieldsOutOfRequirementJson() throws Exception {
        String json = objectMapper.writeValueAsString(new AiOrchestratorResponse.PlanView(
                30L, 20L, "REQUIREMENT_ANALYSIS_SAVE", "PLAN_READY", "requirement", 33L,
                375L, 4L, null, draft("分析"), null, null, null, List.of(), null, null));

        assertThat(json).doesNotContain("nodeContract", "missingFields", "questions");
    }

    @Test
    void previewsACompleteStoryDraftThroughTheExistingDevelopmentItemCommand() throws Exception {
        AiConversationDO conversation = storyConversation(10L);
        AiWorkflowContextDTO context = storyContext(Map.of());
        AgentDraft draft = storyDraft(Map.of("implementationNote", "已完成", "selfTestResult", "通过"));
        when(conversationMapper.selectByIdAndUserId(10L, 7L)).thenReturn(conversation);
        when(connectorPort.readContext("story", 34L, new ContextReadOptions(
                ContextReadOptions.VIEW_FOCUSED, 702L, "story-node-workbench"))).thenReturn(context);
        when(draftValidator.validateStory(any(), any(), any())).thenReturn(new DraftValidationResult(List.of()));
        doAnswer(invocation -> {
            invocation.getArgument(0, AiConversationDO.class).setId(10L);
            return 1;
        }).when(conversationMapper).insert(any(AiConversationDO.class));
        doAnswer(invocation -> {
            invocation.getArgument(0, com.brad.pms.ai.orchestrator.persistence.AiConversationTurnDO.class).setId(20L);
            return 1;
        }).when(turnMapper).insert(any(com.brad.pms.ai.orchestrator.persistence.AiConversationTurnDO.class));
        doAnswer(invocation -> {
            invocation.getArgument(0, AiPlanDO.class).setId(30L);
            return 1;
        }).when(planMapper).insert(any(AiPlanDO.class));
        when(eventMapper.appendNext(any(), any(), any(), any(), any())).thenReturn(1);
        AiPlanDO storedPlan = new AiPlanDO();
        storedPlan.setId(30L);
        storedPlan.setConversationId(10L);
        storedPlan.setTurnId(20L);
        storedPlan.setIntent("STORY_NODE_FIELD_UPDATE");
        storedPlan.setResourceType("story");
        storedPlan.setResourceId(34L);
        storedPlan.setNodeId(702L);
        storedPlan.setContextVersion(8L);
        storedPlan.setContextSnapshotJson(objectMapper.writeValueAsString(context));
        storedPlan.setDraftJson(objectMapper.writeValueAsString(draft));
        storedPlan.setStatus("PLAN_READY");
        when(planMapper.selectForUpdate(30L, 7L)).thenReturn(storedPlan);
        when(connectorPort.preview(any())).thenReturn(preview("op-story", "8"));
        when(connectorPort.execute(any())).thenReturn(new AiOperationResultDTO(
                "op-story", "SUCCEEDED", "已保存", Map.of(), List.of()));
        AiOrchestratorResponse.VerificationView storyVerification = verification(true, true, true, true, "SUCCEEDED");
        when(verificationService.verify(any(), any(), any())).thenReturn(storyVerification);

        AiOrchestratorResponse.ConversationView created = service.createConversation(7L,
                new AiOrchestratorRequest.CreateConversationCommand("story", 34L));
        AiOrchestratorResponse.PlanView prepared = service.appendTurn(7L, created.conversationId(),
                new AiOrchestratorRequest.AppendTurnCommand("完成开发节点", "story-workbench/v1",
                        draft, 702L, 8L, "story-turn-3"));
        AiOrchestratorResponse.PlanView planned = service.preview(7L, prepared.planId());

        ArgumentCaptor<CommandPreviewRequest> request = ArgumentCaptor.forClass(CommandPreviewRequest.class);
        verify(connectorPort).preview(request.capture());
        assertThat(request.getValue().name()).isEqualTo(CommandName.DEVELOPMENT_ITEM_NODE_FIELD_UPDATE);
        assertThat(request.getValue().arguments()).containsEntry("itemType", "STORY");
        assertThat(request.getValue().arguments()).containsEntry("itemId", 34L);
        assertThat(request.getValue().arguments()).containsEntry("nodeId", 702L);
        assertThat(request.getValue().arguments()).containsEntry("version", 8L);
        assertThat(planned.status()).isEqualTo("WAITING_CONFIRMATION");
        assertThat(planned.preview().operationId()).isEqualTo("op-story");
        AiOrchestratorResponse.PlanView executed = service.confirm(7L, planned.planId(), "story-idem-1");
        assertThat(executed.status()).isEqualTo("SUCCEEDED");
        assertThat(executed.verification()).isEqualTo(storyVerification);
        ArgumentCaptor<String> eventType = ArgumentCaptor.forClass(String.class);
        verify(eventMapper, org.mockito.Mockito.atLeastOnce())
                .appendNext(any(), any(), any(), eventType.capture(), any());
        assertThat(eventType.getAllValues()).contains("VERIFICATION_FINISHED");
    }

    @Test
    void rejectsAnExecutionResultThatIsNotBoundToThePreviewOperation() throws Exception {
        AiWorkflowContextDTO context = storyContext(Map.of());
        AgentDraft draft = storyDraft(Map.of("implementationNote", "已完成", "selfTestResult", "通过"));
        AiPlanDO storedPlan = new AiPlanDO();
        storedPlan.setId(30L);
        storedPlan.setConversationId(10L);
        storedPlan.setIntent("STORY_NODE_FIELD_UPDATE");
        storedPlan.setResourceType("story");
        storedPlan.setResourceId(34L);
        storedPlan.setNodeId(702L);
        storedPlan.setContextVersion(8L);
        storedPlan.setContextSnapshotJson(objectMapper.writeValueAsString(context));
        storedPlan.setDraftJson(objectMapper.writeValueAsString(draft));
        storedPlan.setPreviewOperationId("op-preview");
        storedPlan.setStatus("WAITING_CONFIRMATION");
        when(planMapper.selectForUpdate(30L, 7L)).thenReturn(storedPlan);
        when(connectorPort.execute(any())).thenReturn(new AiOperationResultDTO(
                "op-other", "SUCCEEDED", "已保存", Map.of(), List.of()));
        when(eventMapper.appendNext(any(), any(), any(), any(), any())).thenReturn(1);

        AiOrchestratorResponse.PlanView result = service.confirm(7L, 30L, "idem-1");

        assertThat(result.status()).isEqualTo("CONFLICT");
        assertThat(result.errorCode()).isEqualTo("OPERATION_MISMATCH");
        verify(verificationService, never()).verify(any(), any(), any());
    }

    private AiConversationDO conversation(Long id) {
        AiConversationDO value = new AiConversationDO();
        value.setId(id);
        value.setUserId(7L);
        value.setResourceType("requirement");
        value.setResourceId(33L);
        value.setStatus("ACTIVE");
        return value;
    }

    private AiPlanDO plan(Long id, Long conversationId, Long turnId, String status,
                          AiWorkflowContextDTO context, AgentDraft draft) {
        AiPlanDO value = new AiPlanDO();
        value.setId(id);
        value.setConversationId(conversationId);
        value.setTurnId(turnId);
        value.setIntent("REQUIREMENT_ANALYSIS_SAVE");
        value.setResourceType("requirement");
        value.setResourceId(33L);
        value.setNodeId(375L);
        value.setContextVersion(Long.valueOf(context.version()));
        try {
            value.setContextSnapshotJson(objectMapper.writeValueAsString(context));
            value.setDraftJson(objectMapper.writeValueAsString(draft));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        value.setStatus(status);
        return value;
    }

    private AiWorkflowContextDTO context(int version, String analysis) {
        return new AiWorkflowContextDTO("requirement", 33L, version,
                new AiWorkflowContextDTO.CurrentNode(375L, "analysis", "分析"),
                null, List.of("requirement.analysis.save"), version,
                Map.of("analysis", analysis, "status", "OPEN"), List.of(), null);
    }

    private AgentDraft draft(String analysis) {
        return new AgentDraft("requirement-analysis/v1", new AgentDraft.DraftPayload(
                List.of(new AgentDraftItem("事实", List.of("requirement:33"), "high")),
                List.of(), List.of(), List.of(), Map.of("analysis", analysis)));
    }

    private AiConversationDO storyConversation(Long id) {
        AiConversationDO value = conversation(id);
        value.setResourceType("story");
        value.setResourceId(34L);
        return value;
    }

    private AiWorkflowContextDTO storyContext(Map<String, Object> content) {
        AiWorkflowContextDTO.Component component = new AiWorkflowContextDTO.Component(
                "story-node-workbench", "开发工作台", List.of(), List.of(),
                objectMapper.valueToTree(Map.of("variant", "development")), objectMapper.valueToTree(content),
                "LOADED", Map.of("node", 8), Map.of(
                "command", "development-item.node.field.update", "variant", "development",
                "writableFields", List.of("implementationNote", "selfTestResult", "testCases"),
                "protectedFields", List.of("status"), "fieldContracts", List.of(
                        Map.of("key", "implementationNote", "type", "string", "required", true,
                                "nullable", false, "collection", false),
                        Map.of("key", "selfTestResult", "type", "string", "required", true,
                                "nullable", false, "collection", false),
                        Map.of("key", "testCases", "type", "array", "required", false,
                                "nullable", true, "collection", true))));
        AiWorkflowContextDTO.Node node = new AiWorkflowContextDTO.Node(702L, "development", "开发", 0, 1,
                List.of(component), List.of(), Map.of(), 8, null, null, null, null,
                null, null, null, "LOADED", null, null);
        return new AiWorkflowContextDTO("story", 34L, 8,
                new AiWorkflowContextDTO.CurrentNode(702L, "development", "开发"),
                new AiWorkflowContextDTO.Workflow(1L, 1, List.of(node)),
                List.of("development-item.node.field.update"), null, Map.of(), List.of(), null);
    }

    private AgentDraft storyDraft(Map<String, Object> patch) {
        return new AgentDraft("story-workbench/v1", new AgentDraft.DraftPayload(
                List.of(new AgentDraftItem("节点事实", List.of("story:34"), "high")),
                List.of(), List.of(), List.of(), patch));
    }

    private AiOperationPreviewDTO preview(String operationId, String version) {
        return new AiOperationPreviewDTO(operationId, "requirement.analysis.save",
                Instant.now().plusSeconds(600), version, List.of(), List.of(), List.of("requirement-detail"));
    }

    private AiOperationDO storedOperation(String operationId, String status,
                                          AiWorkflowContextDTO context) throws Exception {
        AiOperationDO operation = new AiOperationDO();
        operation.setId(operationId);
        operation.setStatus(status);
        operation.setIdempotencyKey("idem-1");
        operation.setResultJson(objectMapper.writeValueAsString(
                new CommandResult(operationId, status, "已保存", Map.of(), List.of())));
        operation.setPreviewJson(objectMapper.writeValueAsString(new com.brad.pms.ai.command.CommandPreview(
                operationId, CommandName.REQUIREMENT_ANALYSIS_SAVE, Instant.now().plusSeconds(600),
                String.valueOf(context.version()), List.of(), List.of(), List.of())));
        return operation;
    }

    private AiOrchestratorResponse.VerificationView verification(boolean resourceReadback,
                                                                  boolean contextReadback,
                                                                  boolean protectedFieldsUnchanged,
                                                                  boolean patchApplied,
                                                                  String status) {
        return new AiOrchestratorResponse.VerificationView(resourceReadback, contextReadback,
                protectedFieldsUnchanged, patchApplied, status, status);
    }
}
