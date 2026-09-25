package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.common.page.PageResult;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.request.DevelopmentItemPageQry;
import com.brad.pms.dto.response.DevelopmentStoryListDTO;
import com.brad.pms.dto.response.DevelopmentTopicListDTO;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.entity.ProjectNodeIterationPlanDO;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import com.brad.pms.entity.DevelopmentItemWorkflowNodeDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.DevelopmentItemWorkflowMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowNodeMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.workflow.DevelopmentItemType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DevelopmentItemService {

    private final ProjectService projectService;
    private final ProjectMapper projectMapper;
    private final ProjectPermissionService permissionService;
    private final ProjectNodeMapper nodeMapper;
    private final DevelopmentItemWorkflowMapper workflowMapper;
    private final DevelopmentItemWorkflowNodeMapper workflowNodeMapper;
    private final ProjectNodeDevelopmentTopicMapper topicMapper;
    private final ProjectNodeDevelopmentStoryMapper storyMapper;
    private final ProjectNodeIterationPlanMapper iterationPlanMapper;
    private final UserService userService;
    private final WorkflowTemplateService workflowTemplateService;

    public PageResult<DevelopmentTopicListDTO> pageTopics(DevelopmentItemPageQry qry) {
        DevelopmentItemPageQry query = qry == null ? new DevelopmentItemPageQry() : qry;
        QueryContext context = loadContext(query.getProjectId(), Boolean.TRUE.equals(query.getDeleted()));

        Map<Long, List<ProjectNodeDevelopmentStoryDO>> storiesByTopic = context.stories().stream()
                .filter(story -> story.getTopicId() != null)
                .collect(Collectors.groupingBy(ProjectNodeDevelopmentStoryDO::getTopicId));
        Map<Long, FlowSummary> workflowSummaries = workflowSummaries(DevelopmentItemType.TOPIC,
                context.topics().stream().map(ProjectNodeDevelopmentTopicDO::getId).toList());
        List<DevelopmentTopicListDTO> items = context.topics().stream()
                .map(topic -> toTopic(topic, storiesByTopic.getOrDefault(topic.getId(), List.of()), context,
                        workflowSummaries.get(topic.getId())))
                .filter(item -> matchesTopic(item, query))
                .sorted(topicComparator(context))
                .toList();
        return page(items, query);
    }

    public PageResult<DevelopmentStoryListDTO> pageStories(DevelopmentItemPageQry qry) {
        DevelopmentItemPageQry query = qry == null ? new DevelopmentItemPageQry() : qry;
        QueryContext context = loadContext(query.getProjectId(), false);

        Map<Long, FlowSummary> workflowSummaries = workflowSummaries(DevelopmentItemType.STORY,
                context.stories().stream().map(ProjectNodeDevelopmentStoryDO::getId).toList());
        List<DevelopmentStoryListDTO> items = context.stories().stream()
                .map(story -> toStory(story, context, workflowSummaries.get(story.getId())))
                .filter(item -> matchesStory(item, query))
                .sorted(storyComparator(context))
                .toList();
        return page(items, query);
    }

    private QueryContext loadContext(Long projectId, boolean deletedTopicScope) {
        List<ProjectNodeDevelopmentTopicDO> topics;
        Map<Long, ProjectDTO> projectsById;
        List<Long> projectIds;
        if (deletedTopicScope) {
            LambdaQueryWrapper<ProjectNodeDevelopmentTopicDO> topicQuery = new LambdaQueryWrapper<ProjectNodeDevelopmentTopicDO>()
                    .eq(ProjectNodeDevelopmentTopicDO::getDeleted, true)
                    .orderByAsc(ProjectNodeDevelopmentTopicDO::getSort)
                    .orderByAsc(ProjectNodeDevelopmentTopicDO::getId);
            if (projectId != null) topicQuery.eq(ProjectNodeDevelopmentTopicDO::getProjectId, projectId);
            else topicQuery.isNotNull(ProjectNodeDevelopmentTopicDO::getProjectId);
            topics = safeList(topicMapper.selectList(topicQuery));
            if (projectId == null) topics = new ArrayList<>(topics);
            if (projectId == null) topics.addAll(safeList(topicMapper.selectUnbound(true)));
            if (topics.isEmpty()) return QueryContext.empty();
            List<Long> referencedProjectIds = topics.stream().map(ProjectNodeDevelopmentTopicDO::getProjectId)
                    .filter(Objects::nonNull).distinct().toList();
            projectsById = safeList(referencedProjectIds.isEmpty()
                            ? List.of() : projectMapper.selectIncludingDeletedByIds(referencedProjectIds)).stream()
                    .filter(permissionService::canManageProject)
                    .map(this::toProjectSummary)
                    .collect(Collectors.toMap(ProjectDTO::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
            topics = topics.stream().filter(topic -> topic.getProjectId() == null
                    || projectsById.containsKey(topic.getProjectId())).toList();
            projectIds = projectsById.keySet().stream().toList();
        } else {
            List<Long> readableIds = safeIds(projectService.listReadableIds());
            if (projectId != null) {
                if (!readableIds.contains(projectId)) return QueryContext.empty();
                readableIds = List.of(projectId);
            }
            List<ProjectDTO> projects = readableIds.isEmpty() ? List.of() : safeList(projectService.listReadableByIds(readableIds));
            projectsById = projects.stream().filter(project -> project.getId() != null)
                    .collect(Collectors.toMap(ProjectDTO::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
            projectIds = projectsById.keySet().stream().toList();
            List<ProjectNodeDevelopmentTopicDO> boundTopics = projectIds.isEmpty() ? List.of()
                    : safeList(topicMapper.selectList(new LambdaQueryWrapper<ProjectNodeDevelopmentTopicDO>()
                            .in(ProjectNodeDevelopmentTopicDO::getProjectId, projectIds)
                            .eq(ProjectNodeDevelopmentTopicDO::getDeleted, false)
                            .orderByAsc(ProjectNodeDevelopmentTopicDO::getSort)
                            .orderByAsc(ProjectNodeDevelopmentTopicDO::getId)));
            topics = projectId == null ? new ArrayList<>(boundTopics) : new ArrayList<>(boundTopics);
            if (projectId == null) topics.addAll(safeList(topicMapper.selectUnbound(false)));
        }

        List<ProjectNodeDO> nodes = projectIds.isEmpty() ? List.of()
                : safeList(nodeMapper.selectList(new LambdaQueryWrapper<ProjectNodeDO>()
                        .in(ProjectNodeDO::getProjectId, projectIds)
                        .orderByAsc(ProjectNodeDO::getSort)
                        .orderByAsc(ProjectNodeDO::getId)));
        List<Long> topicIds = topics.stream().map(ProjectNodeDevelopmentTopicDO::getId).filter(Objects::nonNull).toList();
        List<ProjectNodeDevelopmentStoryDO> stories = new ArrayList<>(topicIds.isEmpty() ? List.of()
                : safeList(storyMapper.selectList(new LambdaQueryWrapper<ProjectNodeDevelopmentStoryDO>()
                        .in(ProjectNodeDevelopmentStoryDO::getTopicId, topicIds)
                        .orderByAsc(ProjectNodeDevelopmentStoryDO::getSort)
                        .orderByAsc(ProjectNodeDevelopmentStoryDO::getId))));
        if (!deletedTopicScope && projectId == null) stories.addAll(safeList(storyMapper.selectIndependent()));
        List<ProjectNodeIterationPlanDO> plans = projectIds.isEmpty() ? List.of()
                : safeList(iterationPlanMapper.selectList(new LambdaQueryWrapper<ProjectNodeIterationPlanDO>()
                        .in(ProjectNodeIterationPlanDO::getProjectId, projectIds)
                        .orderByAsc(ProjectNodeIterationPlanDO::getSort)
                        .orderByAsc(ProjectNodeIterationPlanDO::getId)));

        Set<Long> ownerIds = new java.util.HashSet<>();
        topics.stream().map(ProjectNodeDevelopmentTopicDO::getOwnerId).filter(Objects::nonNull).forEach(ownerIds::add);
        stories.stream().map(ProjectNodeDevelopmentStoryDO::getOwnerId).filter(Objects::nonNull).forEach(ownerIds::add);
        Map<Long, UserDO> users = userMap(ownerIds);
        List<Long> topicWorkflowNodeIds = stories.stream().map(ProjectNodeDevelopmentStoryDO::getTopicWorkflowNodeId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> topicWorkflowNodeNames = topicWorkflowNodeIds.isEmpty() ? Map.of()
                : safeList(workflowNodeMapper.selectList(new LambdaQueryWrapper<DevelopmentItemWorkflowNodeDO>()
                                .in(DevelopmentItemWorkflowNodeDO::getId, topicWorkflowNodeIds))).stream()
                        .collect(Collectors.toMap(DevelopmentItemWorkflowNodeDO::getId,
                                DevelopmentItemWorkflowNodeDO::getName, (left, right) -> left, LinkedHashMap::new));
        return new QueryContext(
                topics,
                stories,
                projectsById,
                nodes.stream().filter(node -> node.getId() != null).collect(Collectors.toMap(ProjectNodeDO::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new)),
                users,
                topicWorkflowNodeNames,
                plans.stream().filter(plan -> plan.getId() != null).collect(Collectors.toMap(ProjectNodeIterationPlanDO::getId, ProjectNodeIterationPlanDO::getName, (left, right) -> left, LinkedHashMap::new))
        );
    }

    private ProjectDTO toProjectSummary(ProjectDO project) {
        ProjectDTO dto = new ProjectDTO();
        dto.setId(project.getId());
        dto.setCode(project.getCode());
        dto.setName(project.getName());
        dto.setStatus(project.getStatus());
        return dto;
    }

    private DevelopmentTopicListDTO toTopic(ProjectNodeDevelopmentTopicDO topic,
                                             List<ProjectNodeDevelopmentStoryDO> stories,
                                             QueryContext context,
                                             FlowSummary workflowSummary) {
        ProjectDTO project = context.projects().get(topic.getProjectId());
        ProjectNodeDO node = context.nodes().get(topic.getNodeId());
        DevelopmentTopicListDTO dto = new DevelopmentTopicListDTO();
        dto.setId(topic.getId());
        dto.setTitle(topic.getTitle());
        dto.setProjectId(topic.getProjectId());
        dto.setProjectCode(project == null ? null : project.getCode());
        dto.setProjectName(project == null ? null : project.getName());
        dto.setNodeId(topic.getNodeId());
        dto.setNodeKey(node == null ? null : node.getNodeKey());
        dto.setNodeName(node == null ? null : node.getName());
        dto.setOwnerId(topic.getOwnerId());
        dto.setOwnerName(displayName(context.users().get(topic.getOwnerId())));
        dto.setStatus(deriveTopicStatus(stories));
        dto.setDevelopmentProgress(averageProgress(stories));
        dto.setProgress(workflowSummary.progress());
        dto.setWorkflowConfigured(workflowSummary.configured());
        dto.setWorkflowStatus(workflowSummary.status());
        dto.setStoryCount(stories.size());
        dto.setCompletedStoryCount((int) stories.stream().filter(story -> "DONE".equals(story.getStatus())).count());
        dto.setBlockedStoryCount((int) stories.stream().filter(story -> "BLOCKED".equals(story.getStatus())).count());
        dto.setLatestBuildVersion(topic.getLatestBuildVersion());
        dto.setTestStatus(topic.getTestStatus());
        dto.setBlocker(stories.stream().filter(story -> "BLOCKED".equals(story.getStatus()))
                .map(ProjectNodeDevelopmentStoryDO::getBlocker).filter(StringUtils::hasText).findFirst().orElse(null));
        return dto;
    }

    private DevelopmentStoryListDTO toStory(ProjectNodeDevelopmentStoryDO story, QueryContext context, FlowSummary workflowSummary) {
        ProjectDTO project = context.projects().get(story.getProjectId());
        ProjectNodeDO node = context.nodes().get(story.getNodeId());
        ProjectNodeDevelopmentTopicDO topic = context.topics().stream()
                .filter(item -> Objects.equals(item.getId(), story.getTopicId())).findFirst().orElse(null);
        DevelopmentStoryListDTO dto = new DevelopmentStoryListDTO();
        dto.setId(story.getId());
        dto.setTitle(story.getTitle());
        dto.setProjectId(story.getProjectId());
        dto.setProjectCode(project == null ? null : project.getCode());
        dto.setProjectName(project == null ? null : project.getName());
        dto.setNodeId(story.getNodeId());
        dto.setNodeKey(node == null ? null : node.getNodeKey());
        dto.setNodeName(node == null ? null : node.getName());
        dto.setTopicId(story.getTopicId());
        dto.setTopicTitle(topic == null ? null : topic.getTitle());
        dto.setTopicWorkflowNodeId(story.getTopicWorkflowNodeId());
        dto.setTopicWorkflowNodeName(story.getTopicWorkflowNodeId() == null ? null
                : context.topicWorkflowNodeNames().get(story.getTopicWorkflowNodeId()));
        dto.setOwnerId(story.getOwnerId());
        dto.setOwnerName(displayName(context.users().get(story.getOwnerId())));
        dto.setIterationPlanName(context.iterationPlans().get(story.getIterationPlanId()));
        dto.setStatus(story.getStatus());
        dto.setDevelopmentProgress(effectiveProgress(story.getStatus(), story.getProgress()));
        dto.setProgress(workflowSummary.progress());
        dto.setWorkflowConfigured(workflowSummary.configured());
        dto.setWorkflowStatus(workflowSummary.status());
        dto.setStoryPoints(story.getStoryPoints());
        dto.setStartDate(story.getStartDate());
        dto.setDueDate(story.getDueDate());
        dto.setBlocker(story.getBlocker());
        return dto;
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

    private boolean matchesTopic(DevelopmentTopicListDTO item, DevelopmentItemPageQry query) {
        return matchesCommon(item.getProjectId(), item.getNodeId(), item.getOwnerId(), item.getStatus(), query)
                && matchesKeyword(query.getKeyword(), item.getTitle(), item.getProjectName(), item.getProjectCode(), item.getNodeName(), item.getOwnerName());
    }

    private boolean matchesStory(DevelopmentStoryListDTO item, DevelopmentItemPageQry query) {
        return matchesCommon(item.getProjectId(), item.getNodeId(), item.getOwnerId(), item.getStatus(), query)
                && matchesKeyword(query.getKeyword(), item.getTitle(), item.getTopicTitle(), item.getProjectName(), item.getProjectCode(), item.getNodeName(), item.getOwnerName());
    }

    private boolean matchesCommon(Long projectId, Long nodeId, Long ownerId, String status, DevelopmentItemPageQry query) {
        return (query.getProjectId() == null || Objects.equals(query.getProjectId(), projectId))
                && (query.getNodeId() == null || Objects.equals(query.getNodeId(), nodeId))
                && (query.getOwnerId() == null || Objects.equals(query.getOwnerId(), ownerId))
                && (!StringUtils.hasText(query.getStatus()) || query.getStatus().equalsIgnoreCase(status));
    }

    private boolean matchesKeyword(String keyword, String... values) {
        if (!StringUtils.hasText(keyword)) return true;
        String target = keyword.trim().toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(target)) return true;
        }
        return false;
    }

    private Comparator<DevelopmentTopicListDTO> topicComparator(QueryContext context) {
        return Comparator.comparing((DevelopmentTopicListDTO item) -> safe(item.getProjectName()))
                .thenComparingInt(item -> nodeSort(context.nodes().get(item.getNodeId())))
                .thenComparing(item -> safe(item.getTitle()))
                .thenComparing(item -> item.getId() == null ? Long.MAX_VALUE : item.getId());
    }

    private Comparator<DevelopmentStoryListDTO> storyComparator(QueryContext context) {
        return Comparator.comparing((DevelopmentStoryListDTO item) -> safe(item.getProjectName()))
                .thenComparingInt(item -> nodeSort(context.nodes().get(item.getNodeId())))
                .thenComparing(item -> safe(item.getTopicTitle()))
                .thenComparing(item -> safe(item.getTitle()))
                .thenComparing(item -> item.getId() == null ? Long.MAX_VALUE : item.getId());
    }

    private int nodeSort(ProjectNodeDO node) {
        return node == null || node.getSort() == null ? Integer.MAX_VALUE : node.getSort();
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

    private Map<Long, UserDO> userMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();
        List<UserDO> users = safeList(userService.listByIdsIncludingDeleted(new ArrayList<>(ids)));
        return Convertors.userMap(users);
    }

    private String displayName(UserDO user) {
        return Convertors.userDisplayName(user);
    }

    private <T> PageResult<T> page(List<T> items, DevelopmentItemPageQry query) {
        int page = query.getCurrPage();
        int pageSize = query.getPageSize();
        int from = Math.min((page - 1) * pageSize, items.size());
        int to = Math.min(from + pageSize, items.size());
        return PageResult.of(items.size(), page, pageSize, items.subList(from, to));
    }

    private <T> PageResult<T> emptyPage(DevelopmentItemPageQry query) {
        return PageResult.of(0, query.getCurrPage(), query.getPageSize(), List.of());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static List<Long> safeIds(List<Long> values) {
        return safeList(values).stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    private record QueryContext(
            List<ProjectNodeDevelopmentTopicDO> topics,
            List<ProjectNodeDevelopmentStoryDO> stories,
            Map<Long, ProjectDTO> projects,
            Map<Long, ProjectNodeDO> nodes,
            Map<Long, UserDO> users,
            Map<Long, String> topicWorkflowNodeNames,
            Map<Long, String> iterationPlans
    ) {
        private static QueryContext empty() {
            return new QueryContext(List.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    private record FlowSummary(boolean configured, String status, Integer progress) { }
}
