package com.brad.pms.ai.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.brad.pms.common.enums.Priority;
import com.brad.pms.common.enums.TaskStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.security.UserContext;
import com.brad.pms.service.ProjectService;
import com.brad.pms.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Permission-first, read-only query service used by the PMS AI bridge.
 * Work Helper never receives a database connection; every query comes back
 * through this service and is scoped to the current PMS user.
 */
@Service
@RequiredArgsConstructor
public class AiTaskQueryService {

    private static final ZoneId PMS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_PAGE_SIZE = 100;

    private final ProjectTaskMapper taskMapper;
    private final ProjectService projectService;
    private final ProjectNodeMapper nodeMapper;
    private final UserService userService;

    public AiTaskQueryResult query(AiTaskQueryRequest request) {
        QueryOptions options = normalize(request);
        LocalDate today = LocalDate.now(PMS_ZONE);
        if (options.scope().equals("project") && options.projectId() == null) {
            throw BusinessException.error("scope=project 必须提供 projectId");
        }
        List<Long> readableProjectIds = projectService.listReadableIds();
        if (readableProjectIds == null || readableProjectIds.isEmpty()) {
            return empty(options, today);
        }

        Set<Long> scopedProjectIds = new LinkedHashSet<>(readableProjectIds);
        if (options.projectId() != null) {
            if (!scopedProjectIds.contains(options.projectId())) {
                throw BusinessException.forbidden("无权读取该项目任务");
            }
            scopedProjectIds = new LinkedHashSet<>(List.of(options.projectId()));
        }
        LambdaQueryWrapper<ProjectTaskDO> wrapper = new LambdaQueryWrapper<ProjectTaskDO>()
                .in(ProjectTaskDO::getProjectId, scopedProjectIds)
                .orderByAsc(ProjectTaskDO::getDueDate)
                .orderByAsc(ProjectTaskDO::getId);
        if (options.scope().equals("mine")) {
            wrapper.eq(ProjectTaskDO::getAssigneeId, UserContext.userId());
        }
        if (options.nodeId() != null) {
            wrapper.eq(ProjectTaskDO::getNodeId, options.nodeId());
        }
        applyDueFilter(wrapper, options.due(), today);
        applyStatusFilter(wrapper, options.status());

        IPage<ProjectTaskDO> page = taskMapper.selectPage(
                new Page<>(options.page(), options.pageSize()), wrapper);
        List<ProjectTaskDO> records = page == null || page.getRecords() == null
                ? Collections.emptyList() : page.getRecords();
        return new AiTaskQueryResult(
                "task-query",
                true,
                PMS_ZONE.getId(),
                today,
                page == null ? 0 : page.getTotal(),
                options.page(),
                options.pageSize(),
                totalPage(page == null ? 0 : page.getTotal(), options.pageSize()),
                new AiTaskQueryResult.Pagination(
                        options.page(), options.pageSize(),
                        totalPage(page == null ? 0 : page.getTotal(), options.pageSize())),
                enrich(records));
    }

