package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.RequirementReceivingAnalysisActionCmd;
import com.brad.pms.dto.request.RequirementReceivingAnalysisSaveCmd;
import com.brad.pms.dto.response.RequirementReceivingAnalysisDTO;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import com.brad.pms.entity.DevelopmentItemWorkflowNodeDO;
import com.brad.pms.entity.RequirementDO;
import com.brad.pms.mapper.DevelopmentItemWorkflowMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowNodeMapper;
import com.brad.pms.mapper.RequirementMapper;
import com.brad.pms.workflow.RequirementReceivingAnalysisConfig;
import com.brad.pms.workflow.RequirementReceivingAnalysisPolicy;
import com.brad.pms.workflow.RequirementReceivingAnalysisState;
import com.brad.pms.workflow.WorkflowComponentKey;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import com.brad.pms.workflow.WorkflowTemplateDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Application service for the requirement receiving and analysis runtime component. */
@Service
@RequiredArgsConstructor
public class RequirementReceivingAnalysisService {
    private static final int NODE_LOCKED = 0;
    private static final int NODE_ACTIVE = 1;
    private static final int NODE_COMPLETED = 2;

    private static final String COMPONENTS_KEY = "__components";

    private final RequirementMapper requirementMapper;
    private final DevelopmentItemWorkflowMapper workflowMapper;
    private final DevelopmentItemWorkflowNodeMapper nodeMapper;
    private final WorkflowTemplateService workflowTemplateService;
    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    public RequirementReceivingAnalysisDTO get(Long requirementId, Long nodeId) {
        RequirementDO requirement = requireRequirement(requirementId, false);
        DevelopmentItemWorkflowDO workflow = requireWorkflow(requirementId, false);
        DevelopmentItemWorkflowNodeDO node = requireNode(workflow.getId(), nodeId);
        ComponentContext context = componentContext(workflow, node);
        return toDTO(requirementId, workflow, node, context.config(), readState(node));
    }

    @Transactional
    public RequirementReceivingAnalysisDTO save(Long requirementId, Long nodeId,
                                                RequirementReceivingAnalysisSaveCmd cmd) {
        if (cmd == null || cmd.getState() == null || !cmd.getState().isObject()) {
            throw BusinessException.error("需求接收分析内容必须是对象");
        }
        requireRequirement(requirementId, false);
        DevelopmentItemWorkflowDO workflow = requireWorkflow(requirementId, true);
        requireOpenWorkflow(workflow);
        DevelopmentItemWorkflowNodeDO node = requireNode(workflow.getId(), nodeId);
        requireEditableNode(node);
        requireExpectedVersion(node.getVersion(), cmd.getVersion());
        ComponentContext context = componentContext(workflow, node);
        RequirementReceivingAnalysisState state = parseState(cmd.getState());
        validateState(state, context.config(), false);
        if (state.category() == RequirementReceivingAnalysisState.Category.NON_FUNCTIONAL) {
            clearRequirementSystem(requirementId);
        }
        persistState(node, state);
        if (nodeMapper.updateById(node) != 1) {
            throw BusinessException.conflict("流程节点已被其他人修改，请刷新后重试");
        }
        operationLogService.record(AuditEvent.success(AuditAction.REQUIREMENT_RECEIVING_ANALYSIS_SAVED.name(),
                AuditResourceType.REQUIREMENT.name(), requirementId, null, null, null, state));
        return toDTO(requirementId, workflow, node, context.config(), state);
    }

