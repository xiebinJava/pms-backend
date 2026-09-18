package com.brad.pms.ai.query;

import com.brad.pms.ai.context.PageContextRequest;
import com.brad.pms.ai.context.PageContextService;
import com.brad.pms.ai.context.PageContextSnapshot;
import com.brad.pms.ai.context.PageContextType;
import com.brad.pms.common.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Routes bounded DSH queries to existing permission-aware PMS read services. */
@Service
@RequiredArgsConstructor
public class DshQueryService {

    private static final Set<String> PROJECT_FILTERS = Set.of(
            "keyword", "status", "view", "orgUnitId", "projectManagerId", "projectLevel",
            "priority", "attention", "currentNodeKey");
    private static final Set<String> PROJECT_FIELDS = Set.of(
            "id", "code", "name", "status", "statusLabel", "priority", "priorityLabel",
            "projectLevel", "projectTypeId", "description", "ownerId", "ownerName",
            "projectManagerId", "projectManagerName", "orgUnitId", "orgUnitName",
            "orgUnitPath", "orgUnitLeaderName", "startDate", "endDate", "taskCount",
            "doneTaskCount", "memberCount", "currentNodeKey", "currentNodeName", "progress",
            "createdAt", "updatedAt");
    private static final Set<String> TASK_FILTERS = Set.of("projectId", "nodeId", "due", "status");
    private static final Set<String> TASK_FIELDS = Set.of(
            "id", "version", "projectId", "projectCode", "projectName", "nodeId", "nodeKey",
            "nodeName", "parentId", "title", "assigneeId", "assigneeName", "status",
            "statusLabel", "priority", "priorityLabel", "dueDate");

    private final PageContextService pageContextService;
    private final AiTaskQueryService taskQueryService;
    private final ObjectMapper objectMapper;

    public DshQueryResult query(DshQueryRequest request) {
        if (request == null) throw BusinessException.error("查询请求不能为空");
        return switch (request.resource()) {
            case "projects" -> queryProjects(request);
            case "tasks" -> queryTasks(request);
            default -> throw BusinessException.error("不支持的查询资源: " + request.resource());
        };
    }

    private DshQueryResult queryProjects(DshQueryRequest request) {
        validateFilters(request.filters(), PROJECT_FILTERS);
        validateFields(request.fields(), PROJECT_FIELDS);
        Map<String, Object> state = new LinkedHashMap<>(request.filters());
        state.put("page", request.page());
        state.put("pageSize", request.pageSize());
        PageContextSnapshot snapshot = pageContextService.assemble(new PageContextRequest(
                PageContextType.PROJECT_LIST, "/projects", null, null, state));
        Map<String, Object> data = new LinkedHashMap<>(snapshot.data());
        if (!request.fields().isEmpty()) {
            data.put("projects", projectProjection(data.get("projects"), request.fields()));
        }
        return new DshQueryResult("projects", true, snapshot.capturedAt(), data,
                mapValue(snapshot.data().get("pagination")));
    }

    private DshQueryResult queryTasks(DshQueryRequest request) {
        validateFilters(request.filters(), TASK_FILTERS);
        validateFields(request.fields(), TASK_FIELDS);
        Long projectId = longValue(request.filters().get("projectId"));
        if (projectId == null) throw BusinessException.error("查询 tasks 必须提供 projectId");
        Long nodeId = longValue(request.filters().get("nodeId"));
        String due = text(request.filters().get("due"), "any");
        if ("all".equalsIgnoreCase(due)) due = "any";
        String status = text(request.filters().get("status"), "all");
        AiTaskQueryResult result = taskQueryService.query(new AiTaskQueryRequest(
                "project", due, status, projectId, nodeId, request.page(), request.pageSize()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dataScope", result.dataScope());
        data.put("authoritative", result.authoritative());
        data.put("timezone", result.timezone());
        data.put("asOfDate", result.asOfDate());
        data.put("total", result.total());
        data.put("tasks", taskProjection(result.tasks(), request.fields()));
        Map<String, Object> pagination = Map.of(
                "page", result.page(),
                "pageSize", result.pageSize(),
                "totalPage", result.totalPage(),
                "total", result.total());
        return new DshQueryResult("tasks", true, Instant.now(), data, pagination);
    }

    private List<Map<String, Object>> projectProjection(Object raw, List<String> fields) {
        return projectMaps(raw, fields);
    }

    private List<Map<String, Object>> taskProjection(List<?> records, List<String> fields) {
        List<Map<String, Object>> maps = objectMapper.convertValue(records, new TypeReference<>() { });
        return fields.isEmpty() ? maps : maps.stream().map(item -> select(item, fields)).toList();
    }

    private List<Map<String, Object>> projectMaps(Object raw, List<String> fields) {
        if (!(raw instanceof List<?> records)) return List.of();
        List<Map<String, Object>> maps = new ArrayList<>();
        for (Object record : records) {
            if (!(record instanceof Map<?, ?> map)) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            map.forEach((key, value) -> item.put(String.valueOf(key), value));
            maps.add(fields.isEmpty() ? item : select(item, fields));
        }
        return maps;
    }

    private Map<String, Object> select(Map<String, Object> item, List<String> fields) {
        Map<String, Object> selected = new LinkedHashMap<>();
        for (String field : fields) if (item.containsKey(field)) selected.put(field, item.get(field));
        return selected;
    }

    private void validateFilters(Map<String, Object> filters, Set<String> allowed) {
        for (String key : filters.keySet()) {
            if (!allowed.contains(key)) throw BusinessException.error("不允许的过滤条件: " + key);
        }
    }

    private void validateFields(List<String> fields, Set<String> allowed) {
        for (String field : fields) {
            if (!allowed.contains(field)) throw BusinessException.error("不允许的字段: " + field);
        }
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String string) {
            try { return Long.valueOf(string); } catch (NumberFormatException ignored) { }
        }
        return null;
    }

    private String text(Object value, String fallback) {
        return value instanceof String string && !string.isBlank() ? string.trim().toLowerCase() : fallback;
    }
}