    private List<AiTaskQueryResult.TaskItem> enrich(List<ProjectTaskDO> records) {
        if (records.isEmpty()) return Collections.emptyList();

        Set<Long> projectIds = records.stream().map(ProjectTaskDO::getProjectId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, ProjectDTO> projects = projectService.listReadableByIds(projectIds).stream()
                .filter(Objects::nonNull)
                .filter(project -> project.getId() != null)
                .collect(Collectors.toMap(ProjectDTO::getId, Function.identity(), (left, right) -> left,
                        LinkedHashMap::new));

        Map<Long, ProjectNodeDO> nodes = nodeMapper.selectList(new LambdaQueryWrapper<ProjectNodeDO>()
                        .in(ProjectNodeDO::getProjectId, projectIds)).stream()
                .filter(Objects::nonNull).filter(node -> node.getId() != null)
                .collect(Collectors.toMap(ProjectNodeDO::getId, Function.identity(), (left, right) -> left));

        Set<Long> assigneeIds = records.stream().map(ProjectTaskDO::getAssigneeId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, UserDO> users = userService.listByIds(assigneeIds).stream()
                .filter(Objects::nonNull).filter(user -> user.getId() != null)
                .collect(Collectors.toMap(UserDO::getId, Function.identity(), (left, right) -> left));

        return records.stream().map(task -> {
            ProjectDTO project = projects.get(task.getProjectId());
            ProjectNodeDO node = nodes.get(task.getNodeId());
            UserDO assignee = users.get(task.getAssigneeId());
            Integer status = task.getStatus();
            Integer priority = task.getPriority();
            return new AiTaskQueryResult.TaskItem(
                    task.getId(), task.getVersion(), task.getProjectId(),
                    project == null ? null : project.getCode(),
                    project == null ? null : project.getName(),
                    task.getNodeId(), node == null ? null : node.getNodeKey(),
                    node == null ? null : node.getName(), task.getParentId(), task.getTitle(),
                    task.getAssigneeId(), Convertors.userDisplayName(assignee),
                    status, status == null ? null : TaskStatus.labelOf(status),
                    priority, priority == null ? null : Priority.labelOf(priority),
                    task.getDueDate());
        }).toList();
    }

    private AiTaskQueryResult empty(QueryOptions options, LocalDate today) {
        return new AiTaskQueryResult(
                "task-query", true, PMS_ZONE.getId(), today, 0,
                options.page(), options.pageSize(), 0,
                new AiTaskQueryResult.Pagination(options.page(), options.pageSize(), 0),
                Collections.emptyList());
    }

    private QueryOptions normalize(AiTaskQueryRequest request) {
        String scope = valueOr(request == null ? null : request.scope(), "mine");
        String due = valueOr(request == null ? null : request.due(), "any");
        String status = valueOr(request == null ? null : request.status(), "open");
        if (!Set.of("mine", "project").contains(scope)) {
            throw BusinessException.error("scope 只支持 mine 或 project");
        }
        if (!Set.of("today", "overdue", "upcoming", "any").contains(due)) {
            throw BusinessException.error("due 只支持 today、overdue、upcoming 或 any");
        }
        if (!Set.of("open", "done", "all").contains(status)) {
            throw BusinessException.error("status 只支持 open、done 或 all");
        }
        int page = request == null || request.page() == null ? 1 : request.page();
        int pageSize = request == null || request.pageSize() == null ? 50 : request.pageSize();
        if (page < 1) throw BusinessException.error("page 必须大于等于 1");
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw BusinessException.error("pageSize 必须在 1 到 100 之间");
        }
        return new QueryOptions(scope, due, status,
                request == null ? null : request.projectId(),
                request == null ? null : request.nodeId(), page, pageSize);
    }

    private void applyDueFilter(LambdaQueryWrapper<ProjectTaskDO> wrapper, String due, LocalDate today) {
        switch (due) {
            case "today" -> wrapper.eq(ProjectTaskDO::getDueDate, today);
            case "overdue" -> wrapper.lt(ProjectTaskDO::getDueDate, today);
            case "upcoming" -> wrapper.gt(ProjectTaskDO::getDueDate, today);
            default -> { }
        }
    }

    private void applyStatusFilter(LambdaQueryWrapper<ProjectTaskDO> wrapper, String status) {
        switch (status) {
            case "open" -> wrapper.ne(ProjectTaskDO::getStatus, TaskStatus.DONE.getCode());
            case "done" -> wrapper.eq(ProjectTaskDO::getStatus, TaskStatus.DONE.getCode());
            default -> { }
        }
    }

    private String valueOr(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private long totalPage(long total, int pageSize) {
        return total == 0 ? 0 : (total + pageSize - 1) / pageSize;
    }

    private record QueryOptions(String scope, String due, String status,
                                Long projectId, Long nodeId, int page, int pageSize) {
    }
}
