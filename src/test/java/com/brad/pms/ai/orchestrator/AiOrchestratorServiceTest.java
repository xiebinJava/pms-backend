package com.brad.pms.ai.orchestrator;

import com.brad.pms.ai.command.CommandResult;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        verify(connectorPort).execute(any());
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

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("WRITE_RESULT_UNKNOWN");
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
        operation.setResultJson(objectMapper.writeValueAsString(
                new CommandResult("op-1", "SUCCEEDED", "已保存", Map.of(), List.of())));
        when(planMapper.selectForUpdate(30L, 7L)).thenReturn(plan);
        when(operationMapper.selectById("op-1")).thenReturn(operation);

        AiOrchestratorResponse.PlanView result = service.confirm(7L, 30L, "idem-1");

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.operation().operationId()).isEqualTo("op-1");
        verify(connectorPort, never()).execute(any());
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

    private AiOperationPreviewDTO preview(String operationId, String version) {
        return new AiOperationPreviewDTO(operationId, "requirement.analysis.save",
                Instant.now().plusSeconds(600), version, List.of(), List.of(), List.of("requirement-detail"));
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