    @Transactional
    public RequirementReceivingAnalysisDTO reject(Long requirementId, Long nodeId,
                                                  RequirementReceivingAnalysisActionCmd cmd) {
        String reason = requireReason(cmd == null ? null : cmd.getReason(), "驳回原因不能为空");
        RequirementDO requirement = requireRequirement(requirementId, true);
        if (!"ACTIVE".equals(requirement.getStatus())) {
            throw BusinessException.conflict("只有进行中的需求可以驳回");
        }
        DevelopmentItemWorkflowDO workflow = requireWorkflow(requirementId, true);
        requireOpenWorkflow(workflow);
        DevelopmentItemWorkflowNodeDO node = requireNode(workflow.getId(), nodeId);
        requireEditableNode(node);
        if (!Objects.equals(node.getStatus(), NODE_ACTIVE)) {
            throw BusinessException.conflict("只有进行中的需求接收节点可以驳回");
        }
        ComponentContext context = componentContext(workflow, node);
        RequirementReceivingAnalysisState current = readState(node);
        RequirementReceivingAnalysisState state = withDecision(current,
                RequirementReceivingAnalysisState.Decision.REJECT, reason);
        validateState(state, context.config(), true);
        persistState(node, state);
        if (nodeMapper.updateById(node) != 1) {
            throw BusinessException.conflict("流程节点已被其他人修改，请重试");
        }
        requirement.setStatus("REJECTED");
        if (requirementMapper.updateById(requirement) != 1) {
            throw BusinessException.conflict("需求已被其他人修改，请重试");
        }
        workflow.setTerminalStatus("REJECTED");
        if (workflowMapper.updateById(workflow) != 1) {
            throw BusinessException.conflict("需求流程已被其他人修改，请重试");
        }
        operationLogService.record(AuditEvent.success(AuditAction.REQUIREMENT_RECEIVING_REJECTED.name(),
                AuditResourceType.REQUIREMENT.name(), requirementId, null, reason, null, state));
        return toDTO(requirementId, workflow, node, context.config(), state);
    }

    @Transactional
    public RequirementReceivingAnalysisDTO reopen(Long requirementId, RequirementReceivingAnalysisActionCmd cmd) {
        String reason = requireReason(cmd == null ? null : cmd.getReason(), "重开原因不能为空");
        RequirementDO requirement = requireRequirement(requirementId, true);
        DevelopmentItemWorkflowDO workflow = requireWorkflow(requirementId, true);
        if (!"REJECTED".equals(requirement.getStatus()) || !"REJECTED".equals(workflow.getTerminalStatus())) {
            throw BusinessException.conflict("只有已驳回的需求可以重开");
        }
        WorkflowTemplateDefinition definition = workflowTemplateService.getDefinition(workflow.getTemplateVersionId());
        WorkflowNodeDefinition receivingDefinition = findReceivingDefinition(definition);
        List<DevelopmentItemWorkflowNodeDO> nodes = nodeMapper.selectByWorkflowIdsForUpdate(List.of(workflow.getId()));
        DevelopmentItemWorkflowNodeDO receivingNode = nodes.stream()
                .filter(node -> receivingDefinition != null && Objects.equals(node.getNodeKey(), receivingDefinition.key()))
                .findFirst().orElse(null);
        if (receivingNode == null) throw BusinessException.conflict("需求流程未找到需求接收节点");
        for (DevelopmentItemWorkflowNodeDO node : nodes) {
            int nextStatus = node.getSort() < receivingNode.getSort() ? NODE_COMPLETED
                    : Objects.equals(node.getId(), receivingNode.getId()) ? NODE_ACTIVE : NODE_LOCKED;
            if (!Objects.equals(node.getStatus(), nextStatus)) {
                node.setStatus(nextStatus);
                if (nodeMapper.updateById(node) != 1) {
                    throw BusinessException.conflict("需求流程节点已被其他人修改，请重试");
                }
            }
        }
        requirement.setStatus("ACTIVE");
        if (requirementMapper.updateById(requirement) != 1) {
            throw BusinessException.conflict("需求已被其他人修改，请重试");
        }
        workflow.setTerminalStatus(null);
        if (workflowMapper.updateById(workflow) != 1) {
            throw BusinessException.conflict("需求流程已被其他人修改，请重试");
        }
        ComponentContext context = componentContext(workflow, receivingNode);
        operationLogService.record(AuditEvent.success(AuditAction.REQUIREMENT_RECEIVING_REOPENED.name(),
                AuditResourceType.REQUIREMENT.name(), requirementId, null, reason, null,
                Map.of("nodeId", receivingNode.getId(), "reason", reason)));
        return toDTO(requirementId, workflow, receivingNode, context.config(), readState(receivingNode));
    }

