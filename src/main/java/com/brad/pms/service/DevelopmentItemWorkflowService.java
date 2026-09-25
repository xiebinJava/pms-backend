package com.brad.pms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.enums.DevelopmentAssignmentType;
import com.brad.pms.dto.request.DevelopmentItemNodeUpdateCmd;
import com.brad.pms.dto.request.DevelopmentItemTaskSaveCmd;
import com.brad.pms.dto.response.DevelopmentItemTaskDTO;
import com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO;
import com.brad.pms.dto.response.DevelopmentItemWorkflowNodeDTO;
import com.brad.pms.entity.DevelopmentItemTaskDO;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import com.brad.pms.entity.DevelopmentItemWorkflowNodeDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.entity.ProjectNodeIterationPlanDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.entity.WorkflowTemplateVersionDO;
import com.brad.pms.mapper.DevelopmentItemTaskMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowNodeMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.WorkflowTemplateVersionMapper;
import com.brad.pms.security.UserContext;
import com.brad.pms.workflow.DevelopmentItemType;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import com.brad.pms.workflow.WorkflowTemplateDefinition;
import com.brad.pms.workflow.WorkflowFieldDefinition;
import com.brad.pms.workflow.WorkflowFieldType;
import com.brad.pms.workflow.WorkflowFieldValueValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DevelopmentItemWorkflowService {

    private static final int NODE_LOCKED = 0;
    private static final int NODE_ACTIVE = 1;
    private static final int NODE_COMPLETED = 2;
    private static final int TASK_TODO = 0;
    private static final int TASK_IN_PROGRESS = 1;
    private static final int TASK_DONE = 2;

    private final DevelopmentItemWorkflowMapper workflowMapper;
    private final DevelopmentItemWorkflowNodeMapper nodeMapper;
    private final DevelopmentItemTaskMapper taskMapper;
    private final ProjectNodeDevelopmentTopicMapper topicMapper;
    private final ProjectNodeDevelopmentStoryMapper storyMapper;
    private final ProjectNodeMapper projectNodeMapper;
    private final ProjectNodeIterationPlanMapper iterationPlanMapper;
    private final WorkflowTemplateVersionMapper templateVersionMapper;
    private final ProjectPermissionService permissionService;
    private final UserService userService;
    private final WorkflowTemplateService workflowTemplateService;
    private final WorkflowComponentBindingService workflowComponentBindingService;
    private final ProjectMemberAssignmentService assignmentService;
    private final ObjectMapper objectMapper;

    /** Creates a pinned snapshot of the current default, when one is configured. */
    @Transactional
    public DevelopmentItemWorkflowDO createIfDefaultExists(
            DevelopmentItemType itemType, Long itemId, Long projectId, Long sourceNodeId) {
        return createWithResolvedBinding(itemType, itemId, projectId, sourceNodeId,
                workflowTemplateService.resolveDefaultForProcessType(itemType.processTypeCode()));
    }

    /** Creates a pinned snapshot using an explicitly selected published template version. */
    @Transactional
    public DevelopmentItemWorkflowDO createWithTemplate(
            DevelopmentItemType itemType, Long itemId, Long projectId, Long sourceNodeId,
            Long templateVersionId) {
        return createWithResolvedBinding(itemType, itemId, projectId, sourceNodeId,
                workflowTemplateService.resolveForProcessType(itemType.processTypeCode(), templateVersionId));
    }

    private DevelopmentItemWorkflowDO createWithResolvedBinding(
            DevelopmentItemType itemType, Long itemId, Long projectId, Long sourceNodeId,
            WorkflowTemplateService.WorkflowTemplateBinding binding) {
        if (itemId == null || (projectId == null) != (sourceNodeId == null)) {
            throw BusinessException.error("研发事项信息不完整，无法绑定流程");
        }
        if (projectId != null) permissionService.requireProjectReadable(projectId);
        requireItemScope(itemType, itemId, projectId, sourceNodeId);
        DevelopmentItemWorkflowDO existing = workflowMapper.selectByItem(itemType.name(), itemId);
        if (existing != null) {
            if (!Objects.equals(existing.getProjectId(), projectId)
                    || !Objects.equals(existing.getSourceNodeId(), sourceNodeId)) {
                throw BusinessException.notFound("研发事项流程不存在");
            }
            return existing;
        }

        if (binding == null) return null;

        DevelopmentItemWorkflowDO workflow = new DevelopmentItemWorkflowDO();
        workflow.setItemType(itemType.name());
        workflow.setItemId(itemId);
        workflow.setProjectId(projectId);
        workflow.setSourceNodeId(sourceNodeId);
        workflow.setTemplateVersionId(binding.version().getId());
        if (itemType == DevelopmentItemType.TOPIC) {
            String projectMountNodeKey = workflowTemplateService.resolveTopicSourceProjectNodeKeyForRuntime(
                    binding.version().getId());
            if (projectId != null && projectMountNodeKey == null) {
                throw BusinessException.error("专题流程未配置项目节点挂载点");
            }
            workflow.setProjectMountNodeKey(projectMountNodeKey);
            WorkflowTemplateService.WorkflowTemplateBinding storyBinding =
                    workflowTemplateService.resolveDefaultForProcessType(DevelopmentItemType.STORY.processTypeCode());
            if (storyBinding != null) {
                workflow.setStoryMountTemplateVersionId(storyBinding.version().getId());
                workflow.setStoryMountNodeKey(
                        workflowTemplateService.resolveStorySourceTopicNodeKey(storyBinding.version().getId()));
            }
        }
        workflow.setVersion(0);
        try {
            if (workflowMapper.insert(workflow) != 1) {
                throw BusinessException.conflict("研发事项流程绑定失败，请重试");
            }
        } catch (DuplicateKeyException exception) {
            DevelopmentItemWorkflowDO winner = workflowMapper.selectForUpdate(itemType.name(), itemId);
            if (winner != null) return winner;
            throw BusinessException.conflict("研发事项流程绑定失败，请重试");
        }

        WorkflowTemplateDefinition definition = workflowTemplateService.getDefinition(binding.version().getId());
        List<WorkflowNodeDefinition> definitions = definition.nodes();
        for (int i = 0; i < definitions.size(); i++) {
            WorkflowNodeDefinition definitionNode = definitions.get(i);
            DevelopmentItemWorkflowNodeDO node = new DevelopmentItemWorkflowNodeDO();
            node.setWorkflowId(workflow.getId());
            node.setNodeKey(definitionNode.key());
            node.setName(definitionNode.name());
            node.setDescription(definitionNode.description());
            node.setDeliverable(definitionNode.deliverable());
            node.setSort(i);
            node.setStatus(i == 0 ? NODE_ACTIVE : NODE_LOCKED);
            node.setVersion(0);
            if (nodeMapper.insert(node) != 1) {
                throw BusinessException.conflict("研发事项流程节点初始化失败，请重试");
            }
        }
        return workflow;
    }

    /** Resolves a story's parent topic workflow node from the topic's immutable mount snapshot. */
    public Long resolveTopicStoryMountNodeId(Long topicId) {
        if (topicId == null) return null;
        DevelopmentItemWorkflowDO topicWorkflow = workflowMapper.selectByItem(
                DevelopmentItemType.TOPIC.name(), topicId);
        if (topicWorkflow == null || !StringUtils.hasText(topicWorkflow.getStoryMountNodeKey())) return null;
        List<DevelopmentItemWorkflowNodeDO> nodes = nodeMapper.selectByWorkflowAndNodeKey(
                topicWorkflow.getId(), topicWorkflow.getStoryMountNodeKey());
        if (nodes == null || nodes.size() != 1) {
            throw BusinessException.conflict("专题流程没有唯一的故事挂载节点");
        }
        return nodes.get(0).getId();
    }

    @Transactional
    public void remove(DevelopmentItemType itemType, Long itemId) {
        if (itemId == null) return;
        DevelopmentItemWorkflowDO workflow = workflowMapper.selectForUpdate(itemType.name(), itemId);
        if (workflow != null && workflowMapper.deleteById(workflow.getId()) != 1) {
            throw BusinessException.conflict("研发事项流程清理失败，请重试");
        }
    }

    @Transactional
    public DevelopmentItemWorkflowDetailDTO detail(DevelopmentItemType itemType, Long itemId) {
        ItemContext context = loadContext(itemType, itemId);
        DevelopmentItemWorkflowDO workflow = createIfDefaultExists(
                itemType, itemId, context.project() == null ? null : context.project().getId(),
                context.sourceNode() == null ? null : context.sourceNode().getId());
        return toDetail(context, workflow);
    }

    @Transactional
    public DevelopmentItemWorkflowDetailDTO updateNode(
            DevelopmentItemType itemType, Long itemId, Long nodeId, DevelopmentItemNodeUpdateCmd cmd) {
        if (cmd == null) throw BusinessException.error("节点更新内容不能为空");
        ItemContext context = requireWritableItem(itemType, itemId, "更新研发事项流程节点");
        DevelopmentItemWorkflowDO workflow = requireWorkflowForUpdate(itemType, itemId);
        DevelopmentItemWorkflowNodeDO node = requireNode(workflow.getId(), nodeId);
        requireEditableNode(node);
        requireExpectedVersion(node.getVersion(), cmd.getVersion(), "流程节点已被其他人修改，请刷新后重试");
        if (cmd.getStartDate() != null && cmd.getEndDate() != null && cmd.getStartDate().isAfter(cmd.getEndDate())) {
            throw BusinessException.error("节点开始日期不能晚于结束日期");
        }
        if (!Objects.equals(cmd.getOwnerId(), node.getOwnerId())) {
            replaceAssignment(context, itemType, itemId, DevelopmentAssignmentType.WORKFLOW_NODE_OWNER,
                    nodeId, node.getOwnerId(), cmd.getOwnerId());
        }
        node.setOwnerId(cmd.getOwnerId());
        node.setStartDate(cmd.getStartDate());
        node.setEndDate(cmd.getEndDate());
        if (cmd.getFieldValues() != null) {
            WorkflowNodeDefinition definition = workflowTemplateService.getNodeDefinition(
                    workflow.getTemplateVersionId(), node.getNodeKey());
            if (definition == null) throw BusinessException.error("节点没有可配置的字段定义");
            Map<String, JsonNode> values;
            try {
                values = WorkflowFieldValueValidator.validate(definition.fields(), cmd.getFieldValues());
            } catch (IllegalArgumentException exception) {
                throw BusinessException.error(exception.getMessage());
            }
            validateFieldPeople(definition.fields(), values,
                    readFieldValues(node.getFieldValuesJson()));
            node.setFieldValuesJson(writeFieldValues(values));
        }
        if (nodeMapper.updateById(node) != 1) {
            throw BusinessException.conflict("流程节点已被其他人修改，请刷新后重试");
        }
        return detail(itemType, itemId);
    }

    @Transactional
    public DevelopmentItemWorkflowDetailDTO completeNode(DevelopmentItemType itemType, Long itemId, Long nodeId) {
        ItemContext context = requireWritableItem(itemType, itemId, "完成研发事项流程节点");
        DevelopmentItemWorkflowDO workflow = requireWorkflowForUpdate(itemType, itemId);
        DevelopmentItemWorkflowNodeDO node = requireNode(workflow.getId(), nodeId);
        requireActiveNode(node);
        WorkflowNodeDefinition definition = workflowTemplateService.getNodeDefinition(
                workflow.getTemplateVersionId(), node.getNodeKey());
        if (definition != null) {
            List<String> missingFields = WorkflowFieldValueValidator.missingRequiredFields(
                    definition.fields(), readFieldValues(node.getFieldValuesJson()));
            if (!missingFields.isEmpty()) {
                throw BusinessException.error("请先填写" + String.join("、", missingFields));
            }
        }
        Long incompleteCount = taskMapper.selectCount(new LambdaQueryWrapper<DevelopmentItemTaskDO>()
                .eq(DevelopmentItemTaskDO::getWorkflowId, workflow.getId())
                .eq(DevelopmentItemTaskDO::getNodeId, nodeId)
                .ne(DevelopmentItemTaskDO::getStatus, TASK_DONE));
        if (incompleteCount != null && incompleteCount > 0) {
            throw BusinessException.conflict("请先完成当前节点的全部任务和子任务");
        }

        node.setStatus(NODE_COMPLETED);
        if (nodeMapper.updateById(node) != 1) {
            throw BusinessException.conflict("流程节点已被其他人修改，请刷新后重试");
        }
        DevelopmentItemWorkflowNodeDO next = nodeMapper.selectOne(new LambdaQueryWrapper<DevelopmentItemWorkflowNodeDO>()
                .eq(DevelopmentItemWorkflowNodeDO::getWorkflowId, workflow.getId())
                .eq(DevelopmentItemWorkflowNodeDO::getStatus, NODE_LOCKED)
                .gt(DevelopmentItemWorkflowNodeDO::getSort, node.getSort())
                .orderByAsc(DevelopmentItemWorkflowNodeDO::getSort)
                .last("LIMIT 1"));
        if (next != null) {
            next.setStatus(NODE_ACTIVE);
            if (nodeMapper.updateById(next) != 1) {
                throw BusinessException.conflict("下一流程节点已被其他人修改，请刷新后重试");
            }
        }
        return detail(itemType, itemId);
    }

    @Transactional
    public DevelopmentItemWorkflowDetailDTO saveTask(
            DevelopmentItemType itemType, Long itemId, Long nodeId, Long taskId, DevelopmentItemTaskSaveCmd cmd) {
        if (cmd == null) throw BusinessException.error("任务内容不能为空");
        ItemContext context = requireWritableItem(itemType, itemId, "维护研发事项任务");
        DevelopmentItemWorkflowDO workflow = requireWorkflowForUpdate(itemType, itemId);
        DevelopmentItemTaskDO existingTask = taskId == null ? null : taskMapper.selectById(taskId);
        if (taskId != null && (existingTask == null || !workflow.getId().equals(existingTask.getWorkflowId()))) {
            throw BusinessException.notFound("任务不存在");
        }
        if (nodeId == null && existingTask != null) nodeId = existingTask.getNodeId();
        DevelopmentItemWorkflowNodeDO node = requireNode(workflow.getId(), nodeId);
        requireEditableNode(node);
        if (!StringUtils.hasText(cmd.getTitle())) throw BusinessException.error("任务名称不能为空");
        Integer status = cmd.getStatus() == null ? TASK_TODO : cmd.getStatus();
        Integer priority = cmd.getPriority() == null ? 1 : cmd.getPriority();
        if (status < TASK_TODO || status > TASK_DONE) throw BusinessException.error("任务状态不正确");
        if (priority < 0 || priority > 2) throw BusinessException.error("任务优先级不正确");
        Long existingAssigneeId = existingTask == null ? null : existingTask.getAssigneeId();
        if (cmd.getAssigneeId() != null && !Objects.equals(cmd.getAssigneeId(), existingAssigneeId)) {
            userService.requireActiveUser(cmd.getAssigneeId());
        }

        Long savedTaskId;
        if (taskId == null) {
            DevelopmentItemTaskDO task = new DevelopmentItemTaskDO();
            task.setWorkflowId(workflow.getId());
            task.setNodeId(nodeId);
            task.setParentId(validateParent(workflow.getId(), nodeId, cmd.getParentId()));
            task.setCreatedBy(UserContext.userIdOrNull());
            task.setVersion(0);
            applyTaskFields(task, cmd, status, priority);
            if (taskMapper.insert(task) != 1) throw BusinessException.conflict("任务创建失败，请重试");
            savedTaskId = task.getId();
        } else {
            DevelopmentItemTaskDO task = requireTask(workflow.getId(), nodeId, taskId);
            requireExpectedVersion(task.getVersion(), cmd.getVersion(), "任务已被其他人修改，请刷新后重试");
            if (!Objects.equals(task.getParentId(), cmd.getParentId())) {
                throw BusinessException.error("任务不能通过编辑更改父子关系");
            }
            applyTaskFields(task, cmd, status, priority);
            if (taskMapper.updateById(task) != 1) {
                throw BusinessException.conflict("任务已被其他人修改，请刷新后重试");
            }
            savedTaskId = task.getId();
        }
        if (!Objects.equals(existingAssigneeId, cmd.getAssigneeId())) {
            replaceAssignment(context, itemType, itemId, DevelopmentAssignmentType.TASK_ASSIGNEE,
                    savedTaskId, existingAssigneeId, cmd.getAssigneeId());
        }
        return detail(itemType, itemId);
    }

    @Transactional
    public DevelopmentItemWorkflowDetailDTO deleteTask(
            DevelopmentItemType itemType, Long itemId, Long taskId) {
        ItemContext context = requireWritableItem(itemType, itemId, "删除研发事项任务");
        DevelopmentItemWorkflowDO workflow = requireWorkflowForUpdate(itemType, itemId);
        DevelopmentItemTaskDO task = taskMapper.selectById(taskId);
        if (task == null || !workflow.getId().equals(task.getWorkflowId())) {
            throw BusinessException.notFound("任务不存在");
        }
        DevelopmentItemWorkflowNodeDO node = requireNode(workflow.getId(), task.getNodeId());
        requireEditableNode(node);
        if (task.getParentId() == null) {
            List<DevelopmentItemTaskDO> children = taskMapper.selectList(new LambdaQueryWrapper<DevelopmentItemTaskDO>()
                    .eq(DevelopmentItemTaskDO::getWorkflowId, workflow.getId())
                    .eq(DevelopmentItemTaskDO::getNodeId, node.getId())
                    .eq(DevelopmentItemTaskDO::getParentId, task.getId()));
            children.forEach(child -> {
                if (taskMapper.deleteById(child.getId()) == 1 && child.getAssigneeId() != null) {
                    assignmentService.releaseAssignment(context.project() == null ? null : context.project().getId(),
                            itemType, itemId, DevelopmentAssignmentType.TASK_ASSIGNEE, child.getId());
                }
            });
        }
        if (taskMapper.deleteById(taskId) != 1) throw BusinessException.conflict("任务已被其他人修改，请刷新后重试");
        if (task.getAssigneeId() != null) {
            assignmentService.releaseAssignment(context.project() == null ? null : context.project().getId(),
                    itemType, itemId, DevelopmentAssignmentType.TASK_ASSIGNEE, task.getId());
        }
        return detail(itemType, itemId);
    }

    private DevelopmentItemWorkflowDetailDTO toDetail(ItemContext context, DevelopmentItemWorkflowDO workflow) {
        DevelopmentItemWorkflowDetailDTO dto = new DevelopmentItemWorkflowDetailDTO();
        dto.setItemType(context.itemType().name().toLowerCase());
        dto.setId(context.itemId());
        dto.setTitle(context.title());
        dto.setProjectId(context.project() == null ? null : context.project().getId());
        dto.setProjectCode(context.project() == null ? null : context.project().getCode());
        dto.setProjectName(context.project() == null ? null : context.project().getName());
        dto.setSourceNodeId(context.sourceNode() == null ? null : context.sourceNode().getId());
        dto.setSourceNodeName(context.sourceNode() == null ? null : context.sourceNode().getName());
        dto.setTopicId(context.topicId());
        dto.setTopicTitle(context.topicTitle());
        dto.setTopicWorkflowNodeId(context.topicWorkflowNodeId());
        dto.setTopicWorkflowNodeName(context.topicWorkflowNodeName());
        dto.setOwnerId(context.ownerId());
        dto.setOwnerName(displayName(context.ownerId()));
        dto.setDevelopmentStatus(context.developmentStatus());
        dto.setDevelopmentProgress(context.developmentProgress());
        dto.setStoryPoints(context.storyPoints());
        dto.setStartDate(context.startDate());
        dto.setDueDate(context.dueDate());
        dto.setBlocker(context.blocker());
        dto.setLatestBuildVersion(context.latestBuildVersion());
        dto.setTestStatus(context.testStatus());
        dto.setIterationPlanName(context.iterationPlanName());

        if (workflow == null) {
            dto.setWorkflowConfigured(false);
            dto.setWorkflowStatus("NOT_CONFIGURED");
            dto.setWorkflowProgress(0);
            dto.setCompletedNodeCount(0);
            dto.setTotalNodeCount(0);
            dto.setNodes(List.of());
            return dto;
        }
        dto.setWorkflowConfigured(true);
        dto.setWorkflowId(workflow.getId());
        dto.setTemplateVersionId(workflow.getTemplateVersionId());
        WorkflowTemplateVersionDO templateVersion = templateVersionMapper.selectById(workflow.getTemplateVersionId());
        dto.setTemplateVersionNo(templateVersion == null ? null : templateVersion.getVersionNo());

        List<DevelopmentItemWorkflowNodeDO> nodes = nodeMapper.selectList(new LambdaQueryWrapper<DevelopmentItemWorkflowNodeDO>()
                .eq(DevelopmentItemWorkflowNodeDO::getWorkflowId, workflow.getId())
                .orderByAsc(DevelopmentItemWorkflowNodeDO::getSort)
                .orderByAsc(DevelopmentItemWorkflowNodeDO::getId));
        List<DevelopmentItemTaskDO> tasks = taskMapper.selectList(new LambdaQueryWrapper<DevelopmentItemTaskDO>()
                .eq(DevelopmentItemTaskDO::getWorkflowId, workflow.getId())
                .orderByAsc(DevelopmentItemTaskDO::getSort)
                .orderByAsc(DevelopmentItemTaskDO::getId));
        Map<Long, UserDO> users = loadUsers(nodes.stream().map(DevelopmentItemWorkflowNodeDO::getOwnerId).toList(),
                tasks.stream().map(DevelopmentItemTaskDO::getAssigneeId).toList());
        Map<Long, List<DevelopmentItemTaskDO>> tasksByNode = tasks.stream()
                .collect(Collectors.groupingBy(DevelopmentItemTaskDO::getNodeId));
        WorkflowTemplateDefinition effectiveDefinition = workflowTemplateService.getDefinition(workflow.getTemplateVersionId());
        if (context.itemType() == DevelopmentItemType.TOPIC) {
            effectiveDefinition = workflowComponentBindingService.applyStoryBinding(
                    effectiveDefinition, workflow.getStoryMountNodeKey());
        }
        WorkflowTemplateDefinition nodeDefinitionSnapshot = effectiveDefinition;
        List<DevelopmentItemWorkflowNodeDTO> nodeDTOs = nodes.stream()
                .map(node -> toNodeDTO(node, tasksByNode.getOrDefault(node.getId(), List.of()), users,
                        nodeDefinitionSnapshot))
                .toList();
        int completed = (int) nodes.stream().filter(node -> Objects.equals(node.getStatus(), NODE_COMPLETED)).count();
        int total = nodes.size();
        dto.setCompletedNodeCount(completed);
        dto.setTotalNodeCount(total);
        dto.setWorkflowProgress(total == 0 ? 0 : (int) Math.round(completed * 100.0 / total));
        dto.setWorkflowStatus(total > 0 && completed == total ? "COMPLETED"
                : nodes.stream().anyMatch(node -> Objects.equals(node.getStatus(), NODE_ACTIVE)) ? "IN_PROGRESS" : "NOT_STARTED");
        dto.setNodes(nodeDTOs);
        return dto;
    }

    private DevelopmentItemWorkflowNodeDTO toNodeDTO(
            DevelopmentItemWorkflowNodeDO node, List<DevelopmentItemTaskDO> tasks, Map<Long, UserDO> users,
            WorkflowTemplateDefinition templateDefinition) {
        DevelopmentItemWorkflowNodeDTO dto = new DevelopmentItemWorkflowNodeDTO();
        dto.setId(node.getId());
        dto.setNodeKey(node.getNodeKey());
        dto.setName(node.getName());
        dto.setDescription(node.getDescription());
        dto.setDeliverable(node.getDeliverable());
        dto.setSort(node.getSort());
        dto.setStatus(node.getStatus());
        dto.setOwnerId(node.getOwnerId());
        dto.setOwnerName(displayName(users.get(node.getOwnerId())));
        dto.setStartDate(node.getStartDate());
        dto.setEndDate(node.getEndDate());
        dto.setVersion(node.getVersion());
        WorkflowNodeDefinition definition = templateDefinition == null ? null : templateDefinition.nodes().stream()
                .filter(candidate -> Objects.equals(candidate.key(), node.getNodeKey()))
                .findFirst().orElse(null);
        dto.setFields(definition == null ? List.of() : definition.fields());
        dto.setRuntimeComponents(definition == null ? List.of() : definition.runtimeComponents());
        dto.setFieldValues(readFieldValues(node.getFieldValuesJson()));
        Map<Long, DevelopmentItemTaskDTO> taskDTOs = new HashMap<>();
        for (DevelopmentItemTaskDO task : tasks) taskDTOs.put(task.getId(), toTaskDTO(task, users));
        List<DevelopmentItemTaskDTO> roots = new ArrayList<>();
        for (DevelopmentItemTaskDO task : tasks) {
            DevelopmentItemTaskDTO taskDTO = taskDTOs.get(task.getId());
            if (task.getParentId() == null) roots.add(taskDTO);
            else {
                DevelopmentItemTaskDTO parent = taskDTOs.get(task.getParentId());
                if (parent != null) parent.getChildren().add(taskDTO);
            }
        }
        dto.setTasks(roots);
        return dto;
    }

    private void validateFieldPeople(List<WorkflowFieldDefinition> fields,
                                     Map<String, JsonNode> values, Map<String, JsonNode> previousValues) {
        Map<String, WorkflowFieldDefinition> fieldsByKey = fields.stream()
                .collect(Collectors.toMap(WorkflowFieldDefinition::key, field -> field));
        for (Map.Entry<String, JsonNode> entry : values.entrySet()) {
            WorkflowFieldDefinition field = fieldsByKey.get(entry.getKey());
            JsonNode value = entry.getValue();
            if (field == null || value == null || value.isNull()) continue;
            if (value.equals(previousValues.get(entry.getKey()))) continue;
            if (field.type() == WorkflowFieldType.PERSON && value.isIntegralNumber()) {
                userService.requireActiveUser(value.asLong());
            } else if (field.type() == WorkflowFieldType.PERSON_MULTI && value.isArray()) {
                value.forEach(personId -> userService.requireActiveUser(personId.asLong()));
            }
        }
    }

    private Map<String, JsonNode> readFieldValues(String json) {
        if (!StringUtils.hasText(json)) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("研发事项节点字段数据格式不正确", exception);
        }
    }

    private String writeFieldValues(Map<String, JsonNode> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw BusinessException.error("节点字段保存失败，请重试");
        }
    }

    private DevelopmentItemTaskDTO toTaskDTO(DevelopmentItemTaskDO task, Map<Long, UserDO> users) {
        DevelopmentItemTaskDTO dto = new DevelopmentItemTaskDTO();
        dto.setId(task.getId());
        dto.setNodeId(task.getNodeId());
        dto.setParentId(task.getParentId());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setStatus(task.getStatus());
        dto.setPriority(task.getPriority());
        dto.setAssigneeId(task.getAssigneeId());
        dto.setAssigneeName(displayName(users.get(task.getAssigneeId())));
        dto.setDueDate(task.getDueDate());
        dto.setSort(task.getSort());
        dto.setVersion(task.getVersion());
        return dto;
    }

    private ItemContext loadContext(DevelopmentItemType itemType, Long itemId) {
        if (itemId == null) throw BusinessException.notFound("研发事项不存在");
        String title;
        Long projectId;
        Long sourceNodeId;
        Long ownerId;
        String developmentStatus;
        Integer developmentProgress;
        Integer storyPoints = null;
        java.time.LocalDate startDate = null;
        java.time.LocalDate dueDate = null;
        String blocker = null;
        String latestBuildVersion = null;
        String testStatus = null;
        String iterationPlanName = null;
        Long topicId = null;
        String topicTitle = null;
        Long topicWorkflowNodeId = null;
        String topicWorkflowNodeName = null;

        if (itemType == DevelopmentItemType.TOPIC) {
            ProjectNodeDevelopmentTopicDO topic = topicMapper.selectById(itemId);
            if (topic == null || Boolean.TRUE.equals(topic.getDeleted())) throw BusinessException.notFound("专题不存在");
            title = topic.getTitle();
            projectId = topic.getProjectId();
            sourceNodeId = topic.getNodeId();
            ownerId = topic.getOwnerId();
            latestBuildVersion = topic.getLatestBuildVersion();
            testStatus = topic.getTestStatus();
            List<ProjectNodeDevelopmentStoryDO> stories = storyMapper.selectList(new LambdaQueryWrapper<ProjectNodeDevelopmentStoryDO>()
                    .eq(ProjectNodeDevelopmentStoryDO::getTopicId, topic.getId()));
            developmentStatus = deriveTopicStatus(stories);
            developmentProgress = averageProgress(stories);
        } else {
            ProjectNodeDevelopmentStoryDO story = storyMapper.selectById(itemId);
            if (story == null) throw BusinessException.notFound("故事不存在");
            title = story.getTitle();
            projectId = story.getProjectId();
            sourceNodeId = story.getNodeId();
            ownerId = story.getOwnerId();
            developmentStatus = story.getStatus();
            developmentProgress = effectiveProgress(story.getStatus(), story.getProgress());
            storyPoints = story.getStoryPoints();
            startDate = story.getStartDate();
            dueDate = story.getDueDate();
            blocker = story.getBlocker();
            topicId = story.getTopicId();
            ProjectNodeDevelopmentTopicDO topic = topicId == null ? null : topicMapper.selectById(topicId);
            if (topicId == null) {
                if (projectId != null || sourceNodeId != null) {
                    throw BusinessException.notFound("未关联专题的故事不能绑定项目节点");
                }
            } else {
                if (topic == null || Boolean.TRUE.equals(topic.getDeleted())
                        || !Objects.equals(topic.getProjectId(), projectId)
                        || !Objects.equals(topic.getNodeId(), sourceNodeId)) {
                    throw BusinessException.notFound("故事所属专题不存在");
                }
                topicTitle = topic.getTitle();
                topicWorkflowNodeId = story.getTopicWorkflowNodeId();
                if (topicWorkflowNodeId != null) {
                    DevelopmentItemWorkflowNodeDO topicNode = nodeMapper.selectById(topicWorkflowNodeId);
                    if (topicNode == null) throw BusinessException.notFound("故事所属专题流程节点不存在");
                    DevelopmentItemWorkflowDO topicWorkflow = workflowMapper.selectByItem(
                            DevelopmentItemType.TOPIC.name(), topic.getId());
                    if (topicWorkflow == null || !Objects.equals(topicWorkflow.getId(), topicNode.getWorkflowId())) {
                        throw BusinessException.notFound("故事所属专题流程节点不存在");
                    }
                    topicWorkflowNodeName = topicNode.getName();
                }
            }
            ProjectNodeIterationPlanDO plan = story.getIterationPlanId() == null
                    ? null : iterationPlanMapper.selectById(story.getIterationPlanId());
            iterationPlanName = plan == null ? null : plan.getName();
        }
        ProjectDO project = projectId == null ? null : permissionService.requireProjectReadable(projectId);
        ProjectNodeDO sourceNode = sourceNodeId == null ? null : projectNodeMapper.selectById(sourceNodeId);
        if ((projectId == null) != (sourceNodeId == null)
                || (sourceNode != null && !Objects.equals(sourceNode.getProjectId(), projectId))) {
            throw BusinessException.notFound("研发事项来源节点不存在");
        }
        if (sourceNodeId != null && sourceNode == null) throw BusinessException.notFound("研发事项来源节点不存在");
        return new ItemContext(itemType, itemId, title, project, sourceNode, topicId, topicTitle,
                topicWorkflowNodeId, topicWorkflowNodeName, ownerId,
                developmentStatus, developmentProgress, storyPoints, startDate, dueDate, blocker,
                latestBuildVersion, testStatus, iterationPlanName);
    }

    private ItemContext requireWritableItem(DevelopmentItemType itemType, Long itemId, String action) {
        ItemContext context = loadContext(itemType, itemId);
        if (context.project() != null) permissionService.requireProjectWritable(context.project().getId(), action);
        return context;
    }

    private DevelopmentItemWorkflowDO requireWorkflowForUpdate(DevelopmentItemType itemType, Long itemId) {
        ItemContext context = loadContext(itemType, itemId);
        Long projectId = context.project() == null ? null : context.project().getId();
        Long sourceNodeId = context.sourceNode() == null ? null : context.sourceNode().getId();
        requireItemScope(itemType, itemId, projectId, sourceNodeId);
        DevelopmentItemWorkflowDO workflow = workflowMapper.selectByItem(itemType.name(), itemId);
        if (workflow != null) workflow = workflowMapper.selectForUpdate(itemType.name(), itemId);
        if (workflow == null) {
            workflow = createIfDefaultExists(itemType, itemId, projectId, sourceNodeId);
        }
        if (workflow == null) throw BusinessException.conflict("流程尚未配置，请先为该类型发布并设置默认流程模板");
        if (!Objects.equals(workflow.getProjectId(), projectId)
                || !Objects.equals(workflow.getSourceNodeId(), sourceNodeId)
                || !Objects.equals(workflow.getItemId(), itemId)
                || !Objects.equals(workflow.getItemType(), itemType.name())) {
            throw BusinessException.notFound("研发事项流程不存在");
        }
        return workflow;
    }

    private DevelopmentItemWorkflowNodeDO requireNode(Long workflowId, Long nodeId) {
        DevelopmentItemWorkflowNodeDO node = nodeMapper.selectById(nodeId);
        if (node == null || !workflowId.equals(node.getWorkflowId())) {
            throw BusinessException.notFound("流程节点不存在");
        }
        return node;
    }

    private void requireActiveNode(DevelopmentItemWorkflowNodeDO node) {
        if (!Objects.equals(node.getStatus(), NODE_ACTIVE)) {
            throw BusinessException.conflict("只能操作当前进行中的流程节点");
        }
    }

    private void requireEditableNode(DevelopmentItemWorkflowNodeDO node) {
        if (!Objects.equals(node.getStatus(), NODE_LOCKED) && !Objects.equals(node.getStatus(), NODE_ACTIVE)) {
            throw BusinessException.conflict("已完成的流程节点不能编辑");
        }
    }

    private DevelopmentItemTaskDO requireTask(Long workflowId, Long nodeId, Long taskId) {
        DevelopmentItemTaskDO task = taskMapper.selectById(taskId);
        if (task == null || !workflowId.equals(task.getWorkflowId()) || !nodeId.equals(task.getNodeId())) {
            throw BusinessException.notFound("任务不存在");
        }
        return task;
    }

    private Long validateParent(Long workflowId, Long nodeId, Long parentId) {
        if (parentId == null) return null;
        DevelopmentItemTaskDO parent = taskMapper.selectById(parentId);
        if (parent == null || !workflowId.equals(parent.getWorkflowId()) || !nodeId.equals(parent.getNodeId())) {
            throw BusinessException.notFound("父任务不存在或不属于当前流程节点");
        }
        if (parent.getParentId() != null) throw BusinessException.error("子任务不能再添加子任务");
        return parentId;
    }

    private void applyTaskFields(DevelopmentItemTaskDO task, DevelopmentItemTaskSaveCmd cmd, Integer status, Integer priority) {
        task.setTitle(cmd.getTitle().trim());
        task.setDescription(trimToNull(cmd.getDescription()));
        task.setStatus(status);
        task.setPriority(priority);
        task.setAssigneeId(cmd.getAssigneeId());
        task.setDueDate(cmd.getDueDate());
        task.setSort(cmd.getSort() == null ? 0 : cmd.getSort());
    }

    private void requireExpectedVersion(Integer actual, Integer expected, String message) {
        if (expected == null || !Objects.equals(actual, expected)) throw BusinessException.conflict(message);
    }

    private void requireItemScope(DevelopmentItemType itemType, Long itemId, Long projectId, Long sourceNodeId) {
        if ((projectId == null) != (sourceNodeId == null)) {
            throw BusinessException.notFound("研发事项来源节点不存在");
        }
        if (projectId == null) {
            if (itemType == DevelopmentItemType.TOPIC) {
                ProjectNodeDevelopmentTopicDO topic = topicMapper.selectByIdForUpdate(itemId);
                if (topic == null || Boolean.TRUE.equals(topic.getDeleted())
                        || topic.getProjectId() != null || topic.getNodeId() != null) {
                    throw BusinessException.notFound("研发事项不属于独立事项范围");
                }
                return;
            }
            ProjectNodeDevelopmentStoryDO story = storyMapper.selectByIdForUpdate(itemId);
            if (story == null || story.getTopicId() != null || story.getProjectId() != null || story.getNodeId() != null) {
                throw BusinessException.notFound("研发事项不属于独立事项范围");
            }
            return;
        }
        ProjectNodeDO sourceNode = projectNodeMapper.selectById(sourceNodeId);
        if (sourceNode == null || !projectId.equals(sourceNode.getProjectId())) {
            throw BusinessException.notFound("研发事项来源节点不存在");
        }
        boolean belongsToScope;
        if (itemType == DevelopmentItemType.TOPIC) {
            ProjectNodeDevelopmentTopicDO topic = topicMapper.selectByIdForUpdate(itemId);
            belongsToScope = topic != null && !Boolean.TRUE.equals(topic.getDeleted())
                    && projectId.equals(topic.getProjectId()) && sourceNodeId.equals(topic.getNodeId());
        } else {
            ProjectNodeDevelopmentStoryDO observedStory = storyMapper.selectById(itemId);
            ProjectNodeDevelopmentTopicDO topic = observedStory == null || observedStory.getTopicId() == null
                    ? null : topicMapper.selectByIdForUpdate(observedStory.getTopicId());
            ProjectNodeDevelopmentStoryDO story = topic == null ? null : storyMapper.selectByIdForUpdate(itemId);
            belongsToScope = story != null && topic != null
                    && Objects.equals(story.getTopicId(), topic.getId())
                    && !Boolean.TRUE.equals(topic.getDeleted())
                    && projectId.equals(story.getProjectId()) && sourceNodeId.equals(story.getNodeId());
        }
        if (!belongsToScope) throw BusinessException.notFound("研发事项不存在或不属于当前项目节点");
    }

    private Map<Long, UserDO> loadUsers(List<Long> nodeOwnerIds, List<Long> assigneeIds) {
        List<Long> ids = new ArrayList<>();
        ids.addAll(nodeOwnerIds);
        ids.addAll(assigneeIds);
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) return Collections.emptyMap();
        return userService.listByIdsIncludingDeleted(distinct).stream()
                .collect(Collectors.toMap(UserDO::getId, user -> user, (left, right) -> left));
    }

    private String displayName(Long userId) {
        if (userId == null) return null;
        return userService.listByIdsIncludingDeleted(List.of(userId)).stream().findFirst()
                .map(com.brad.pms.convertor.Convertors::userDisplayName).orElse(null);
    }

    private void replaceAssignment(ItemContext context, DevelopmentItemType itemType, Long itemId,
                                   DevelopmentAssignmentType assignmentType, Long assignmentId,
                                   Long oldUserId, Long newUserId) {
        if (newUserId != null) userService.requireActiveUser(newUserId);
        assignmentService.replaceAssignment(
                context.project() == null ? null : context.project().getId(),
                itemType, itemId, assignmentType, assignmentId, newUserId);
    }

    private String displayName(UserDO user) {
        return user == null ? null : com.brad.pms.convertor.Convertors.userDisplayName(user);
    }

    private String deriveTopicStatus(List<ProjectNodeDevelopmentStoryDO> stories) {
        if (stories.isEmpty()) return "NOT_STARTED";
        if (stories.stream().allMatch(story -> "DONE".equals(story.getStatus()))) return "DONE";
        if (stories.stream().anyMatch(story -> !"NOT_STARTED".equals(story.getStatus()))) return "IN_PROGRESS";
        return "NOT_STARTED";
    }

    private int averageProgress(List<ProjectNodeDevelopmentStoryDO> stories) {
        if (stories.isEmpty()) return 0;
        return (int) Math.round(stories.stream()
                .mapToInt(story -> effectiveProgress(story.getStatus(), story.getProgress())).average().orElse(0));
    }

    private int effectiveProgress(String status, Integer progress) {
        if ("DONE".equals(status)) return 100;
        if ("NOT_STARTED".equals(status)) return 0;
        return progress == null ? 0 : Math.max(0, Math.min(100, progress));
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ItemContext(
            DevelopmentItemType itemType,
            Long itemId,
            String title,
            ProjectDO project,
            ProjectNodeDO sourceNode,
            Long topicId,
            String topicTitle,
            Long topicWorkflowNodeId,
            String topicWorkflowNodeName,
            Long ownerId,
            String developmentStatus,
            Integer developmentProgress,
            Integer storyPoints,
            java.time.LocalDate startDate,
            java.time.LocalDate dueDate,
            String blocker,
            String latestBuildVersion,
            String testStatus,
            String iterationPlanName) { }
}
