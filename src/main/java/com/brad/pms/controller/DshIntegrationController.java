package com.brad.pms.controller;

import com.brad.pms.ai.context.PageContextRequest;
import com.brad.pms.ai.context.PageContextService;
import com.brad.pms.ai.context.PageContextSnapshot;
import com.brad.pms.ai.context.PageContextType;
import com.brad.pms.ai.query.AiTaskQueryRequest;
import com.brad.pms.ai.query.AiTaskQueryResult;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.ProjectPageQry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stable read-only facade used by the DSH PMS plugin.
 *
 * <p>The facade intentionally delegates to the existing authoritative page
 * context and task query services. It is an integration boundary, not a
 * second implementation of PMS business rules.</p>
 */
@RestController
@RequestMapping("/integration/dsh/v1")
@RequiredArgsConstructor
public class DshIntegrationController {

    private final PageContextService pageContextService;
    private final com.brad.pms.ai.query.AiTaskQueryService taskQueryService;

    @GetMapping("/projects")
    public ResponseResult<PageContextSnapshot> projects(@ModelAttribute ProjectPageQry query) {
        Map<String, Object> state = new LinkedHashMap<>();
        put(state, "page", query.getCurrPage());
        put(state, "pageSize", query.getPageSize());
        put(state, "keyword", query.getKeyword());
        put(state, "status", query.getStatus());
        put(state, "view", query.getView());
        put(state, "orgUnitId", query.getOrgUnitId());
        put(state, "projectManagerId", query.getProjectManagerId());
        put(state, "projectLevel", query.getProjectLevel());
        put(state, "priority", query.getPriority());
        put(state, "attention", query.getAttention());
        put(state, "currentNodeKey", query.getCurrentNodeKey());
        return ResponseResult.success(pageContextService.assemble(new PageContextRequest(
                PageContextType.PROJECT_LIST,
                "/projects",
                null,
                null,
                state)));
    }

    @GetMapping("/projects/{projectId}")
    public ResponseResult<PageContextSnapshot> project(
            @PathVariable Long projectId,
            @RequestParam(required = false) Long nodeId) {
        return ResponseResult.success(pageContextService.assemble(new PageContextRequest(
                PageContextType.PROJECT_DETAIL,
                "/projects/" + projectId,
                projectId,
                nodeId,
                Map.of())));
    }

    @GetMapping("/projects/{projectId}/tasks")
    public ResponseResult<AiTaskQueryResult> tasks(
            @PathVariable Long projectId,
            @RequestParam(required = false) Long nodeId,
            @RequestParam(required = false) String due,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        String normalizedDue = "all".equalsIgnoreCase(due) ? "any" : due;
        return ResponseResult.success(taskQueryService.query(new AiTaskQueryRequest(
                "project",
                normalizedDue,
                status == null ? "all" : status,
                projectId,
                nodeId,
                page,
                pageSize)));
    }

    private static void put(Map<String, Object> state, String key, Object value) {
        if (value != null) state.put(key, value);
    }
}