    private RequirementDO requireRequirement(Long requirementId, boolean forUpdate) {
        if (requirementId == null) throw BusinessException.notFound("需求不存在");
        RequirementDO requirement = forUpdate ? requirementMapper.selectByIdForUpdate(requirementId)
                : requirementMapper.selectById(requirementId);
        if (requirement == null || Boolean.TRUE.equals(requirement.getDeleted())) {
            throw BusinessException.notFound("需求不存在");
        }
        return requirement;
    }

    private void clearRequirementSystem(Long requirementId) {
        RequirementDO requirement = requirementMapper.selectByIdForUpdate(requirementId);
        if (requirement == null || Boolean.TRUE.equals(requirement.getDeleted())) {
            throw BusinessException.notFound("需求不存在");
        }
        if (requirement.getSystemId() == null) return;
        requirement.setSystemId(null);
        if (requirementMapper.updateById(requirement) != 1) {
            throw BusinessException.conflict("需求系统已被其他人修改，请刷新后重试");
        }
    }

    private DevelopmentItemWorkflowDO requireWorkflow(Long requirementId, boolean forUpdate) {
        DevelopmentItemWorkflowDO workflow = forUpdate
                ? workflowMapper.selectForUpdate("REQUIREMENT", requirementId)
                : workflowMapper.selectByItem("REQUIREMENT", requirementId);
        if (workflow == null || !Objects.equals(workflow.getItemType(), "REQUIREMENT")
                || !Objects.equals(workflow.getItemId(), requirementId)) {
            throw BusinessException.conflict("需求流程尚未配置");
        }
        return workflow;
    }

    private DevelopmentItemWorkflowNodeDO requireNode(Long workflowId, Long nodeId) {
        DevelopmentItemWorkflowNodeDO node = nodeMapper.selectById(nodeId);
        if (node == null || !Objects.equals(workflowId, node.getWorkflowId())) {
            throw BusinessException.notFound("流程节点不存在");
        }
        return node;
    }

    private ComponentContext componentContext(DevelopmentItemWorkflowDO workflow,
                                              DevelopmentItemWorkflowNodeDO node) {
        WorkflowTemplateDefinition definition = workflowTemplateService.getDefinition(workflow.getTemplateVersionId());
        WorkflowNodeDefinition nodeDefinition = definition.nodes().stream()
                .filter(candidate -> Objects.equals(candidate.key(), node.getNodeKey()))
                .findFirst().orElse(null);
        if (nodeDefinition == null || !nodeDefinition.runtimeComponents().contains(
                WorkflowComponentKey.REQUIREMENT_RECEIVING_ANALYSIS)) {
            throw BusinessException.conflict("当前节点未配置需求接收与分析组件");
        }
        JsonNode configNode = nodeDefinition.componentConfigs() == null ? null
                : nodeDefinition.componentConfigs().get(WorkflowComponentKey.REQUIREMENT_RECEIVING_ANALYSIS);
        return new ComponentContext(nodeDefinition, RequirementReceivingAnalysisConfig.from(configNode));
    }

    private WorkflowNodeDefinition findReceivingDefinition(WorkflowTemplateDefinition definition) {
        if (definition == null) return null;
        return definition.nodes().stream()
                .filter(node -> node.runtimeComponents().contains(WorkflowComponentKey.REQUIREMENT_RECEIVING_ANALYSIS))
                .findFirst().orElse(null);
    }

    private RequirementReceivingAnalysisState readState(DevelopmentItemWorkflowNodeDO node) {
        Map<String, JsonNode> values = readValues(node.getFieldValuesJson());
        JsonNode components = values.get(COMPONENTS_KEY);
        JsonNode state = components == null ? null
                : components.get(WorkflowComponentKey.REQUIREMENT_RECEIVING_ANALYSIS);
        if (state == null || state.isNull()) return null;
        return parseState(state);
    }

