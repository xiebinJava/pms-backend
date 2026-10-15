package com.brad.pms.ai.orchestrator.service;

import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.OperationExecuteRequest;
import com.brad.pms.ai.connector.context.ContextReadOptions;
import com.brad.pms.ai.orchestrator.api.AiOrchestratorRequest;
import com.brad.pms.ai.orchestrator.api.AiOrchestratorResponse;
import com.brad.pms.ai.orchestrator.connector.AiConnectorPort;
import com.brad.pms.ai.orchestrator.contract.AgentDraft;
import com.brad.pms.ai.orchestrator.contract.AiAgentDraftValidator;
import com.brad.pms.ai.orchestrator.contract.DraftValidationResult;
import com.brad.pms.ai.orchestrator.domain.AiOrchestratorEventType;
import com.brad.pms.ai.orchestrator.domain.AiOrchestratorState;
import com.brad.pms.ai.orchestrator.persistence.AiConversationDO;
import com.brad.pms.ai.orchestrator.persistence.AiConversationMapper;
import com.brad.pms.ai.orchestrator.persistence.AiConversationTurnDO;
import com.brad.pms.ai.orchestrator.persistence.AiConversationTurnMapper;
import com.brad.pms.ai.orchestrator.persistence.AiOperationEventDO;
import com.brad.pms.ai.orchestrator.persistence.AiOperationEventMapper;
import com.brad.pms.ai.orchestrator.persistence.AiPlanDO;
import com.brad.pms.ai.orchestrator.persistence.AiPlanMapper;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.integration.ai.api.AiOperationPreviewDTO;
import com.brad.pms.integration.ai.api.AiOperationResultDTO;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.brad.pms.mapper.AiOperationMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AiOrchestratorService {

    private static final String ACTIVE = "ACTIVE";
    private static final String PLAN_READY = "PLAN_READY";
    private static final String WAITING_CONFIRMATION = "WAITING_CONFIRMATION";
    private static final String EXECUTING = "EXECUTING";
    private static final String VERIFYING = "VERIFYING";
    private static final String SUCCEEDED = "SUCCEEDED";
    private static final String FAILED = "FAILED";
    private static final String CONFLICT = "CONFLICT";

    private final AiConversationMapper conversationMapper;
    private final AiConversationTurnMapper turnMapper;
    private final AiPlanMapper planMapper;
    private final AiOperationEventMapper eventMapper;
    private final AiOperationMapper operationMapper;
    private final AiConnectorPort connectorPort;
    private final AiAgentDraftValidator draftValidator;
    private final AiOrchestratorVerificationService verificationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public AiOrchestratorResponse.ConversationView createConversation(
            Long userId, AiOrchestratorRequest.CreateConversationCommand command) {
        requireUser(userId);
        if (command == null || command.resourceType() == null || command.resourceId() == null) {
            throw BusinessException.error("资源类型和资源 ID 不能为空");
        }
        String resourceType = normalizeResourceType(command.resourceType());
        if (!"requirement".equals(resourceType)) {
            throw BusinessException.error("当前编排器只支持 requirement 资源");
        }
        AiConversationDO conversation = new AiConversationDO();
        conversation.setUserId(userId);
        conversation.setResourceType(resourceType);
        conversation.setResourceId(command.resourceId());
        conversation.setStatus(ACTIVE);
        conversationMapper.insert(conversation);
        if (conversation.getId() == null) throw BusinessException.error("创建 AI 会话失败");
        return conversationView(conversation, null);
    }

    @Transactional
    public AiOrchestratorResponse.PlanView appendTurn(
            Long userId, Long conversationId, AiOrchestratorRequest.AppendTurnCommand command) {
        requireUser(userId);
        AiConversationDO conversation = requireConversation(userId, conversationId);
        if (command == null || command.draft() == null) {
            throw BusinessException.error("Agent Draft 不能为空");
        }
        if (command.message() == null || command.message().isBlank()) {
            throw BusinessException.error("用户消息不能为空");
        }
        if (command.contractVersion() != null
                && !Objects.equals(command.contractVersion(), command.draft().contractVersion())) {
            throw BusinessException.conflict("请求契约版本与 Agent Draft 不一致");
        }

        AiWorkflowContextDTO context = connectorPort.readContext(
                conversation.getResourceType(), conversation.getResourceId(), ContextReadOptions.legacy());
        validateContextTarget(conversation, context, command);

        DraftValidationResult validation = draftValidator.validate(command.draft(), context);
        if (!validation.valid()) {
            throw BusinessException.error("Agent Draft 校验失败: " + validation.errorCodes());
        }

        AiConversationTurnDO turn = new AiConversationTurnDO();
        turn.setConversationId(conversationId);
        turn.setRole("AGENT");
        turn.setMessageText(command.message().trim());
        turn.setRequestId(command.requestId());
        turn.setDraftJson(write(command.draft()));
        turnMapper.insert(turn);
        if (turn.getId() == null) throw BusinessException.error("保存 AI 会话轮次失败");

        AiPlanDO plan = new AiPlanDO();
        plan.setConversationId(conversationId);
        plan.setTurnId(turn.getId());
        plan.setIntent("REQUIREMENT_ANALYSIS_SAVE");
        plan.setResourceType(conversation.getResourceType());
        plan.setResourceId(conversation.getResourceId());
        plan.setNodeId(resolveNodeId(context, command.nodeId()));
        plan.setContextVersion(Long.valueOf(context.version()));
        plan.setContextSnapshotJson(write(context));
        plan.setDraftJson(write(command.draft()));
        plan.setStatus(PLAN_READY);
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.insert(plan);
        if (plan.getId() == null) throw BusinessException.error("创建 AI 执行计划失败");
        appendEvent(plan, null, AiOrchestratorEventType.CONTEXT_READ,
                Map.of("resourceType", plan.getResourceType(), "resourceId", plan.getResourceId()));
        appendEvent(plan, null, AiOrchestratorEventType.DRAFT_VALIDATED,
                Map.of("contractVersion", command.draft().contractVersion()));
        appendEvent(plan, null, AiOrchestratorEventType.PLAN_CREATED,
                Map.of("intent", plan.getIntent(), "nodeId", plan.getNodeId()));
        return planView(plan, context, command.draft(), null, null, null, List.of());
    }

    @Transactional
    public AiOrchestratorResponse.PlanView preview(Long userId, Long planId) {
        requireUser(userId);
        AiPlanDO plan = requirePlan(userId, planId);
        requireState(plan, PLAN_READY);
        AgentDraft draft = readDraft(plan);
        CommandPreviewRequest request = commandRequest(plan, draft);
        AiOperationPreviewDTO preview = connectorPort.preview(request);

        AiOrchestratorState state = state(plan).transition(AiOrchestratorEventType.PREVIEW_CREATED)
                .transition(AiOrchestratorEventType.WAITING_CONFIRMATION);
        plan.setPreviewOperationId(preview.operationId());
        plan.setStatus(state.name());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        appendEvent(plan, preview.operationId(), AiOrchestratorEventType.PREVIEW_CREATED,
                Map.of("operationId", preview.operationId()));
        appendEvent(plan, preview.operationId(), AiOrchestratorEventType.WAITING_CONFIRMATION,
                Map.of("expiresAt", preview.expiresAt()));
        return planView(plan, readContext(plan), draft, preview, null, null, List.of());
    }

    @Transactional
    public AiOrchestratorResponse.PlanView confirm(Long userId, Long planId, String idempotencyKey) {
        requireUser(userId);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw BusinessException.error("idempotencyKey 不能为空");
        }
        AiPlanDO plan = requirePlan(userId, planId);
        if (SUCCEEDED.equals(plan.getStatus())) {
            if (!Objects.equals(plan.getIdempotencyKey(), idempotencyKey)) {
                throw BusinessException.conflict("该计划已经执行，请使用原幂等键重放");
            }
            return replay(plan);
        }
        requireState(plan, WAITING_CONFIRMATION);
        AgentDraft draft = readDraft(plan);
        AiOrchestratorState executingState = state(plan).transition(AiOrchestratorEventType.EXECUTION_STARTED);
        plan.setIdempotencyKey(idempotencyKey.trim());
        plan.setConfirmedAt(LocalDateTime.now());
        plan.setStatus(executingState.name());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        appendEvent(plan, plan.getPreviewOperationId(), AiOrchestratorEventType.EXECUTION_STARTED,
                Map.of("idempotencyKey", idempotencyKey));

        AiOperationResultDTO operation;
        try {
            operation = connectorPort.execute(new OperationExecuteRequest(
                    plan.getPreviewOperationId(), idempotencyKey,
                    contextId(plan), String.valueOf(plan.getContextVersion()), null, null));
        } catch (BusinessException exception) {
            return finishError(plan, exception.getCode() == BusinessException.ResponseCode.CONFLICT
                    ? CONFLICT : FAILED, exception.getCode() == BusinessException.ResponseCode.CONFLICT
                    ? "CONFLICT" : "EXECUTION_FAILED", exception.getMessage());
        } catch (RuntimeException exception) {
            return finishError(plan, FAILED, "EXECUTION_FAILED", safeMessage(exception));
        }

        if (operation == null || !SUCCEEDED.equals(operation.status())) {
            String nextState = operation != null && CONFLICT.equals(operation.status()) ? CONFLICT : FAILED;
            return finishError(plan, nextState,
                    CONFLICT.equals(nextState) ? "CONFLICT" : "EXECUTION_FAILED",
                    operation == null ? "执行结果为空" : operation.message());
        }

        plan.setStatus(VERIFYING);
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        appendEvent(plan, operation.operationId(), AiOrchestratorEventType.EXECUTION_FINISHED,
                Map.of("operationId", operation.operationId()));
        AiOrchestratorResponse.VerificationView verification = verificationService.verify(plan, draft, operation);
        plan.setVerificationJson(write(verification));
        plan.setExecutedAt(LocalDateTime.now());
        if ("SUCCEEDED".equals(verification.status())) {
            plan.setStatus(SUCCEEDED);
            plan.setErrorCode(null);
            plan.setErrorMessage(null);
            appendEvent(plan, operation.operationId(), AiOrchestratorEventType.VERIFICATION_FINISHED,
                    Map.of("status", verification.status()));
        } else {
            plan.setStatus(FAILED);
            plan.setErrorCode(verification.status());
            plan.setErrorMessage(verification.message());
            appendEvent(plan, operation.operationId(), AiOrchestratorEventType.FAILED,
                    Map.of("status", verification.status(), "message", verification.message()));
        }
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        return planView(plan, readContext(plan), draft, readPreview(plan), operation, verification, List.of());
    }

    @Transactional(readOnly = true)
    public AiOrchestratorResponse.ConversationView getConversation(Long userId, Long conversationId) {
        requireUser(userId);
        AiConversationDO conversation = requireConversation(userId, conversationId);
        AiPlanDO plan = planMapper.selectLatestByConversationUser(conversationId, userId);
        return conversationView(conversation, plan == null ? null : planView(plan));
    }

    @Transactional(readOnly = true)
    public List<AiOrchestratorResponse.OperationEventView> listEvents(
            Long userId, Long conversationId, long afterSequence) {
        requireUser(userId);
        requireNonNegativeSequence(afterSequence);
        requireConversation(userId, conversationId);
        return eventMapper.selectAfterSequence(conversationId, userId, afterSequence, 100).stream()
                .map(this::eventView)
                .toList();
    }

    @Transactional(readOnly = true)
    public AiOrchestratorResponse.SessionView getSession(Long userId, Long conversationId, long afterSequence) {
        requireUser(userId);
        requireNonNegativeSequence(afterSequence);
        AiConversationDO conversation = requireConversation(userId, conversationId);
        AiPlanDO plan = planMapper.selectLatestByConversationUser(conversationId, userId);
        List<AiOrchestratorResponse.OperationEventView> events = eventMapper
                .selectAfterSequence(conversationId, userId, afterSequence, 100).stream()
                .map(this::eventView)
                .toList();
        long nextSequence = events.stream()
                .map(AiOrchestratorResponse.OperationEventView::sequenceNo)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(afterSequence) + 1L;
        AiOrchestratorResponse.PlanView latestPlan = plan == null ? null : planView(plan);
        return new AiOrchestratorResponse.SessionView(
                conversation.getId(), conversationView(conversation, latestPlan), latestPlan,
                events, afterSequence, nextSequence);
    }

    private AiOrchestratorResponse.PlanView finishError(AiPlanDO plan, String state,
                                                          String errorCode, String errorMessage) {
        plan.setStatus(state);
        plan.setErrorCode(errorCode);
        plan.setErrorMessage(errorMessage);
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        appendEvent(plan, plan.getPreviewOperationId(),
                CONFLICT.equals(state) ? AiOrchestratorEventType.CONFLICT : AiOrchestratorEventType.FAILED,
                Map.of("code", errorCode, "message", errorMessage == null ? "" : errorMessage));
        return planView(plan);
    }

    private AiOrchestratorResponse.PlanView replay(AiPlanDO plan) {
        AiOperationDO operation = operationMapper.selectById(plan.getPreviewOperationId());
        AiOperationResultDTO result = operation == null ? null : readOperation(operation);
        if (result == null) throw BusinessException.error("幂等操作结果不存在");
        return planView(plan, readContext(plan), readDraft(plan), readPreview(plan), result,
                readVerification(plan), List.of());
    }

    private AiConversationDO requireConversation(Long userId, Long conversationId) {
        if (conversationId == null) throw BusinessException.notFound("AI 会话不存在");
        AiConversationDO conversation = conversationMapper.selectByIdAndUserId(conversationId, userId);
        if (conversation == null) throw BusinessException.notFound("AI 会话不存在");
        return conversation;
    }

    private AiPlanDO requirePlan(Long userId, Long planId) {
        if (planId == null) throw BusinessException.notFound("AI 计划不存在");
        AiPlanDO plan = planMapper.selectForUpdate(planId, userId);
        if (plan == null) throw BusinessException.notFound("AI 计划不存在");
        return plan;
    }

    private void validateContextTarget(AiConversationDO conversation, AiWorkflowContextDTO context,
                                       AiOrchestratorRequest.AppendTurnCommand command) {
        if (context == null || !Objects.equals(context.resourceType(), conversation.getResourceType())
                || !Objects.equals(context.resourceId(), conversation.getResourceId())) {
            throw BusinessException.notFound("目标资源上下文不存在");
        }
        if (context.version() == null) throw BusinessException.conflict("上下文缺少可写版本");
        if (command.contextVersion() != null && !Objects.equals(
                command.contextVersion().intValue(), context.version())) {
            throw BusinessException.conflict("上下文版本已变化，请重新读取");
        }
        resolveNodeId(context, command.nodeId());
    }

    private Long resolveNodeId(AiWorkflowContextDTO context, Long requestedNodeId) {
        Long currentNodeId = context.currentNode() == null ? null : context.currentNode().id();
        if (requestedNodeId != null && !Objects.equals(requestedNodeId, currentNodeId)) {
            throw BusinessException.conflict("指定节点不是当前可写节点");
        }
        if (requestedNodeId == null && currentNodeId == null) {
            throw BusinessException.conflict("当前上下文没有可写节点");
        }
        return requestedNodeId == null ? currentNodeId : requestedNodeId;
    }

    private CommandPreviewRequest commandRequest(AiPlanDO plan, AgentDraft draft) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("requirementId", plan.getResourceId());
        arguments.put("nodeId", plan.getNodeId());
        arguments.put("version", plan.getContextVersion());
        arguments.put("patch", draft.draft().patch());
        return new CommandPreviewRequest(CommandName.REQUIREMENT_ANALYSIS_SAVE, arguments,
                contextId(plan), String.valueOf(plan.getContextVersion()));
    }

    private String contextId(AiPlanDO plan) {
        return plan.getResourceType() + ":" + plan.getResourceId();
    }

    private void appendEvent(AiPlanDO plan, String operationId, AiOrchestratorEventType type,
                             Map<String, Object> detail) {
        eventMapper.appendNext(plan.getId(), plan.getConversationId(), operationId, type.name(), write(detail));
    }

    private AiOrchestratorState state(AiPlanDO plan) {
        try {
            return AiOrchestratorState.valueOf(plan.getStatus());
        } catch (RuntimeException exception) {
            throw BusinessException.error("未知 AI 编排状态: " + plan.getStatus());
        }
    }

    private void requireState(AiPlanDO plan, String expected) {
        if (!Objects.equals(expected, plan.getStatus())) {
            throw BusinessException.conflict("计划当前状态不可执行: " + plan.getStatus());
        }
    }

    private void requireNonNegativeSequence(long afterSequence) {
        if (afterSequence < 0) throw BusinessException.error("事件游标不能为负数");
    }

    private AiOrchestratorResponse.ConversationView conversationView(
            AiConversationDO conversation, AiOrchestratorResponse.PlanView latestPlan) {
        return new AiOrchestratorResponse.ConversationView(conversation.getId(), conversation.getResourceType(),
                conversation.getResourceId(), conversation.getStatus(), latestPlan);
    }

    private AiOrchestratorResponse.PlanView planView(AiPlanDO plan) {
        return planView(plan, readContext(plan), readDraft(plan), readPreview(plan), null,
                readVerification(plan), List.of());
    }

    private AiOrchestratorResponse.PlanView planView(AiPlanDO plan, AiWorkflowContextDTO context,
                                                      AgentDraft draft, AiOperationPreviewDTO preview,
                                                      AiOperationResultDTO operation,
                                                      AiOrchestratorResponse.VerificationView verification,
                                                      List<DraftValidationResult.ValidationError> validationErrors) {
        return new AiOrchestratorResponse.PlanView(plan.getId(), plan.getTurnId(), plan.getIntent(), plan.getStatus(),
                plan.getResourceType(), plan.getResourceId(), plan.getNodeId(), plan.getContextVersion(),
                context, draft, preview, operation, verification, validationErrors,
                plan.getErrorCode(), plan.getErrorMessage());
    }

    private AiOrchestratorResponse.OperationEventView eventView(AiOperationEventDO event) {
        return new AiOrchestratorResponse.OperationEventView(event.getSequenceNo(), event.getEventType(),
                event.getOperationId(), readMap(event.getDetailJson()), event.getCreatedAt());
    }

    private AgentDraft readDraft(AiPlanDO plan) {
        try {
            return objectMapper.readValue(plan.getDraftJson(), AgentDraft.class);
        } catch (Exception exception) {
            throw BusinessException.error("AI 计划草稿格式无效");
        }
    }

    private AiWorkflowContextDTO readContext(AiPlanDO plan) {
        try {
            return objectMapper.readValue(plan.getContextSnapshotJson(), AiWorkflowContextDTO.class);
        } catch (Exception exception) {
            throw BusinessException.error("AI 计划上下文快照格式无效");
        }
    }

    private AiOperationPreviewDTO readPreview(AiPlanDO plan) {
        if (plan.getPreviewOperationId() == null) return null;
        AiOperationDO operation = operationMapper.selectById(plan.getPreviewOperationId());
        if (operation == null || operation.getPreviewJson() == null) return null;
        try {
            return AiOperationPreviewDTO.from(objectMapper.readValue(operation.getPreviewJson(), CommandPreview.class));
        } catch (Exception exception) {
            throw BusinessException.error("AI 预览结果格式无效");
        }
    }

    private AiOrchestratorResponse.VerificationView readVerification(AiPlanDO plan) {
        if (plan.getVerificationJson() == null) return null;
        try {
            return objectMapper.readValue(plan.getVerificationJson(), AiOrchestratorResponse.VerificationView.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private AiOperationResultDTO readOperation(AiOperationDO operation) {
        try {
            com.brad.pms.ai.command.CommandResult result = objectMapper.readValue(
                    operation.getResultJson(), com.brad.pms.ai.command.CommandResult.class);
            return AiOperationResultDTO.from(result);
        } catch (Exception exception) {
            throw BusinessException.error("幂等操作结果格式无效");
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception exception) {
            return Map.of("raw", json);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw BusinessException.error("AI 编排数据序列化失败");
        }
    }

    private String normalizeResourceType(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if (normalized.isBlank()) throw BusinessException.error("resourceType 不能为空");
        return normalized;
    }

    private void requireUser(Long userId) {
        if (userId == null) throw BusinessException.unauthorized("未登录");
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? "AI 操作执行失败" : exception.getMessage();
    }
}
