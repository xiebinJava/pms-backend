package com.brad.pms.ai.connector;

import com.brad.pms.ai.query.AiTaskQueryRequest;
import com.brad.pms.ai.query.AiTaskQueryResult;
import com.brad.pms.ai.query.AiTaskQueryService;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.PmsCommandDescriptor;
import com.brad.pms.ai.command.PmsCommandMetadata;
import com.brad.pms.ai.command.PmsCommandRegistry;
import com.brad.pms.common.enums.RequirementExecutionTargetType;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.page.PageResult;
import com.brad.pms.dto.request.DevelopmentItemPageQry;
import com.brad.pms.dto.request.IterationPlanPageQry;
import com.brad.pms.dto.request.ProjectPageQry;
import com.brad.pms.dto.request.RequirementPageQry;
import com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO;
import com.brad.pms.dto.response.DevelopmentItemWorkflowNodeDTO;
import com.brad.pms.dto.response.DevelopmentStoryListDTO;
import com.brad.pms.dto.response.DevelopmentTopicListDTO;
import com.brad.pms.dto.response.IterationPlanListDTO;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.dto.response.RequirementListDTO;
import com.brad.pms.integration.ai.api.AiQueryRequest;
import com.brad.pms.integration.ai.api.AiQueryResultDTO;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.brad.pms.service.DevelopmentItemService;
import com.brad.pms.service.DevelopmentItemWorkflowService;
import com.brad.pms.service.IterationPlanService;
import com.brad.pms.service.ProjectService;
import com.brad.pms.security.UserContext;
import com.brad.pms.workflow.DevelopmentItemType;
import com.brad.pms.workflow.WorkflowFieldDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Permission-aware read facade. It never queries PMS tables directly. */
@Service
@RequiredArgsConstructor
public class AiConnectorQueryService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ProjectService projectService;
    private final DevelopmentItemService developmentItemService;
    private final IterationPlanService iterationPlanService;
    private final AiTaskQueryService taskQueryService;
    private final DevelopmentItemWorkflowService workflowService;
    private final PmsCommandRegistry commandRegistry;

    public AiQueryResultDTO query(AiQueryRequest request) {
        String resourceType = normalizeResourceType(request == null ? null : request.resourceType());
        int page = page(request == null ? null : request.page());
        int pageSize = pageSize(request == null ? null : request.pageSize());
        Map<String, Object> filters = request == null || request.filters() == null ? Map.of() : request.filters();
        String keyword = request == null ? null : trimToNull(request.keyword());

        return switch (resourceType) {
            case "project" -> projects(keyword, filters, page, pageSize);
            case "requirement" -> requirements(keyword, filters, page, pageSize);
            case "topic" -> topics(keyword, filters, page, pageSize);
            case "story" -> stories(keyword, filters, page, pageSize);
            case "iteration_plan" -> iterationPlans(keyword, filters, page, pageSize);
            case "task", "subtask" -> tasks(filters, page, pageSize);
            default -> throw BusinessException.error("不支持的查询资源类型: " + resourceType);
        };
    }

    public AiWorkflowContextDTO context(String resourceType, Long resourceId) {
        String type = normalizeResourceType(resourceType);
        if (resourceId == null) throw BusinessException.notFound("资源不存在");
        return switch (type) {
            case "project" -> projectContext(resourceId);
            case "topic", "story", "requirement" -> developmentItemContext(type, resourceId);
            default -> throw BusinessException.error("不支持的流程上下文资源类型: " + type);
        };
    }

    private AiQueryResultDTO projects(String keyword, Map<String, Object> filters, int page, int pageSize) {
        ProjectPageQry query = new ProjectPageQry();
        query.setKeyword(keyword);
        query.setStatus(integer(filters, "status"));
        query.setView(text(filters, "view"));
        query.setOrgUnitId(longValue(filters, "orgUnitId"));
        query.setProjectManagerId(longValue(filters, "projectManagerId"));
        query.setProjectLevel(integer(filters, "projectLevel"));
        query.setPriority(integer(filters, "priority"));
        query.setAttention(text(filters, "attention"));
        query.setCurrentNodeKey(text(filters, "currentNodeKey"));
        query.setCurrPage(page);
        query.setPageSize(pageSize);
        PageResult<ProjectDTO> result = projectService.page(query);
        List<AiQueryResultDTO.Item> items = safe(result == null ? null : result.getList()).stream()
                .map(this::projectItem).toList();
        return pageResult("project", result, items, page, pageSize);
    }

    private AiQueryResultDTO requirements(String keyword, Map<String, Object> filters, int page, int pageSize) {
        RequirementPageQry query = new RequirementPageQry();
        query.setKeyword(keyword);
        query.setStatus(text(filters, "status"));
        query.setOwnerId(longValue(filters, "ownerId"));
        query.setDeleted(booleanValue(filters, "deleted"));
        String targetType = text(filters, "targetType");
        if (targetType != null) {
            try {
                query.setTargetType(RequirementExecutionTargetType.valueOf(targetType.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                throw BusinessException.error("targetType 不正确");
            }
        }
        query.setCurrPage(page);
        query.setPageSize(pageSize);
        PageResult<RequirementListDTO> result = developmentItemService.pageRequirements(query);
        List<AiQueryResultDTO.Item> items = safe(result == null ? null : result.getList()).stream()
                .map(this::requirementItem).toList();
        return pageResult("requirement", result, items, page, pageSize);
    }

    private AiQueryResultDTO topics(String keyword, Map<String, Object> filters, int page, int pageSize) {
        DevelopmentItemPageQry query = developmentQuery(keyword, filters, page, pageSize);
        PageResult<DevelopmentTopicListDTO> result = developmentItemService.pageTopics(query);
        List<AiQueryResultDTO.Item> items = safe(result == null ? null : result.getList()).stream()
                .map(this::topicItem).toList();
        return pageResult("topic", result, items, page, pageSize);
    }

    private AiQueryResultDTO stories(String keyword, Map<String, Object> filters, int page, int pageSize) {
        DevelopmentItemPageQry query = developmentQuery(keyword, filters, page, pageSize);
        PageResult<DevelopmentStoryListDTO> result = developmentItemService.pageStories(query);
        List<AiQueryResultDTO.Item> items = safe(result == null ? null : result.getList()).stream()
                .map(this::storyItem).toList();
        return pageResult("story", result, items, page, pageSize);
    }

    private DevelopmentItemPageQry developmentQuery(String keyword, Map<String, Object> filters,
                                                     int page, int pageSize) {
        DevelopmentItemPageQry query = new DevelopmentItemPageQry();
        query.setKeyword(keyword);
        query.setProjectId(longValue(filters, "projectId"));
        query.setNodeId(longValue(filters, "nodeId"));
        query.setOwnerId(longValue(filters, "ownerId"));
        query.setStatus(text(filters, "status"));
        query.setDeleted(booleanValue(filters, "deleted"));
        query.setCurrPage(page);
        query.setPageSize(pageSize);
        return query;
    }

    private AiQueryResultDTO iterationPlans(String keyword, Map<String, Object> filters,
                                            int page, int pageSize) {
        IterationPlanPageQry query = new IterationPlanPageQry();
        query.setKeyword(keyword);
        query.setProjectId(longValue(filters, "projectId"));
        query.setStatus(text(filters, "status"));
        query.setCurrPage(page);
        query.setPageSize(pageSize);
        PageResult<IterationPlanListDTO> result = iterationPlanService.page(query);
        List<AiQueryResultDTO.Item> items = safe(result == null ? null : result.getList()).stream()
                .map(this::iterationPlanItem).toList();
        return pageResult("iteration_plan", result, items, page, pageSize);
    }

    private AiQueryResultDTO tasks(Map<String, Object> filters, int page, int pageSize) {
        Long projectId = longValue(filters, "projectId");
        String scope = projectId == null ? "mine" : "project";
        AiTaskQueryResult result = taskQueryService.query(new AiTaskQueryRequest(
                scope, valueOr(text(filters, "due"), "any"), valueOr(text(filters, "status"), "all"),
                projectId, longValue(filters, "nodeId"), page, pageSize));
        List<AiQueryResultDTO.Item> items = safe(result == null ? null : result.tasks()).stream()
                .map(this::taskItem).toList();
        return new AiQueryResultDTO("task", items,
                result == null ? 0 : result.total(),
                result == null ? page : result.page(),
                result == null ? pageSize : result.pageSize(),
                result == null ? 0 : result.totalPage(),
                "current-user-readable");
    }

    private AiWorkflowContextDTO projectContext(Long projectId) {
        ProjectDTO project = projectService.detail(projectId);
        AiWorkflowContextDTO.CurrentNode currentNode = project.getCurrentNodeKey() == null
                ? null : new AiWorkflowContextDTO.CurrentNode(null, project.getCurrentNodeKey(), project.getCurrentNodeName());
        return new AiWorkflowContextDTO("project", project.getId(), project.getVersion(), currentNode,
                null, allowedProjectActions());
    }

    private AiWorkflowContextDTO developmentItemContext(String resourceType, Long resourceId) {
        DevelopmentItemType itemType = switch (resourceType) {
            case "topic" -> DevelopmentItemType.TOPIC;
            case "story" -> DevelopmentItemType.STORY;
            case "requirement" -> DevelopmentItemType.REQUIREMENT;
            default -> throw BusinessException.error("不支持的研发事项类型: " + resourceType);
        };
        DevelopmentItemWorkflowDetailDTO detail = workflowService.detail(itemType, resourceId);
        List<DevelopmentItemWorkflowNodeDTO> sourceNodes = safe(detail.getNodes());
        List<AiWorkflowContextDTO.Node> nodes = new ArrayList<>();
        for (int position = 0; position < sourceNodes.size(); position++) {
            nodes.add(toContextNode(sourceNodes.get(position), resourceType, position));
        }
        AiWorkflowContextDTO.CurrentNode current = sourceNodes.stream()
                .filter(node -> Objects.equals(node.getStatus(), 1)).findFirst()
                .or(() -> sourceNodes.stream().findFirst())
                .map(node -> new AiWorkflowContextDTO.CurrentNode(node.getId(), node.getNodeKey(), node.getName()))
                .orElse(null);
        AiWorkflowContextDTO.Workflow workflow = Boolean.TRUE.equals(detail.getWorkflowConfigured())
                || !nodes.isEmpty()
                ? new AiWorkflowContextDTO.Workflow(detail.getTemplateVersionId(), detail.getTemplateVersionNo(), nodes)
                : null;
        Integer contextVersion = detail.getTemplateVersionNo();
        return new AiWorkflowContextDTO(resourceType, resourceId, contextVersion, current, workflow,
                allowedDevelopmentActions(resourceType));
    }

    private AiWorkflowContextDTO.Node toContextNode(DevelopmentItemWorkflowNodeDTO node,
                                                    String resourceType, int position) {
        List<AiWorkflowContextDTO.Field> fields = fields(node.getFields());
        List<String> actions = workflowComponentActions(resourceType);
        List<AiWorkflowContextDTO.Component> components = safe(node.getRuntimeComponents()).stream()
                .filter(StringUtils::hasText)
                .map(key -> new AiWorkflowContextDTO.Component(key, key, fields, actions)).toList();
        return new AiWorkflowContextDTO.Node(node.getId(), node.getNodeKey(), node.getName(), position,
                node.getStatus(), components, fields, jsonValues(node.getFieldValues()));
    }

    private List<String> allowedDevelopmentActions(String resourceType) {
        String identityKey = resourceType + "Id";
        return commandRegistry.list().stream()
                .sorted(Comparator.comparing(CommandName::code))
                .filter(name -> PmsCommandMetadata.resourceTypes(name).contains(resourceType))
                .filter(name -> PmsCommandMetadata.isWorkflowComponentAction(name)
                        || PmsCommandMetadata.descriptor(name).parameters().containsKey(identityKey))
                .filter(this::scopeAvailable)
                .map(CommandName::code)
                .toList();
    }

    private List<String> allowedProjectActions() {
        return commandRegistry.list().stream()
                .sorted(Comparator.comparing(CommandName::code))
                .filter(name -> PmsCommandMetadata.resourceTypes(name).stream()
                        .anyMatch(type -> Set.of("project", "project_node", "task").contains(type)))
                .filter(name -> PmsCommandMetadata.descriptor(name).parameters().containsKey("projectId"))
                .filter(this::scopeAvailable)
                .map(CommandName::code)
                .toList();
    }

    private List<String> workflowComponentActions(String resourceType) {
        return commandRegistry.list().stream()
                .sorted(Comparator.comparing(CommandName::code))
                .filter(PmsCommandMetadata::isWorkflowComponentAction)
                .filter(name -> PmsCommandMetadata.resourceTypes(name).contains(resourceType))
                .filter(this::scopeAvailable)
                .map(CommandName::code)
                .toList();
    }

    private boolean scopeAvailable(CommandName name) {
        PmsCommandDescriptor descriptor = PmsCommandMetadata.descriptor(name);
        return !UserContext.isDshDelegation()
                || descriptor.scopes().stream().allMatch(UserContext::hasDshScope);
    }

    private List<AiWorkflowContextDTO.Field> fields(List<WorkflowFieldDefinition> definitions) {
        return safe(definitions).stream().filter(Objects::nonNull).map(field -> new AiWorkflowContextDTO.Field(
                field.key(), field.label(), field.type() == null ? null : field.type().name(),
                field.required(), field.options(), field.visible(), field.binding(), field.fullWidth())).toList();
    }

    private Map<String, Object> jsonValues(Map<String, JsonNode> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (values == null) return result;
        values.forEach((key, value) -> result.put(key, jsonValue(value)));
        return result;
    }

    private Object jsonValue(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (value.isObject()) {
            Map<String, Object> result = new LinkedHashMap<>();
            value.fields().forEachRemaining(entry -> result.put(entry.getKey(), jsonValue(entry.getValue())));
            return result;
        }
        if (value.isArray()) {
            List<Object> result = new ArrayList<>();
            value.forEach(item -> result.add(jsonValue(item)));
            return result;
        }
        if (value.isBoolean()) return value.booleanValue();
        if (value.isIntegralNumber()) return value.longValue();
        if (value.isNumber()) return value.doubleValue();
        return value.asText();
    }

    private AiQueryResultDTO.Item projectItem(ProjectDTO item) {
        Map<String, Object> summary = new LinkedHashMap<>();
        put(summary, "name", item.getName());
        put(summary, "code", item.getCode()); put(summary, "description", item.getDescription());
        put(summary, "priority", item.getPriority()); put(summary, "projectManagerName", item.getProjectManagerName());
        put(summary, "currentNodeKey", item.getCurrentNodeKey()); put(summary, "currentNodeName", item.getCurrentNodeName());
        put(summary, "startDate", item.getStartDate()); put(summary, "endDate", item.getEndDate());
        return new AiQueryResultDTO.Item("project", item.getId(), item.getName(), string(item.getStatus()),
                item.getVersion(), summary);
    }

    private AiQueryResultDTO.Item requirementItem(RequirementListDTO item) {
        Map<String, Object> summary = new LinkedHashMap<>();
        put(summary, "description", item.getDescription()); put(summary, "priority", item.getPriority());
        put(summary, "ownerId", item.getOwnerId()); put(summary, "ownerName", item.getOwnerName());
        put(summary, "executionTarget", item.getExecutionTarget());
        return new AiQueryResultDTO.Item("requirement", item.getId(), item.getTitle(), item.getStatus(),
                item.getVersion(), summary);
    }

    private AiQueryResultDTO.Item topicItem(DevelopmentTopicListDTO item) {
        Map<String, Object> summary = new LinkedHashMap<>();
        put(summary, "projectId", item.getProjectId()); put(summary, "projectCode", item.getProjectCode());
        put(summary, "projectName", item.getProjectName()); put(summary, "nodeId", item.getNodeId());
        put(summary, "nodeName", item.getNodeName()); put(summary, "ownerId", item.getOwnerId());
        put(summary, "ownerName", item.getOwnerName()); put(summary, "progress", item.getProgress());
        put(summary, "storyCount", item.getStoryCount()); put(summary, "workflowStatus", item.getWorkflowStatus());
        return new AiQueryResultDTO.Item("topic", item.getId(), item.getTitle(), item.getStatus(), null, summary);
    }

    private AiQueryResultDTO.Item storyItem(DevelopmentStoryListDTO item) {
        Map<String, Object> summary = new LinkedHashMap<>();
        put(summary, "projectId", item.getProjectId()); put(summary, "projectName", item.getProjectName());
        put(summary, "topicId", item.getTopicId()); put(summary, "topicTitle", item.getTopicTitle());
        put(summary, "ownerId", item.getOwnerId()); put(summary, "ownerName", item.getOwnerName());
        put(summary, "progress", item.getProgress()); put(summary, "iterationPlanName", item.getIterationPlanName());
        put(summary, "storyPoints", item.getStoryPoints()); put(summary, "startDate", item.getStartDate());
        put(summary, "dueDate", item.getDueDate());
        return new AiQueryResultDTO.Item("story", item.getId(), item.getTitle(), item.getStatus(), null, summary);
    }

    private AiQueryResultDTO.Item iterationPlanItem(IterationPlanListDTO item) {
        Map<String, Object> summary = new LinkedHashMap<>();
        put(summary, "projectId", item.getProjectId()); put(summary, "projectName", item.getProjectName());
        put(summary, "nodeId", item.getNodeId()); put(summary, "nodeName", item.getNodeName());
        put(summary, "ownerId", item.getOwnerId()); put(summary, "ownerName", item.getOwnerName());
        put(summary, "goal", item.getGoal()); put(summary, "progress", item.getProgress());
        put(summary, "storyCount", item.getStoryCount()); put(summary, "taskCount", item.getTaskCount());
        return new AiQueryResultDTO.Item("iteration_plan", item.getId(), item.getName(), item.getStatus(), null, summary);
    }

    private AiQueryResultDTO.Item taskItem(AiTaskQueryResult.TaskItem item) {
        Map<String, Object> summary = new LinkedHashMap<>();
        put(summary, "projectId", item.projectId()); put(summary, "projectName", item.projectName());
        put(summary, "nodeId", item.nodeId()); put(summary, "nodeName", item.nodeName());
        put(summary, "parentId", item.parentId()); put(summary, "assigneeId", item.assigneeId());
        put(summary, "assigneeName", item.assigneeName()); put(summary, "statusLabel", item.statusLabel());
        put(summary, "priority", item.priority()); put(summary, "priorityLabel", item.priorityLabel());
        put(summary, "dueDate", item.dueDate());
        return new AiQueryResultDTO.Item(item.parentId() == null ? "task" : "subtask", item.id(), item.title(),
                item.statusLabel(), item.version(), summary);
    }

    private <T> AiQueryResultDTO pageResult(String resourceType, PageResult<T> result,
                                             List<AiQueryResultDTO.Item> items, int page, int pageSize) {
        return new AiQueryResultDTO(resourceType, items,
                result == null ? 0 : result.getTotal(),
                result == null ? page : result.getCurrPage(),
                result == null ? pageSize : result.getPageSize(),
                result == null ? 0 : result.getTotalPage(),
                "current-user-readable");
    }

    private String normalizeResourceType(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalized.isBlank()) throw BusinessException.error("resourceType 不能为空");
        return normalized;
    }

    private int page(Integer value) {
        if (value == null) return DEFAULT_PAGE;
        if (value < 1) throw BusinessException.error("page 必须大于等于 1");
        return value;
    }

    private int pageSize(Integer value) {
        if (value == null) return DEFAULT_PAGE_SIZE;
        if (value < 1 || value > MAX_PAGE_SIZE) throw BusinessException.error("pageSize 必须在 1 到 100 之间");
        return value;
    }

    private String text(Map<String, Object> filters, String key) {
        Object value = filters.get(key);
        return value == null ? null : trimToNull(String.valueOf(value));
    }

    private String valueOr(String value, String fallback) {
        return value == null ? fallback : value.toLowerCase(Locale.ROOT);
    }

    private Long longValue(Map<String, Object> filters, String key) {
        Object value = filters.get(key);
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try { return Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException exception) { throw BusinessException.error(key + " 必须是整数"); }
    }

    private Integer integer(Map<String, Object> filters, String key) {
        Long value = longValue(filters, key);
        return value == null ? null : Math.toIntExact(value);
    }

    private Boolean booleanValue(Map<String, Object> filters, String key) {
        Object value = filters.get(key);
        if (value == null) return null;
        if (value instanceof Boolean bool) return bool;
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(text)) return Boolean.FALSE;
        throw BusinessException.error(key + " 必须是布尔值");
    }

    private String string(Integer value) { return value == null ? null : String.valueOf(value); }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        return value.trim();
    }

    private <T> List<T> safe(List<T> values) { return values == null ? List.of() : values; }
}
