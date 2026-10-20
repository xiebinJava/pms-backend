package com.brad.pms.ai.orchestrator.service;

import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.OperationExecuteRequest;
import com.brad.pms.ai.connector.context.ContextReadOptions;
import com.brad.pms.ai.orchestrator.api.AiOrchestratorRequest;
import com.brad.pms.ai.orchestrator.api.AiOrchestratorResponse;
import com.brad.pms.ai.orchestrator.adapter.ResourceNodeBinding;
import com.brad.pms.ai.orchestrator.adapter.ResourceNodeBindingResolver;
import com.brad.pms.ai.orchestrator.adapter.ResourceOrchestratorAdapter;
import com.brad.pms.ai.orchestrator.adapter.ResourceOrchestratorAdapterRegistry;
import com.brad.pms.ai.orchestrator.connector.AiConnectorPort;
import com.brad.pms.ai.orchestrator.contract.AgentDraft;
import com.brad.pms.ai.orchestrator.contract.AiAgentDraftValidator;
import com.brad.pms.ai.orchestrator.contract.DraftValidationResult;
import com.brad.pms.ai.orchestrator.contract.StoryNodeContract;
import com.brad.pms.ai.orchestrator.contract.StoryNodeContractResolver;
import com.brad.pms.ai.orchestrator.contract.ResourceNodeContract;
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
import com.brad.pms.ai.orchestrator.provenance.DraftProvenanceDigestService;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.integration.ai.api.AiOperationPreviewDTO;
import com.brad.pms.integration.ai.api.AiOperationResultDTO;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.brad.pms.mapper.AiOperationMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class AiOrchestratorService {

    private static final String ACTIVE = "ACTIVE";
    private static final String PLAN_READY = "PLAN_READY";
    private static final String WAITING_CONFIRMATION = "WAITING_CONFIRMATION";
    private static final String EXECUTING = "EXECUTING";
    private static final String VERIFYING = "VERIFYING";
    private static final String SUCCEEDED = "SUCCEEDED";
    private static final String FAILED = "FAILED";
    private static final String CONFLICT = "CONFLICT";
    private static final String NEEDS_INPUT = "NEEDS_INPUT";
    private static final String STORY_CONTRACT_VERSION = "story-workbench/v1";
    private static final String STORY_INTENT = "STORY_NODE_FIELD_UPDATE";
    private static final String RESOURCE_NODE_CONTRACT_VERSION = "resource-node/v1";
    private static final String RESOURCE_NODE_FIELD_UPDATE_INTENT = "RESOURCE_NODE_FIELD_UPDATE";
    private static final String RESOURCE_NODE_COMPLETE_INTENT = "RESOURCE_NODE_COMPLETE";
    private static final java.util.Set<String> NODE_ID_KEYS = Set.of(
            "nodeId", "contextVersion", "resourceId", "resourceType", "version", "__components");

    private final AiConversationMapper conversationMapper;
    private final AiConversationTurnMapper turnMapper;
    private final AiPlanMapper planMapper;
    private final AiOperationEventMapper eventMapper;
    private final AiOperationMapper operationMapper;
    private final AiConnectorPort connectorPort;
    private final AiAgentDraftValidator draftValidator;
    private final AiOrchestratorVerificationService verificationService;
    private final ObjectMapper objectMapper;
    private final StoryNodeContractResolver storyNodeContractResolver;
    private final ResourceOrchestratorAdapterRegistry resourceAdapterRegistry;
    private final DraftProvenanceDigestService provenanceDigestService;

    @Autowired
    public AiOrchestratorService(AiConversationMapper conversationMapper,
                                 AiConversationTurnMapper turnMapper,
                                 AiPlanMapper planMapper,
                                 AiOperationEventMapper eventMapper,
                                 AiOperationMapper operationMapper,
                                 AiConnectorPort connectorPort,
                                 AiAgentDraftValidator draftValidator,
                                 AiOrchestratorVerificationService verificationService,
                                 ObjectMapper objectMapper,
                                 ResourceOrchestratorAdapterRegistry resourceAdapterRegistry) {
        this(conversationMapper, turnMapper, planMapper, eventMapper, operationMapper, connectorPort,
                draftValidator, verificationService, objectMapper, new StoryNodeContractResolver(),
                resourceAdapterRegistry, new DraftProvenanceDigestService(objectMapper));
    }

    public AiOrchestratorService(AiConversationMapper conversationMapper,
                                 AiConversationTurnMapper turnMapper,
                                 AiPlanMapper planMapper,
                                 AiOperationEventMapper eventMapper,
                                 AiOperationMapper operationMapper,
                                 AiConnectorPort connectorPort,
                                 AiAgentDraftValidator draftValidator,
                                 AiOrchestratorVerificationService verificationService,
                                 ObjectMapper objectMapper,
                                 StoryNodeContractResolver storyNodeContractResolver,
                                 ResourceOrchestratorAdapterRegistry resourceAdapterRegistry,
                                 DraftProvenanceDigestService provenanceDigestService) {
        this.conversationMapper = conversationMapper;
        this.turnMapper = turnMapper;
        this.planMapper = planMapper;
        this.eventMapper = eventMapper;
        this.operationMapper = operationMapper;
        this.connectorPort = connectorPort;
        this.draftValidator = draftValidator;
        this.verificationService = verificationService;
        this.objectMapper = objectMapper;
        this.storyNodeContractResolver = storyNodeContractResolver;
        this.resourceAdapterRegistry = resourceAdapterRegistry;
        this.provenanceDigestService = provenanceDigestService;
    }

    /** Requirement/story-only constructor kept for existing tests; project/topic routing is empty. */
    public AiOrchestratorService(AiConversationMapper conversationMapper,
                          AiConversationTurnMapper turnMapper,
                          AiPlanMapper planMapper,
                          AiOperationEventMapper eventMapper,
                          AiOperationMapper operationMapper,
                          AiConnectorPort connectorPort,
                          AiAgentDraftValidator draftValidator,
                          AiOrchestratorVerificationService verificationService,
                          ObjectMapper objectMapper) {
        this(conversationMapper, turnMapper, planMapper, eventMapper, operationMapper, connectorPort,
                draftValidator, verificationService, objectMapper, new StoryNodeContractResolver(),
                new ResourceOrchestratorAdapterRegistry(List.of()), new DraftProvenanceDigestService(objectMapper));
    }

    @Transactional
    public AiOrchestratorResponse.ConversationView createConversation(
            Long userId, AiOrchestratorRequest.CreateConversationCommand command) {
        requireUser(userId);
        if (command == null || command.resourceType() == null || command.resourceId() == null
                || command.resourceId() <= 0) {
            throw BusinessException.error("资源类型和资源 ID 必须为有效值");
        }
        String resourceType = normalizeResourceType(command.resourceType());
        if (!List.of("requirement", "story", "project", "topic").contains(resourceType)) {
            throw BusinessException.error("当前编排器只支持 requirement、story、project 或 topic 资源");
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

        if ("story".equals(conversation.getResourceType())) {
            return appendStoryTurn(userId, conversation, command);
        }
        if (resourceAdapterRegistry.find(conversation.getResourceType()).isPresent()) {
            return appendResourceNodeTurn(userId, conversation, command);
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

    // appendTurn 已开启事务；私有方法上的 @Transactional 不会被代理触发，避免误导。
    private AiOrchestratorResponse.PlanView appendStoryTurn(
            Long userId, AiConversationDO conversation,
            AiOrchestratorRequest.AppendTurnCommand command) {
        if (!STORY_CONTRACT_VERSION.equals(command.contractVersion())
                || !STORY_CONTRACT_VERSION.equals(command.draft().contractVersion())) {
            throw BusinessException.error("故事节点只支持 " + STORY_CONTRACT_VERSION + " 契约");
        }
        if (command.nodeId() == null || command.nodeId() <= 0) {
            throw BusinessException.error("故事 turn 必须提供正整数 nodeId");
        }
        if (command.contextVersion() == null || command.contextVersion() < 0) {
            throw BusinessException.error("故事 turn 必须提供非负 contextVersion");
        }
        AiPlanDO latestPlan = planMapper.selectLatestByConversationUser(conversation.getId(), userId);
        if (latestPlan != null && !terminalPlan(latestPlan.getStatus())
                && latestPlan.getNodeId() != null && !Objects.equals(latestPlan.getNodeId(), command.nodeId())) {
            throw BusinessException.conflict("CONVERSATION_TARGET_MISMATCH: 当前会话仍在推进另一个故事节点");
        }
        AiWorkflowContextDTO context = connectorPort.readContext(
                conversation.getResourceType(), conversation.getResourceId(),
                new ContextReadOptions(ContextReadOptions.VIEW_FOCUSED, command.nodeId(),
                        "story-node-workbench"));
        validateStoryContextTarget(conversation, context, command);
        StoryNodeContractResolver.Resolution resolution;
        try {
            resolution = storyNodeContractResolver.resolve(context);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.conflict(exception.getMessage());
        }
        StoryNodeContract contract = resolution.contract();
        if (!Objects.equals(contract.nodeId(), command.nodeId())) {
            throw BusinessException.conflict("故事节点上下文与请求 nodeId 不一致");
        }
        AgentDraft validatedDraft = mergePendingStoryPatch(latestPlan, command.draft(), contract);
        DraftValidationResult validation = draftValidator.validateStory(validatedDraft, context, contract);
        if (!validation.valid()) {
            throw BusinessException.error("故事 Agent Draft 校验失败: " + validation.errorCodes());
        }
        List<String> missingFields;
        try {
            missingFields = storyNodeContractResolver.missingFields(context, contract,
                    validatedDraft.draft().patch());
        } catch (IllegalArgumentException exception) {
            throw BusinessException.error("故事节点草稿字段校验失败: " + exception.getMessage());
        }

        AiConversationTurnDO turn = new AiConversationTurnDO();
        turn.setConversationId(conversation.getId());
        turn.setRole("AGENT");
        turn.setMessageText(command.message().trim());
        turn.setRequestId(command.requestId());
        turn.setDraftJson(write(command.draft()));
        turnMapper.insert(turn);
        if (turn.getId() == null) throw BusinessException.error("保存 AI 会话轮次失败");

        AiPlanDO plan = new AiPlanDO();
        plan.setConversationId(conversation.getId());
        plan.setTurnId(turn.getId());
        plan.setIntent(STORY_INTENT);
        plan.setResourceType(conversation.getResourceType());
        plan.setResourceId(conversation.getResourceId());
        plan.setNodeId(command.nodeId());
        plan.setContextVersion(Long.valueOf(context.version()));
        plan.setContextSnapshotJson(write(context));
        plan.setDraftJson(write(validatedDraft));
        plan.setStatus(missingFields.isEmpty() ? PLAN_READY : NEEDS_INPUT);
        if (!missingFields.isEmpty()) {
            plan.setErrorCode("STORY_MISSING_INPUT");
            plan.setErrorMessage("故事节点仍缺少必填字段: " + String.join(", ", missingFields));
        }
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.insert(plan);
        if (plan.getId() == null) throw BusinessException.error("创建 AI 执行计划失败");

        appendEvent(plan, null, AiOrchestratorEventType.CONTEXT_READ,
                Map.of("resourceType", plan.getResourceType(), "resourceId", plan.getResourceId(),
                        "nodeId", plan.getNodeId()));
        appendEvent(plan, null, AiOrchestratorEventType.DRAFT_VALIDATED,
                Map.of("contractVersion", command.draft().contractVersion(), "intent", STORY_INTENT));
        if (missingFields.isEmpty()) {
            appendEvent(plan, null, AiOrchestratorEventType.PLAN_CREATED,
                    Map.of("intent", plan.getIntent(), "nodeId", plan.getNodeId()));
        } else {
            appendEvent(plan, null, AiOrchestratorEventType.INPUT_REQUIRED,
                    Map.of("code", "STORY_MISSING_INPUT", "missingFields", missingFields));
        }
        return planView(plan, context, validatedDraft, null, null, null, validation.errors(),
                ResourceNodeContract.from(contract), missingFields, questions(missingFields));
    }

    /**
     * Routes a project/topic resource-node turn through the adapter registry.
     * The intent derives from the draft: requestedCompletion=true means
     * RESOURCE_NODE_COMPLETE, everything else stays RESOURCE_NODE_FIELD_UPDATE.
     * REJECTED is never persisted as a plan status; completion semantics are
     * exposed only at the progression-run boundary.
     */
    private AiOrchestratorResponse.PlanView appendResourceNodeTurn(
            Long userId, AiConversationDO conversation,
            AiOrchestratorRequest.AppendTurnCommand command) {
        if (!RESOURCE_NODE_CONTRACT_VERSION.equals(command.contractVersion())
                || !RESOURCE_NODE_CONTRACT_VERSION.equals(command.draft().contractVersion())) {
            throw BusinessException.error("项目/专题节点只支持 " + RESOURCE_NODE_CONTRACT_VERSION + " 契约");
        }
        if (command.nodeId() == null || command.nodeId() <= 0) {
            throw BusinessException.error("资源节点 turn 必须提供正整数 nodeId");
        }
        AiPlanDO latestPlan = planMapper.selectLatestByConversationUser(conversation.getId(), userId);
        if (latestPlan != null && !terminalPlan(latestPlan.getStatus())
                && latestPlan.getNodeId() != null && !Objects.equals(latestPlan.getNodeId(), command.nodeId())) {
            throw BusinessException.conflict("CONVERSATION_TARGET_MISMATCH: 当前会话仍在推进另一个资源节点");
        }

        ResourceOrchestratorAdapter adapter = resourceAdapterRegistry.require(conversation.getResourceType());
        AiWorkflowContextDTO context = connectorPort.readContext(
                conversation.getResourceType(), conversation.getResourceId(),
                new ContextReadOptions(ContextReadOptions.VIEW_FOCUSED, command.nodeId(),
                        command.componentKey()));
        validateResourceNodeContextTarget(conversation, context);
        ResourceNodeBinding binding = adapter.resolveBinding(
                context, command.nodeId(), command.componentKey(), command.nodeVersion());

        String intent = Boolean.TRUE.equals(command.draft().draft() == null ? null
                : command.draft().draft().requestedCompletion())
                ? RESOURCE_NODE_COMPLETE_INTENT : RESOURCE_NODE_FIELD_UPDATE_INTENT;
        AgentDraft validatedDraft = mergePendingResourceNodePatch(latestPlan, command.draft(), binding);
        DraftValidationResult validation = adapter.validateDraft(validatedDraft, context, binding);
        if (!validation.valid()) {
            throw BusinessException.error("资源节点 Agent Draft 校验失败: " + validation.errorCodes());
        }
        List<String> missingFields = resourceNodeMissingFields(binding, validatedDraft.draft().patch());

        AiConversationTurnDO turn = new AiConversationTurnDO();
        turn.setConversationId(conversation.getId());
        turn.setRole("AGENT");
        turn.setMessageText(command.message().trim());
        turn.setRequestId(command.requestId());
        turn.setDraftJson(write(command.draft()));
        turnMapper.insert(turn);
        if (turn.getId() == null) throw BusinessException.error("保存 AI 会话轮次失败");

        AiPlanDO plan = new AiPlanDO();
        plan.setConversationId(conversation.getId());
        plan.setTurnId(turn.getId());
        plan.setIntent(intent);
        plan.setResourceType(conversation.getResourceType());
        plan.setResourceId(conversation.getResourceId());
        plan.setNodeId(binding.nodeId());
        plan.setNodeVersion(binding.nodeVersion());
        plan.setComponentKey(binding.componentKey());
        plan.setContextVersion(binding.contextVersion());
        plan.setContextSnapshotJson(write(context));
        plan.setDraftJson(write(validatedDraft));
        plan.setStatus(missingFields.isEmpty() ? PLAN_READY : NEEDS_INPUT);
        if (!missingFields.isEmpty()) {
            plan.setErrorCode("RESOURCE_NODE_MISSING_INPUT");
            plan.setErrorMessage("资源节点仍缺少必填字段: " + String.join(", ", missingFields));
        }
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.insert(plan);
        if (plan.getId() == null) throw BusinessException.error("创建 AI 执行计划失败");

        appendEvent(plan, null, AiOrchestratorEventType.CONTEXT_READ,
                Map.of("resourceType", plan.getResourceType(), "resourceId", plan.getResourceId(),
                        "nodeId", plan.getNodeId(), "componentKey", plan.getComponentKey()));
        appendEvent(plan, null, AiOrchestratorEventType.DRAFT_VALIDATED,
                Map.of("contractVersion", command.draft().contractVersion(), "intent", intent));
        if (missingFields.isEmpty()) {
            appendEvent(plan, null, AiOrchestratorEventType.PLAN_CREATED,
                    Map.of("intent", plan.getIntent(), "nodeId", plan.getNodeId()));
        } else {
            appendEvent(plan, null, AiOrchestratorEventType.INPUT_REQUIRED,
                    Map.of("code", "RESOURCE_NODE_MISSING_INPUT", "missingFields", missingFields));
        }
        return planView(plan, context, validatedDraft, null, null, null, validation.errors(),
                adapter.nodeContract(binding), missingFields, resourceNodeQuestions(missingFields));
    }

    private void validateResourceNodeContextTarget(AiConversationDO conversation,
                                                   AiWorkflowContextDTO context) {
        if (context == null || !Objects.equals(context.resourceType(), conversation.getResourceType())
                || !Objects.equals(context.resourceId(), conversation.getResourceId())) {
            throw BusinessException.notFound("资源节点上下文不存在");
        }
        if (context.currentNode() == null || context.currentNode().id() == null) {
            throw BusinessException.error("CAPABILITY_UNAVAILABLE: 上下文没有当前节点");
        }
    }

    /**
     * NEEDS_INPUT 续轮语义 for resource nodes: only patch entries still inside
     * the current saveContract writable set are carried into the new Plan; the
     * previous Plan JSON and status remain unchanged.
     */
    private AgentDraft mergePendingResourceNodePatch(AiPlanDO latestPlan, AgentDraft submitted,
                                                     ResourceNodeBinding binding) {
        if (latestPlan == null || !NEEDS_INPUT.equals(latestPlan.getStatus())
                || !Objects.equals(latestPlan.getNodeId(), binding.nodeId())
                || latestPlan.getDraftJson() == null) {
            return submitted;
        }
        AgentDraft previous = readDraft(latestPlan);
        if (!RESOURCE_NODE_CONTRACT_VERSION.equals(previous.contractVersion())
                || previous.draft() == null || submitted.draft() == null) {
            return submitted;
        }
        Map<String, Object> previousPatch = previous.draft().patch() == null ? Map.of() : previous.draft().patch();
        Set<String> writable = Set.copyOf(binding.writableFields());
        Set<String> protectedFields = Set.copyOf(binding.protectedFields());
        Map<String, Object> merged = submitted.draft().patch() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(submitted.draft().patch());
        for (Map.Entry<String, Object> entry : previousPatch.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || value == null || protectedFields.contains(key)
                    || ResourceNodeBinding.NODE_POSITIONING_KEYS.contains(key)
                    || !writable.contains(key)
                    || (value instanceof String text && text.isBlank())
                    || carriesNoValue(value)) {
                continue;
            }
            merged.putIfAbsent(key, value);
        }
        return new AgentDraft(submitted.contractVersion(), new AgentDraft.DraftPayload(
                submitted.draft().facts(), submitted.draft().inferences(), submitted.draft().assumptions(),
                submitted.draft().unresolvedDecisions(), merged,
                submitted.draft().requestedCompletion(), submitted.draft().fieldSources()));
    }

    private List<String> resourceNodeMissingFields(ResourceNodeBinding binding, Map<String, Object> patch) {
        List<String> required = ResourceNodeBindingResolver.strings(binding.saveContract().get("requiredFields"));
        if (required.isEmpty()) return List.of();
        Map<String, Object> values = patch == null ? Map.of() : patch;
        return required.stream()
                .filter(field -> {
                    Object value = values.get(field);
                    return value == null || (value instanceof String text && text.isBlank())
                            || carriesNoValue(value);
                })
                .toList();
    }

    private List<String> resourceNodeQuestions(List<String> missingFields) {
        return missingFields.stream().map(field -> "请补充必填字段：" + field).toList();
    }

    /**
     * NEEDS_INPUT 续轮语义：合并前一个待补充 Plan 的有效 patch 与本轮 patch，原 Plan 保持不可变。
     * 只有仍然属于当前节点可写契约的字段才会被带入，防止旧草稿绕过新的字段白名单。
     */
    private AgentDraft mergePendingStoryPatch(AiPlanDO latestPlan, AgentDraft submitted,
                                              StoryNodeContract contract) {
        if (latestPlan == null || !NEEDS_INPUT.equals(latestPlan.getStatus())
                || !Objects.equals(latestPlan.getNodeId(), contract.nodeId())
                || latestPlan.getDraftJson() == null) {
            return submitted;
        }
        AgentDraft previous = readDraft(latestPlan);
        if (!STORY_CONTRACT_VERSION.equals(previous.contractVersion())
                || previous.draft() == null || submitted.draft() == null) {
            return submitted;
        }
        Map<String, Object> previousPatch = previous.draft().patch() == null ? Map.of() : previous.draft().patch();
        Set<String> writable = Set.copyOf(contract.writableFields());
        Set<String> protectedFields = Set.copyOf(contract.protectedFields());
        Map<String, Object> merged = submitted.draft().patch() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(submitted.draft().patch());
        for (Map.Entry<String, Object> entry : previousPatch.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || value == null || protectedFields.contains(key) || NODE_ID_KEYS.contains(key)
                    || !writable.contains(key)
                    || (value instanceof String text && text.isBlank())
                    || carriesNoValue(value)) {
                continue;
            }
            merged.putIfAbsent(key, value);
        }
        return new AgentDraft(submitted.contractVersion(), new AgentDraft.DraftPayload(
                submitted.draft().facts(), submitted.draft().inferences(), submitted.draft().assumptions(),
                submitted.draft().unresolvedDecisions(), merged));
    }

    /** 空集合与空对象对 merge 是"不修改"语义，不能携带进合并结果覆盖已保存内容。 */
    private boolean carriesNoValue(Object value) {
        if (value instanceof Collection<?> collection) return collection.isEmpty();
        if (value instanceof Map<?, ?> map) return map.isEmpty();
        return false;
    }

    @Transactional
    public AiOrchestratorResponse.PlanView preview(Long userId, Long planId) {
        requireUser(userId);
        AiPlanDO plan = requirePlan(userId, planId);
        requireState(plan, PLAN_READY);
        AgentDraft draft = readDraft(plan);
        CommandPreviewRequest request = commandRequest(plan, draft);
        AiOperationPreviewDTO preview = connectorPort.preview(request);
        if (isResourceNodeIntent(plan.getIntent())) {
            provenanceDigestService.verifyPreviewBinding(
                    preview == null ? null : preview.operationId(), preview, request.provenance());
        }

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
        return storyPlanView(plan, draft, preview, null, null, List.of());
    }

    @Transactional
    public AiOrchestratorResponse.PlanView confirm(Long userId, Long planId, String idempotencyKey) {
        requireUser(userId);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw BusinessException.error("idempotencyKey 不能为空");
        }
        String normalizedIdempotencyKey = idempotencyKey.trim();
        AiPlanDO plan = requirePlan(userId, planId);
        if (SUCCEEDED.equals(plan.getStatus())) {
            if (!Objects.equals(plan.getIdempotencyKey(), normalizedIdempotencyKey)) {
                throw BusinessException.conflict("该计划已经执行，请使用原幂等键重放");
            }
            return replay(plan);
        }
        if ("WRITE_RESULT_UNKNOWN".equals(plan.getStatus())) {
            if (!Objects.equals(plan.getIdempotencyKey(), normalizedIdempotencyKey)) {
                throw BusinessException.conflict("回读未知结果只能使用原幂等键重试");
            }
            return retryReadback(plan);
        }
        if (EXECUTING.equals(plan.getStatus()) || VERIFYING.equals(plan.getStatus())) {
            if (!Objects.equals(plan.getIdempotencyKey(), normalizedIdempotencyKey)) {
                throw BusinessException.conflict("进行中的计划只能使用原幂等键恢复");
            }
            return recoverInFlight(plan, normalizedIdempotencyKey);
        }
        requireState(plan, WAITING_CONFIRMATION);
        if (plan.getPreviewOperationId() == null || plan.getPreviewOperationId().isBlank()) {
            throw BusinessException.conflict("预览操作不存在，不能确认");
        }
        return executeAndVerify(plan, normalizedIdempotencyKey, true);
    }

    private AiOrchestratorResponse.PlanView executeAndVerify(AiPlanDO plan,
                                                              String idempotencyKey,
                                                              boolean startExecution) {
        AgentDraft draft = readDraft(plan);
        if (startExecution) {
            AiOrchestratorState executingState = state(plan).transition(AiOrchestratorEventType.EXECUTION_STARTED);
            plan.setIdempotencyKey(idempotencyKey);
            plan.setConfirmedAt(LocalDateTime.now());
            plan.setStatus(executingState.name());
            plan.setUpdatedAt(LocalDateTime.now());
            planMapper.updateById(plan);
            appendEvent(plan, plan.getPreviewOperationId(), AiOrchestratorEventType.EXECUTION_STARTED,
                    Map.of("idempotencyKey", idempotencyKey));
        }

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
        if (!Objects.equals(plan.getPreviewOperationId(), operation.operationId())) {
            return finishError(plan, CONFLICT, "OPERATION_MISMATCH", "执行结果与预览操作不匹配");
        }

        plan.setStatus(VERIFYING);
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        appendEvent(plan, operation.operationId(), AiOrchestratorEventType.EXECUTION_FINISHED,
                Map.of("operationId", operation.operationId()));
        AiOrchestratorResponse.VerificationView verification = verificationService.verify(plan, draft, operation);
        return finishVerification(plan, draft, operation, verification, true);
    }

    private AiOrchestratorResponse.PlanView recoverInFlight(AiPlanDO plan, String idempotencyKey) {
        if (plan.getPreviewOperationId() == null || plan.getPreviewOperationId().isBlank()) {
            throw BusinessException.conflict("进行中的计划缺少原操作");
        }
        AiOperationDO storedOperation = operationMapper.selectById(plan.getPreviewOperationId());
        if (storedOperation == null) {
            throw BusinessException.conflict("进行中的操作结果不存在，暂不能恢复");
        }
        if (SUCCEEDED.equals(storedOperation.getStatus())) {
            AgentDraft draft = readDraft(plan);
            AiOperationResultDTO operation = readBoundOperation(plan, storedOperation);
            if (EXECUTING.equals(plan.getStatus())) {
                plan.setStatus(state(plan).transition(AiOrchestratorEventType.EXECUTION_FINISHED).name());
            }
            if (eventMapper.countByPlanAndType(plan.getId(), AiOrchestratorEventType.EXECUTION_FINISHED.name()) == 0) {
                appendEvent(plan, operation.operationId(), AiOrchestratorEventType.EXECUTION_FINISHED,
                        Map.of("operationId", operation.operationId(), "recovered", true));
            }
            plan.setUpdatedAt(LocalDateTime.now());
            planMapper.updateById(plan);
            AiOrchestratorResponse.VerificationView verification = verificationService.verify(plan, draft, operation);
            return finishVerification(plan, draft, operation, verification, true);
        }
        if (EXECUTING.equals(plan.getStatus()) && "PREVIEW".equals(storedOperation.getStatus())) {
            return executeAndVerify(plan, idempotencyKey, false);
        }
        throw BusinessException.conflict("进行中的操作当前不可恢复，请查看会话事件");
    }

    private AiOrchestratorResponse.PlanView retryReadback(AiPlanDO plan) {
        if (plan.getPreviewOperationId() == null || plan.getPreviewOperationId().isBlank()) {
            throw BusinessException.conflict("回读未知结果缺少原操作");
        }
        AiOperationDO storedOperation = operationMapper.selectById(plan.getPreviewOperationId());
        if (storedOperation == null || !SUCCEEDED.equals(storedOperation.getStatus())
                || storedOperation.getResultJson() == null) {
            throw BusinessException.conflict("原操作结果不存在，暂不能重试回读");
        }
        AgentDraft draft = readDraft(plan);
        AiOperationResultDTO operation = readBoundOperation(plan, storedOperation);
        AiOrchestratorResponse.VerificationView verification = verificationService.verify(plan, draft, operation);
        return finishVerification(plan, draft, operation, verification, false);
    }

    private AiOrchestratorResponse.PlanView finishVerification(
            AiPlanDO plan,
            AgentDraft draft,
            AiOperationResultDTO operation,
            AiOrchestratorResponse.VerificationView verification,
            boolean executionCompleted) {
        plan.setVerificationJson(write(verification));
        if (executionCompleted) plan.setExecutedAt(LocalDateTime.now());
        if ("SUCCEEDED".equals(verification.status())) {
            plan.setStatus(state(plan).transition(AiOrchestratorEventType.VERIFICATION_FINISHED).name());
            plan.setErrorCode(null);
            plan.setErrorMessage(null);
            appendEvent(plan, operation.operationId(), AiOrchestratorEventType.VERIFICATION_FINISHED,
                    Map.of("status", verification.status()));
        } else if ("WRITE_RESULT_UNKNOWN".equals(verification.status())) {
            plan.setStatus(state(plan).transition(AiOrchestratorEventType.READBACK_UNKNOWN).name());
            plan.setErrorCode("WRITE_RESULT_UNKNOWN");
            plan.setErrorMessage(verification.message());
            appendEvent(plan, operation.operationId(), AiOrchestratorEventType.READBACK_UNKNOWN,
                    Map.of("status", verification.status(), "message", verification.message()));
        } else {
            plan.setStatus(state(plan).transition(AiOrchestratorEventType.FAILED).name());
            plan.setErrorCode(verification.status());
            plan.setErrorMessage(verification.message());
            appendEvent(plan, operation.operationId(), AiOrchestratorEventType.FAILED,
                    Map.of("status", verification.status(), "message", verification.message()));
        }
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        return storyPlanView(plan, draft, readPreview(plan), operation, verification, List.of());
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
        AiOperationResultDTO result = operation == null ? null : readBoundOperation(plan, operation);
        if (result == null) throw BusinessException.error("幂等操作结果不存在");
        return storyPlanView(plan, readDraft(plan), readPreview(plan), result,
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

    private void validateStoryContextTarget(AiConversationDO conversation,
                                            AiWorkflowContextDTO context,
                                            AiOrchestratorRequest.AppendTurnCommand command) {
        if (context == null || !Objects.equals(context.resourceType(), conversation.getResourceType())
                || !Objects.equals(context.resourceId(), conversation.getResourceId())) {
            throw BusinessException.notFound("故事上下文不存在");
        }
        if (context.version() == null || !Objects.equals(command.contextVersion().intValue(), context.version())) {
            throw BusinessException.conflict("故事上下文版本已变化，请重新读取");
        }
        if (context.currentNode() == null || !Objects.equals(context.currentNode().id(), command.nodeId())) {
            throw BusinessException.conflict("指定节点不是当前可写节点");
        }
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
        if (isResourceNodeIntent(plan.getIntent())) {
            Map<String, Object> provenance = resourceProvenance(plan, draft);
            boolean completion = RESOURCE_NODE_COMPLETE_INTENT.equals(plan.getIntent());
            if ("project".equals(plan.getResourceType())) {
                arguments.put("projectId", plan.getResourceId());
                arguments.put("nodeId", plan.getNodeId());
                arguments.put("workbench", plan.getComponentKey());
                arguments.put("fields", draft.draft().patch());
                return new CommandPreviewRequest(completion
                        ? CommandName.NODE_COMPLETE_WITH_DATA : CommandName.NODE_FIELD_UPDATE,
                        arguments, contextId(plan), String.valueOf(plan.getContextVersion()),
                        null, null, provenance);
            }
            arguments.put("itemType", "TOPIC");
            arguments.put("itemId", plan.getResourceId());
            arguments.put("nodeId", plan.getNodeId());
            arguments.put("version", plan.getNodeVersion());
            arguments.put("fieldValues", draft.draft().patch());
            return new CommandPreviewRequest(completion
                    ? CommandName.DEVELOPMENT_ITEM_NODE_COMPLETE_WITH_DATA
                    : CommandName.DEVELOPMENT_ITEM_NODE_FIELD_UPDATE,
                    arguments, contextId(plan), String.valueOf(plan.getContextVersion()),
                    null, null, provenance);
        }
        if (STORY_INTENT.equals(plan.getIntent())) {
            arguments.put("itemType", "STORY");
            arguments.put("itemId", plan.getResourceId());
            arguments.put("nodeId", plan.getNodeId());
            arguments.put("version", plan.getContextVersion());
            arguments.put("fieldValues", Map.of("__components",
                    Map.of("story-node-workbench", draft.draft().patch())));
            return new CommandPreviewRequest(CommandName.DEVELOPMENT_ITEM_NODE_FIELD_UPDATE, arguments,
                    contextId(plan), String.valueOf(plan.getContextVersion()));
        }
        arguments.put("requirementId", plan.getResourceId());
        arguments.put("nodeId", plan.getNodeId());
        arguments.put("version", plan.getContextVersion());
        arguments.put("patch", draft.draft().patch());
        return new CommandPreviewRequest(CommandName.REQUIREMENT_ANALYSIS_SAVE, arguments,
                contextId(plan), String.valueOf(plan.getContextVersion()));
    }

    private Map<String, Object> resourceProvenance(AiPlanDO plan, AgentDraft draft) {
        if (draft == null || draft.draft() == null || draft.draft().fieldSources() == null) {
            throw BusinessException.error("PROVENANCE_INVALID: 资源节点草稿缺少 fieldSources");
        }
        AiWorkflowContextDTO context = readContext(plan);
        ResourceOrchestratorAdapter adapter = resourceAdapterRegistry.require(plan.getResourceType());
        ResourceNodeBinding binding = adapter.resolveBinding(
                context, plan.getNodeId(), plan.getComponentKey(), plan.getNodeVersion());
        AgentDraft.DraftPayload payload = draft.draft();
        DraftProvenanceDigestService.DigestInput input = new DraftProvenanceDigestService.DigestInput(
                DraftProvenanceDigestService.CONTRACT_VERSION,
                plan.getResourceType(), plan.getResourceId(), binding.nodeId(), binding.componentKey(),
                plan.getIntent(), Boolean.TRUE.equals(payload.requestedCompletion()),
                DraftProvenanceDigestService.versionSnapshot(binding),
                DraftProvenanceDigestService.items(payload.facts()),
                DraftProvenanceDigestService.items(payload.inferences()),
                DraftProvenanceDigestService.items(payload.assumptions()),
                DraftProvenanceDigestService.items(payload.unresolvedDecisions()),
                payload.patch(), payload.fieldSources());
        return provenanceDigestService.buildProvenance(input);
    }

    private boolean isResourceNodeIntent(String intent) {
        return RESOURCE_NODE_FIELD_UPDATE_INTENT.equals(intent)
                || RESOURCE_NODE_COMPLETE_INTENT.equals(intent);
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

    private boolean terminalPlan(String status) {
        return SUCCEEDED.equals(status) || FAILED.equals(status) || CONFLICT.equals(status)
                || "CANCELLED".equals(status);
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
        return storyPlanView(plan, readDraft(plan), readPreview(plan), readStoredOperation(plan),
                readVerification(plan), List.of());
    }

    private AiOrchestratorResponse.PlanView storyPlanView(AiPlanDO plan,
                                                           AgentDraft draft,
                                                           AiOperationPreviewDTO preview,
                                                           AiOperationResultDTO operation,
                                                           AiOrchestratorResponse.VerificationView verification,
                                                           List<DraftValidationResult.ValidationError> validationErrors) {
        AiWorkflowContextDTO context = readContext(plan);
        if (!STORY_INTENT.equals(plan.getIntent())) {
            if (isResourceNodeIntent(plan.getIntent())) {
                return resourcePlanView(plan, context, draft, preview, operation, verification, validationErrors);
            }
            return planView(plan, context, draft, preview, operation, verification, validationErrors);
        }
        try {
            StoryNodeContractResolver.Resolution resolution = storyNodeContractResolver.resolve(context);
            List<String> missing = storyNodeContractResolver.missingFields(context, resolution.contract(),
                    draft == null || draft.draft() == null ? Map.of() : draft.draft().patch());
            return planView(plan, context, draft, preview, operation, verification, validationErrors,
                    ResourceNodeContract.from(resolution.contract()), missing, questions(missing));
        } catch (RuntimeException exception) {
            return planView(plan, context, draft, preview, operation, verification, validationErrors);
        }
    }

    private AiOrchestratorResponse.PlanView resourcePlanView(
            AiPlanDO plan,
            AiWorkflowContextDTO context,
            AgentDraft draft,
            AiOperationPreviewDTO preview,
            AiOperationResultDTO operation,
            AiOrchestratorResponse.VerificationView verification,
            List<DraftValidationResult.ValidationError> validationErrors) {
        try {
            ResourceOrchestratorAdapter adapter = resourceAdapterRegistry.require(plan.getResourceType());
            ResourceNodeBinding binding = adapter.resolveBinding(
                    context, plan.getNodeId(), plan.getComponentKey(), plan.getNodeVersion());
            Map<String, Object> patch = draft == null || draft.draft() == null
                    ? Map.of() : draft.draft().patch();
            List<String> missingFields = resourceNodeMissingFields(binding, patch);
            return planView(plan, context, draft, preview, operation, verification, validationErrors,
                    adapter.nodeContract(binding), missingFields, resourceNodeQuestions(missingFields));
        } catch (RuntimeException exception) {
            return planView(plan, context, draft, preview, operation, verification, validationErrors);
        }
    }

    private AiOrchestratorResponse.PlanView planView(AiPlanDO plan, AiWorkflowContextDTO context,
                                                      AgentDraft draft, AiOperationPreviewDTO preview,
                                                      AiOperationResultDTO operation,
                                                      AiOrchestratorResponse.VerificationView verification,
                                                      List<DraftValidationResult.ValidationError> validationErrors) {
        return planView(plan, context, draft, preview, operation, verification, validationErrors,
                null, List.of(), List.of());
    }

    private AiOrchestratorResponse.PlanView planView(AiPlanDO plan, AiWorkflowContextDTO context,
                                                      AgentDraft draft, AiOperationPreviewDTO preview,
                                                      AiOperationResultDTO operation,
                                                      AiOrchestratorResponse.VerificationView verification,
                                                      List<DraftValidationResult.ValidationError> validationErrors,
                                                      ResourceNodeContract nodeContract,
                                                      List<String> missingFields,
                                                      List<String> questions) {
        return new AiOrchestratorResponse.PlanView(plan.getId(), plan.getTurnId(), plan.getIntent(), plan.getStatus(),
                plan.getResourceType(), plan.getResourceId(), plan.getNodeId(), plan.getContextVersion(),
                context, draft, preview, operation, verification, validationErrors,
                plan.getErrorCode(), plan.getErrorMessage(), nodeContract, missingFields, questions,
                plan.getIdempotencyKey(), plan.getConfirmedAt(), plan.getExecutedAt(),
                plan.getNodeVersion(), plan.getComponentKey());
    }

    private List<String> questions(List<String> missingFields) {
        return missingFields.stream().map(field -> switch (field) {
            case "implementationNote" -> "请补充实现说明：本节点具体完成了什么？";
            case "selfTestResult" -> "请补充自测结果：验证了哪些场景，结果如何？";
            default -> "请补充必填字段：" + field;
        }).toList();
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

    private AiOperationResultDTO readStoredOperation(AiPlanDO plan) {
        if (plan.getPreviewOperationId() == null || plan.getPreviewOperationId().isBlank()) return null;
        AiOperationDO operation = operationMapper.selectById(plan.getPreviewOperationId());
        if (operation == null || operation.getResultJson() == null) return null;
        try {
            return readBoundOperation(plan, operation);
        } catch (BusinessException exception) {
            return null;
        }
    }

    private AiOperationResultDTO readBoundOperation(AiPlanDO plan, AiOperationDO operation) {
        if (operation == null || !Objects.equals(plan.getPreviewOperationId(), operation.getId())) {
            throw BusinessException.conflict("计划与操作结果不匹配");
        }
        if (!SUCCEEDED.equals(operation.getStatus()) || operation.getResultJson() == null) {
            throw BusinessException.conflict("操作结果尚未成功持久化");
        }
        if (plan.getIdempotencyKey() == null
                || !Objects.equals(plan.getIdempotencyKey(), operation.getIdempotencyKey())) {
            throw BusinessException.conflict("计划与操作幂等键不匹配");
        }
        AiOperationResultDTO result = readOperation(operation);
        if (!Objects.equals(plan.getPreviewOperationId(), result.operationId())) {
            throw BusinessException.conflict("计划与操作结果 ID 不匹配");
        }
        return result;
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
