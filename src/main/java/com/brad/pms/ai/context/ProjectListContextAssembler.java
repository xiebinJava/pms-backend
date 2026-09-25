package com.brad.pms.ai.context;

import com.brad.pms.common.page.PageResult;
import com.brad.pms.common.enums.Priority;
import com.brad.pms.common.enums.ProjectStatus;
import com.brad.pms.dto.request.ProjectPageQry;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.dto.response.ProjectListSummaryDTO;
import com.brad.pms.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ProjectListContextAssembler implements PageContextAssembler {

    private static final int MAX_VISIBLE_PROJECTS = 100;
    private static final Map<String, String> STATUS_LEGEND = Map.of(
            "1", "进行中",
            "2", "已完成",
            "3", "已终止",
            "4", "已删除");
    private final ProjectService projectService;

    @Override
    public PageContextType supports() {
        return PageContextType.PROJECT_LIST;
    }

    @Override
    public PageContextSnapshot assemble(PageContextRequest request) {
        ProjectPageQry query = queryFrom(request.pageState());
        PageResult<ProjectDTO> page = projectService.page(query);
        List<ProjectDTO> projects = page == null || page.getList() == null
                ? List.of()
                : page.getList().stream().limit(MAX_VISIBLE_PROJECTS).toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dataScope", "current-page");
        data.put("authoritative", true);
        data.put("total", page == null ? 0L : page.getTotal());
        data.put("page", page == null ? 1L : page.getCurrPage());
        data.put("pageSize", page == null ? 0L : page.getPageSize());
        data.put("pagination", pagination(page));
        ProjectListSummaryDTO summary = projectService.listSummary(query);
        data.put("summary", summary == null ? new ProjectListSummaryDTO() : summary);
        List<Map<String, Object>> projectSummaries = projects.stream()
                .map(ProjectListContextAssembler::projectSummary)
                .toList();
        data.put("projects", projectSummaries);
        data.put("visibleCount", projectSummaries.size());
        data.put("priorityGroups", priorityGroups(projectSummaries));
        data.put("filters", filtersFrom(request.pageState()));
        data.put("statusLegend", STATUS_LEGEND);
        data.put("currentNodeRule", "已完成或已终止项目可能没有当前节点，当前节点为空时应明确说明。");
        data.put("priorityLegend", Map.of(
                "0", "低",
                "1", "中",
                "2", "高",
                "3", "紧急"));
        return ContextSnapshotFactory.create(request, "project-list", "page-" + data.get("page"), data);
    }

    private static Map<String, Object> pagination(PageResult<ProjectDTO> page) {
        Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("page", page == null ? 1L : page.getCurrPage());
        pagination.put("pageSize", page == null ? 0L : page.getPageSize());
        pagination.put("total", page == null ? 0L : page.getTotal());
        pagination.put("totalPage", page == null ? 0L : page.getTotalPage());
        return pagination;
    }

    private static Map<String, List<Map<String, Object>>> priorityGroups(
            List<Map<String, Object>> projects) {
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> project : projects) {
            Object label = project.get("priorityLabel");
            if (label == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", project.get("name"));
            item.put("code", project.get("code"));
            groups.computeIfAbsent(String.valueOf(label), ignored -> new java.util.ArrayList<>()).add(item);
        }
        return groups;
    }

    private static Map<String, Object> projectSummary(ProjectDTO project) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", project.getId());
        summary.put("code", project.getCode());
        summary.put("name", project.getName());
        summary.put("status", project.getStatus());
        summary.put("statusLabel", ProjectStatus.labelOf(ProjectStatus.normalize(project.getStatus())));
        summary.put("priority", project.getPriority());
        summary.put("priorityLabel", project.getPriority() == null
                ? null : Priority.labelOf(project.getPriority()));
        summary.put("projectLevel", project.getProjectLevel());
        summary.put("projectTypeId", project.getProjectTypeId());
        summary.put("workflowTemplateVersionId", project.getWorkflowTemplateVersionId());
        summary.put("description", project.getDescription());
        summary.put("ownerId", project.getOwnerId());
        summary.put("ownerName", project.getOwnerName());
        summary.put("projectManagerId", project.getProjectManagerId());
        summary.put("projectManagerName", project.getProjectManagerName());
        summary.put("orgUnitId", project.getOrgUnitId());
        summary.put("orgUnitName", project.getOrgUnitName());
        summary.put("orgUnitPath", project.getOrgUnitPath());
        summary.put("orgUnitLeaderName", project.getOrgUnitLeaderName());
        summary.put("startDate", project.getStartDate());
        summary.put("endDate", project.getEndDate());
        summary.put("taskCount", project.getTaskCount());
        summary.put("doneTaskCount", project.getDoneTaskCount());
        summary.put("memberCount", project.getMemberCount());
        summary.put("currentNodeKey", project.getCurrentNodeKey());
        summary.put("currentNodeName", project.getCurrentNodeName());
        summary.put("progress", project.getProgress());
        summary.put("createdAt", project.getCreatedAt());
        summary.put("updatedAt", project.getUpdatedAt());
        return summary;
    }

    private static ProjectPageQry queryFrom(Map<String, Object> state) {
        ProjectPageQry query = new ProjectPageQry();
        if (state == null) return query;
        query.setCurrPage(integer(state.get("page")) == null ? 1 : integer(state.get("page")));
        query.setPageSize(integer(state.get("pageSize")) == null ? 10 : integer(state.get("pageSize")));
        query.setKeyword(text(state.get("keyword")));
        query.setView(text(state.get("view")));
        query.setStatus(integer(state.get("status")));
        query.setOrgUnitId(longValue(state.get("orgUnitId")));
        query.setProjectManagerId(longValue(state.get("projectManagerId")));
        query.setProjectLevel(integer(state.get("projectLevel")));
        query.setPriority(integer(state.get("priority")));
        query.setAttention(text(state.get("attention")));
        query.setCurrentNodeKey(text(state.get("currentNodeKey")));
        return query;
    }

    private static Map<String, Object> filtersFrom(Map<String, Object> state) {
        if (state == null || state.isEmpty()) return Map.of();
        Map<String, Object> filters = new LinkedHashMap<>();
        for (String key : List.of("keyword", "view", "status", "orgUnitId", "projectManagerId",
                "projectLevel", "priority", "attention", "currentNodeKey")) {
            if (state.containsKey(key) && state.get(key) != null) filters.put(key, state.get(key));
        }
        return filters;
    }

    private static String text(Object value) {
        return value instanceof String string && !string.isBlank() ? string : null;
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String string) {
            try { return Integer.valueOf(string); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String string) {
            try { return Long.valueOf(string); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }
}