    private RequirementReceivingAnalysisState parseState(JsonNode state) {
        try {
            return objectMapper.treeToValue(state, RequirementReceivingAnalysisState.class);
        } catch (JsonProcessingException exception) {
            throw BusinessException.error("需求接收分析内容格式不正确");
        }
    }

    private void validateState(RequirementReceivingAnalysisState state,
                               RequirementReceivingAnalysisConfig config, boolean completing) {
        List<String> errors = RequirementReceivingAnalysisPolicy.validate(state, config, completing);
        if (!errors.isEmpty()) throw BusinessException.error(String.join("；", errors));
    }

    private void persistState(DevelopmentItemWorkflowNodeDO node, RequirementReceivingAnalysisState state) {
        Map<String, JsonNode> values = new LinkedHashMap<>(readValues(node.getFieldValuesJson()));
        ObjectNode components;
        JsonNode current = values.get(COMPONENTS_KEY);
        if (current != null && current.isObject()) components = (ObjectNode) current.deepCopy();
        else components = objectMapper.createObjectNode();
        components.set(WorkflowComponentKey.REQUIREMENT_RECEIVING_ANALYSIS, objectMapper.valueToTree(state));
        values.put(COMPONENTS_KEY, components);
        try {
            node.setFieldValuesJson(objectMapper.writeValueAsString(values));
        } catch (JsonProcessingException exception) {
            throw BusinessException.error("需求接收分析保存失败，请重试");
        }
    }

    private Map<String, JsonNode> readValues(String json) {
        if (!StringUtils.hasText(json)) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw BusinessException.error("节点字段数据格式不正确");
        }
    }

    private RequirementReceivingAnalysisState withDecision(RequirementReceivingAnalysisState current,
                                                            RequirementReceivingAnalysisState.Decision decision,
                                                            String reason) {
        if (current == null) throw BusinessException.error("请先填写需求接收分析内容");
        return new RequirementReceivingAnalysisState(current.validity(), current.filterReasons(),
                current.interpretation(), current.filterNote(), current.category(), current.feasibilityScore(),
                current.roiScore(), current.strategicFitScore(), current.analysisConclusion(), decision,
                current.supplementNote(), reason);
    }

    private void requireOpenWorkflow(DevelopmentItemWorkflowDO workflow) {
        if (StringUtils.hasText(workflow.getTerminalStatus())) {
            throw BusinessException.conflict("需求流程已终止，不能继续操作");
        }
    }

    private void requireEditableNode(DevelopmentItemWorkflowNodeDO node) {
        if (!Objects.equals(node.getStatus(), NODE_LOCKED) && !Objects.equals(node.getStatus(), NODE_ACTIVE)) {
            throw BusinessException.conflict("已完成的流程节点不能编辑");
        }
    }

    private void requireExpectedVersion(Integer actual, Integer expected) {
        if (!Objects.equals(actual, expected)) {
            throw BusinessException.conflict("流程节点已被其他人修改，请刷新后重试");
        }
    }

    private String requireReason(String reason, String message) {
        if (!StringUtils.hasText(reason)) throw BusinessException.error(message);
        return reason.trim();
    }

    private RequirementReceivingAnalysisDTO toDTO(Long requirementId, DevelopmentItemWorkflowDO workflow,
                                                  DevelopmentItemWorkflowNodeDO node,
                                                  RequirementReceivingAnalysisConfig config,
                                                  RequirementReceivingAnalysisState state) {
        RequirementReceivingAnalysisDTO dto = new RequirementReceivingAnalysisDTO();
        dto.setRequirementId(requirementId);
        dto.setNodeId(node.getId());
        dto.setNodeVersion(node.getVersion());
        dto.setNodeStatus(node.getStatus());
        dto.setTerminalStatus(workflow.getTerminalStatus());
        dto.setState(state);
        dto.setConfig(config);
        dto.setAverageScore(RequirementReceivingAnalysisPolicy.averageScore(state));
        dto.setValueConclusion(RequirementReceivingAnalysisPolicy.valueConclusion(state));
        return dto;
    }

    private record ComponentContext(WorkflowNodeDefinition definition,
                                    RequirementReceivingAnalysisConfig config) { }
}
