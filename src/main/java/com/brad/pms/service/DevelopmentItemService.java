package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.common.enums.RequirementExecutionTargetType;
import com.brad.pms.common.page.PageResult;
import com.brad.pms.dto.request.DevelopmentItemPageQry;
import com.brad.pms.dto.request.RequirementPageQry;
import com.brad.pms.dto.response.DevelopmentStoryListDTO;
import com.brad.pms.dto.response.DevelopmentTopicListDTO;
import com.brad.pms.dto.response.RequirementListDTO;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import com.brad.pms.entity.DevelopmentItemWorkflowNodeDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.mapper.DevelopmentItemWorkflowMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowNodeMapper;
import com.brad.pms.mapper.DevelopmentPageMapper;
import com.brad.pms.mapper.DevelopmentPageMapper.Scope;
import com.brad.pms.mapper.DevelopmentPageMapper.SearchText;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.workflow.DevelopmentItemType;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.cursor.Cursor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DevelopmentItemService {
    private final DevelopmentPageMapper pages;
    private final ProjectService projectService;
    private final ProjectMapper projectMapper;
    private final ProjectPermissionService permissionService;
    private final DevelopmentItemWorkflowMapper workflowMapper;
    private final DevelopmentItemWorkflowNodeMapper workflowNodeMapper;
    private final ProjectNodeDevelopmentStoryMapper storyMapper;
    private final WorkflowTemplateService workflowTemplateService;
    private final RequirementManagementService requirementManagementService;
    private final RequirementExecutionTargetReadService requirementTargetReadService;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<DevelopmentTopicListDTO> pageTopics(DevelopmentItemPageQry qry) {
        var query = qry == null ? new DevelopmentItemPageQry() : qry;
        var scope = unicodeScope(scope(query, Boolean.TRUE.equals(query.getDeleted())), pages::unicodeTopicSearchText);
        long total = pages.countTopics(scope);
        if (scope.offset() >= total) return PageResult.of(total, query.getCurrPage(), query.getPageSize(), List.of());
        var ids = pages.topicIds(scope);
        var topics = ordered(pages.topics(ids, scope.projects()), ids, DevelopmentTopicListDTO::getId);
        var summaries = workflowSummaries(DevelopmentItemType.TOPIC, ids);
        var sources = requirementTargetReadService.findDirectSourcesForTargets(RequirementExecutionTargetType.TOPIC, ids);
        var children = storyMapper.selectList(new LambdaQueryWrapper<ProjectNodeDevelopmentStoryDO>()
                .select(ProjectNodeDevelopmentStoryDO::getTopicId, ProjectNodeDevelopmentStoryDO::getStatus,
                        ProjectNodeDevelopmentStoryDO::getProgress, ProjectNodeDevelopmentStoryDO::getBlocker)
                .in(ProjectNodeDevelopmentStoryDO::getTopicId, ids)
                .orderByAsc(ProjectNodeDevelopmentStoryDO::getSort, ProjectNodeDevelopmentStoryDO::getId))
                .stream().collect(Collectors.groupingBy(ProjectNodeDevelopmentStoryDO::getTopicId));
        for (var topic : topics) {
            var stories = children.getOrDefault(topic.getId(), List.of());
            topic.setStatus(deriveTopicStatus(stories));
            topic.setDevelopmentProgress(averageProgress(stories));
            topic.setStoryCount(stories.size());
            topic.setCompletedStoryCount((int) stories.stream().filter(s -> "DONE".equals(s.getStatus())).count());
            topic.setBlockedStoryCount((int) stories.stream().filter(s -> "BLOCKED".equals(s.getStatus())).count());
            topic.setBlocker(stories.stream().filter(s -> "BLOCKED".equals(s.getStatus()))
                    .map(ProjectNodeDevelopmentStoryDO::getBlocker).filter(StringUtils::hasText).findFirst().orElse(null));
            var flow = summaries.get(topic.getId());
            topic.setProgress(flow.progress());
            topic.setWorkflowConfigured(flow.configured());
            topic.setWorkflowStatus(flow.status());
            var direct = sources.getOrDefault(topic.getId(), List.of());
            topic.setSourceRequirements(direct);
            topic.setSourceRequirement(direct.isEmpty() ? null : direct.get(0));
        }
        return PageResult.of(total, query.getCurrPage(), query.getPageSize(), topics);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<DevelopmentStoryListDTO> pageStories(DevelopmentItemPageQry qry) {
        var query = qry == null ? new DevelopmentItemPageQry() : qry;
        var scope = unicodeScope(scope(query, false), pages::unicodeStorySearchText);
        long total = pages.countStories(scope);
        if (scope.offset() >= total) return PageResult.of(total, query.getCurrPage(), query.getPageSize(), List.of());
        var ids = pages.storyIds(scope);
        var stories = ordered(pages.stories(ids, scope.projects()), ids, DevelopmentStoryListDTO::getId);
        var summaries = workflowSummaries(DevelopmentItemType.STORY, ids);
        var sources = requirementTargetReadService.findDirectSourcesForTargets(RequirementExecutionTargetType.STORY, ids);
        for (var story : stories) {
            var flow = summaries.get(story.getId());
            story.setProgress(flow.progress());
            story.setWorkflowConfigured(flow.configured());
            story.setWorkflowStatus(flow.status());
            var direct = sources.getOrDefault(story.getId(), List.of());
            story.setSourceRequirements(direct);
            story.setSourceRequirement(direct.isEmpty() ? null : direct.get(0));
        }
        return PageResult.of(total, query.getCurrPage(), query.getPageSize(), stories);
    }

    public PageResult<RequirementListDTO> pageRequirements(RequirementPageQry qry) {
        return requirementManagementService.page(qry);
    }

    private Scope scope(DevelopmentItemPageQry query, boolean recycleBin) {
        if (recycleBin) {
            var ids = pages.deletedTopicProjects(query.getProjectId());
            var allowed = ids.isEmpty() ? List.<Long>of() : permissionService.manageableProjectIds(
                    projectMapper.selectIncludingDeletedByIds(ids));
            return new Scope(query, allowed);
        }
        var readable = projectService.listReadableIds();
        if (query.getProjectId() == null) return new Scope(query, readable);
        return new Scope(query, readable.contains(query.getProjectId()) ? List.of(query.getProjectId()) : List.of());
    }

    private Scope unicodeScope(Scope scope, Function<Scope, Cursor<SearchText>> search) {
        if (scope.keyword() == null) return scope;
        // Java and MySQL differ on Unicode case mappings. Evaluate only rows with
        // non-ASCII cased text here, before the database counts and selects the page.
        var additional = new StringJoiner(",", "[", "]");
        var excluded = new StringJoiner(",", "[", "]");
        try (var candidates = search.apply(scope)) {
            for (var row : candidates) {
                boolean match = Stream.of(row.title(), row.projectName(), row.projectCode(),
                                row.nodeName(), row.ownerName(), row.topicTitle())
                        .filter(Objects::nonNull)
                        .anyMatch(text -> text.toLowerCase(Locale.ROOT).contains(scope.keyword()));
                if (match != row.sqlMatch()) {
                    (match ? additional : excluded).add(Long.toString(row.id()));
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return scope.withAdjustedMatches(additional.toString(), excluded.toString());
    }

    private static <T> List<T> ordered(List<T> rows, List<Long> ids, Function<T, Long> id) {
        var byId = rows.stream().collect(Collectors.toMap(id, Function.identity()));
        return ids.stream().map(byId::get).toList();
    }

    private Map<Long, FlowSummary> workflowSummaries(DevelopmentItemType itemType, List<Long> itemIds) {
        boolean defaultConfigured = workflowTemplateService.resolveDefaultForProcessType(itemType.processTypeCode()) != null;
        Map<Long, FlowSummary> summaries = new HashMap<>();
        List<Long> ids = itemIds.stream().filter(Objects::nonNull).distinct().toList();
        if (!ids.isEmpty()) {
            List<DevelopmentItemWorkflowDO> workflows = workflowMapper.selectList(new LambdaQueryWrapper<DevelopmentItemWorkflowDO>()
                    .eq(DevelopmentItemWorkflowDO::getItemType, itemType.name())
                    .in(DevelopmentItemWorkflowDO::getItemId, ids));
            List<Long> workflowIds = workflows.stream().map(DevelopmentItemWorkflowDO::getId).filter(Objects::nonNull).toList();
            Map<Long, List<DevelopmentItemWorkflowNodeDO>> nodesByWorkflow = workflowIds.isEmpty() ? Map.of()
                    : workflowNodeMapper.selectList(new LambdaQueryWrapper<DevelopmentItemWorkflowNodeDO>()
                                    .in(DevelopmentItemWorkflowNodeDO::getWorkflowId, workflowIds))
                            .stream().collect(Collectors.groupingBy(DevelopmentItemWorkflowNodeDO::getWorkflowId));
            for (DevelopmentItemWorkflowDO workflow : workflows) {
                List<DevelopmentItemWorkflowNodeDO> nodes = nodesByWorkflow.getOrDefault(workflow.getId(), List.of());
                int total = nodes.size();
                int completed = (int) nodes.stream().filter(node -> Objects.equals(node.getStatus(), 2)).count();
                String status = total > 0 && completed == total ? "COMPLETED"
                        : nodes.stream().anyMatch(node -> Objects.equals(node.getStatus(), 1)) ? "IN_PROGRESS" : "NOT_STARTED";
                int progress = total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);
                summaries.put(workflow.getItemId(), new FlowSummary(true, status, progress));
            }
        }
        for (Long id : ids) summaries.putIfAbsent(id,
                new FlowSummary(defaultConfigured, defaultConfigured ? "NOT_STARTED" : "NOT_CONFIGURED", 0));
        return summaries;
    }

    private int averageProgress(List<ProjectNodeDevelopmentStoryDO> stories) {
        if (stories.isEmpty()) return 0;
        return (int) Math.round(stories.stream().mapToInt(story -> effectiveProgress(story.getStatus(), story.getProgress())).average().orElse(0));
    }

    private String deriveTopicStatus(List<ProjectNodeDevelopmentStoryDO> stories) {
        if (stories.isEmpty()) return "NOT_STARTED";
        if (stories.stream().allMatch(story -> "DONE".equals(story.getStatus()))) return "DONE";
        if (stories.stream().anyMatch(story -> !"NOT_STARTED".equals(story.getStatus()))) return "IN_PROGRESS";
        return "NOT_STARTED";
    }

    private int effectiveProgress(String status, Integer progress) {
        if ("DONE".equals(status)) return 100;
        if ("NOT_STARTED".equals(status)) return 0;
        return progress == null ? 0 : Math.max(0, Math.min(100, progress));
    }

    private record FlowSummary(boolean configured, String status, Integer progress) {}
}
