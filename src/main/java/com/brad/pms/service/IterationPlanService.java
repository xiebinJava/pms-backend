package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.common.page.PageResult;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.request.IterationPlanPageQry;
import com.brad.pms.dto.response.IterationPlanDetailDTO;
import com.brad.pms.dto.response.IterationPlanListDTO;
import com.brad.pms.dto.response.IterationPlanStoryDTO;
import com.brad.pms.dto.response.IterationPlanTaskDTO;
import com.brad.pms.dto.response.NodeIterationPlanDTO;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeIterationPlanDO;
import com.brad.pms.entity.ProjectNodePlanBaselineDO;
import com.brad.pms.entity.ProjectNodeSolutionDecisionDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import com.brad.pms.mapper.ProjectNodePlanBaselineMapper;
import com.brad.pms.mapper.ProjectNodeSolutionDecisionMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.workflow.WorkflowComponentKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class IterationPlanService {

    private final ProjectNodeIterationPlanMapper iterationPlanMapper;
    private final ProjectNodePlanBaselineMapper baselineMapper;
    private final ProjectNodeSolutionDecisionMapper decisionMapper;
    private final ProjectPermissionService permissionService;
    private final UserService userService;
    private final ProjectService projectService;
    private final ProjectNodeMapper nodeMapper;
    private final ProjectNodeDevelopmentTopicMapper topicMapper;
    private final ProjectNodeDevelopmentStoryMapper storyMapper;
    private final ProjectTaskMapper taskMapper;

    @Autowired
    public IterationPlanService(
            ProjectNodeIterationPlanMapper iterationPlanMapper,
            ProjectNodePlanBaselineMapper baselineMapper,
            ProjectNodeSolutionDecisionMapper decisionMapper,
            ProjectPermissionService permissionService,
            UserService userService,
            @Lazy ProjectService projectService,
            ProjectNodeMapper nodeMapper,
            ProjectNodeDevelopmentTopicMapper topicMapper,
            ProjectNodeDevelopmentStoryMapper storyMapper,
            ProjectTaskMapper taskMapper) {
        this.iterationPlanMapper = iterationPlanMapper;
        this.baselineMapper = baselineMapper;
        this.decisionMapper = decisionMapper;
        this.permissionService = permissionService;
        this.userService = userService;
        this.projectService = projectService;
        this.nodeMapper = nodeMapper;
        this.topicMapper = topicMapper;
        this.storyMapper = storyMapper;
        this.taskMapper = taskMapper;
    }

    public List<NodeIterationPlanDTO> listByProject(Long projectId) {
        permissionService.requireProjectReadable(projectId);
        return listConfirmedByProject(projectId);
    }

    public Set<Long> confirmedPlanIds(Long projectId) {
        return listConfirmedByProject(projectId).stream()
                .map(NodeIterationPlanDTO::getId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
    }

    /**
     * Returns current plans plus plans already referenced by development stories.
     * A solution-decision revision can make an old plan non-current; keeping the
     * old row readable prevents an unchanged story from looking blank or being
     * rejected merely because the project moved to a newer baseline.
     */
    public List<NodeIterationPlanDTO> listForDevelopment(Long projectId, Set<Long> referencedPlanIds) {
        List<NodeIterationPlanDTO> current = new ArrayList<>(listConfirmedByProject(projectId));
        Set<Long> visibleIds = current.stream()
                .map(NodeIterationPlanDTO::getId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Set<Long> missingIds = referencedPlanIds == null ? Set.of() : referencedPlanIds.stream()
                .filter(id -> id != null && !visibleIds.contains(id))
                .collect(Collectors.toSet());
        if (missingIds.isEmpty()) return current;
        current.addAll(toDTOs(findRowsByIds(projectId, missingIds)));
        return current;
    }

    public List<NodeIterationPlanDTO> listForPlanNode(Long projectId, Long nodeId) {
        List<ProjectNodeIterationPlanDO> rows = findRows(projectId, nodeId);
        return toDTOs(rows);
    }

    /**
     * Iteration plans are a project-planning view, not workflow instances.
     * This aggregate intentionally reads plan, story, and project-task rows
     * without resolving any workflow template.
     */
    public PageResult<IterationPlanListDTO> page(IterationPlanPageQry query) {
        IterationPlanPageQry qry = query == null ? new IterationPlanPageQry() : query;
        List<ProjectNodeIterationPlanDO> plans = findReadablePlans(qry.getProjectId());
        if (plans.isEmpty()) return PageResult.of(0, qry.getCurrPage(), qry.getPageSize(), List.of());
        Map<Long, Aggregate> aggregates = aggregates(plans);
        List<IterationPlanListDTO> items = plans.stream()
                .map(ProjectNodeIterationPlanDO::getId)
                .map(aggregates::get)
                .filter(Objects::nonNull)
                .map(Aggregate::summary)
                .filter(item -> matches(item, qry))
                .sorted(Comparator.comparing((IterationPlanListDTO item) -> safe(item.getProjectName()))
                        .thenComparingInt(item -> item.getSort() == null ? Integer.MAX_VALUE : item.getSort())
                        .thenComparing(item -> safe(item.getName()))
                        .thenComparing(item -> item.getId() == null ? Long.MAX_VALUE : item.getId()))
                .toList();
        int page = qry.getCurrPage();
        int pageSize = qry.getPageSize();
        int from = Math.min((page - 1) * pageSize, items.size());
        int to = Math.min(from + pageSize, items.size());
        return PageResult.of(items.size(), page, pageSize, items.subList(from, to));
    }

    public IterationPlanDetailDTO detail(Long id) {
        ProjectNodeIterationPlanDO plan = iterationPlanMapper.selectById(id);
        if (plan == null) throw BusinessException.notFound("迭代计划不存在");
        permissionService.requireProjectReadable(plan.getProjectId());
        Aggregate aggregate = aggregates(List.of(plan)).get(plan.getId());
        if (aggregate == null) throw BusinessException.notFound("迭代计划不存在");
        IterationPlanDetailDTO result = new IterationPlanDetailDTO();
        result.setPlan(aggregate.summary());
        result.setStories(aggregate.stories());
        result.setTasks(aggregate.tasks());
        return result;
    }

    private List<ProjectNodeIterationPlanDO> findReadablePlans(Long projectId) {
        List<Long> readableProjectIds = projectService.listReadableIds();
        if (projectId != null) {
            if (!readableProjectIds.contains(projectId)) return List.of();
            readableProjectIds = List.of(projectId);
        }
        if (readableProjectIds.isEmpty()) return List.of();
        return iterationPlanMapper.selectList(new LambdaQueryWrapper<ProjectNodeIterationPlanDO>()
                .in(ProjectNodeIterationPlanDO::getProjectId, readableProjectIds)
                .orderByAsc(ProjectNodeIterationPlanDO::getSort)
                .orderByAsc(ProjectNodeIterationPlanDO::getId));
    }

    private Map<Long, Aggregate> aggregates(List<ProjectNodeIterationPlanDO> plans) {
        if (plans == null || plans.isEmpty()) return Map.of();
        Set<Long> projectIds = plans.stream().map(ProjectNodeIterationPlanDO::getProjectId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        Set<Long> planIds = plans.stream().map(ProjectNodeIterationPlanDO::getId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        Map<Long, ProjectDTO> projects = projectService.listReadableByIds(projectIds).stream()
                .collect(Collectors.toMap(ProjectDTO::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
        List<ProjectNodeDO> nodes = projectIds.isEmpty() ? List.of() : nodeMapper.selectList(
                new LambdaQueryWrapper<ProjectNodeDO>().in(ProjectNodeDO::getProjectId, projectIds));
        Map<Long, ProjectNodeDO> nodesById = nodes.stream().filter(item -> item.getId() != null)
                .collect(Collectors.toMap(ProjectNodeDO::getId, item -> item, (left, right) -> left));
        List<ProjectNodeDevelopmentStoryDO> storyRows = storyMapper.selectList(new LambdaQueryWrapper<ProjectNodeDevelopmentStoryDO>()
                .in(ProjectNodeDevelopmentStoryDO::getIterationPlanId, planIds)
                .orderByAsc(ProjectNodeDevelopmentStoryDO::getSort)
                .orderByAsc(ProjectNodeDevelopmentStoryDO::getId));
        Set<Long> topicIds = storyRows.stream().map(ProjectNodeDevelopmentStoryDO::getTopicId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> topicNames = topicIds.isEmpty() ? Map.of() : topicMapper.selectList(
                new LambdaQueryWrapper<ProjectNodeDevelopmentTopicDO>().in(ProjectNodeDevelopmentTopicDO::getId, topicIds))
                .stream().collect(Collectors.toMap(ProjectNodeDevelopmentTopicDO::getId,
                        ProjectNodeDevelopmentTopicDO::getTitle, (left, right) -> left));
        List<ProjectTaskDO> taskRows = taskMapper.selectList(new LambdaQueryWrapper<ProjectTaskDO>()
                .in(ProjectTaskDO::getIterationPlanId, planIds)
                .orderByAsc(ProjectTaskDO::getParentId)
                .orderByAsc(ProjectTaskDO::getSort)
                .orderByAsc(ProjectTaskDO::getId));
        Set<Long> userIds = new HashSet<>();
        plans.stream().map(ProjectNodeIterationPlanDO::getOwnerId).filter(Objects::nonNull).forEach(userIds::add);
        storyRows.stream().map(ProjectNodeDevelopmentStoryDO::getOwnerId).filter(Objects::nonNull).forEach(userIds::add);
        taskRows.stream().map(ProjectTaskDO::getAssigneeId).filter(Objects::nonNull).forEach(userIds::add);
        Map<Long, UserDO> users = userIds.isEmpty() ? Map.of() : Convertors.userMap(
                userService.listByIdsIncludingDeleted(new ArrayList<>(userIds)));
        Map<Long, List<ProjectNodeDevelopmentStoryDO>> storiesByPlan = storyRows.stream()
                .collect(Collectors.groupingBy(ProjectNodeDevelopmentStoryDO::getIterationPlanId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<ProjectTaskDO>> tasksByPlan = taskRows.stream()
                .collect(Collectors.groupingBy(ProjectTaskDO::getIterationPlanId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, Aggregate> result = new LinkedHashMap<>();
        for (ProjectNodeIterationPlanDO plan : plans) {
            List<IterationPlanStoryDTO> stories = storiesByPlan.getOrDefault(plan.getId(), List.of()).stream()
                    .map(story -> toStory(story, topicNames, users)).toList();
            List<IterationPlanTaskDTO> tasks = tasksByPlan.getOrDefault(plan.getId(), List.of()).stream()
                    .map(task -> toTask(task, nodesById, users)).toList();
            result.put(plan.getId(), new Aggregate(toSummary(plan, projects.get(plan.getProjectId()), nodesById.get(plan.getNodeId()), users,
                    stories, tasks), stories, tasks));
        }
        return result;
    }

    private IterationPlanListDTO toSummary(ProjectNodeIterationPlanDO plan, ProjectDTO project, ProjectNodeDO node,
                                           Map<Long, UserDO> users, List<IterationPlanStoryDTO> stories,
                                           List<IterationPlanTaskDTO> tasks) {
        IterationPlanListDTO dto = new IterationPlanListDTO();
        dto.setId(plan.getId());
        dto.setProjectId(plan.getProjectId());
        dto.setProjectCode(project == null ? null : project.getCode());
        dto.setProjectName(project == null ? null : project.getName());
        dto.setNodeId(plan.getNodeId());
        dto.setNodeName(node == null ? null : node.getName());
        dto.setName(plan.getName());
        dto.setOwnerId(plan.getOwnerId());
        dto.setOwnerName(Convertors.userDisplayName(user(users, plan.getOwnerId())));
        dto.setGoal(plan.getGoal());
        dto.setStatus(plan.getStatus());
        dto.setStartDate(plan.getStartDate());
        dto.setDueDate(plan.getDueDate());
        dto.setSort(plan.getSort());
        dto.setStoryCount(stories.size());
        dto.setCompletedStoryCount((int) stories.stream().filter(item -> "DONE".equals(item.getStatus())).count());
        dto.setTaskCount(tasks.size());
        dto.setCompletedTaskCount((int) tasks.stream().filter(item -> Integer.valueOf(2).equals(item.getStatus())).count());
        dto.setProgress(progress(stories, tasks));
        return dto;
    }

    private IterationPlanStoryDTO toStory(ProjectNodeDevelopmentStoryDO story, Map<Long, String> topicNames,
                                          Map<Long, UserDO> users) {
        IterationPlanStoryDTO dto = new IterationPlanStoryDTO();
        dto.setId(story.getId());
        dto.setProjectId(story.getProjectId());
        dto.setNodeId(story.getNodeId());
        dto.setTopicId(story.getTopicId());
        dto.setTopicTitle(story.getTopicId() == null ? null : topicNames.get(story.getTopicId()));
        dto.setTitle(story.getTitle());
        dto.setOwnerId(story.getOwnerId());
        dto.setOwnerName(Convertors.userDisplayName(user(users, story.getOwnerId())));
        dto.setStatus(story.getStatus());
        dto.setProgress(effectiveStoryProgress(story.getStatus(), story.getProgress()));
        dto.setStoryPoints(story.getStoryPoints());
        dto.setStartDate(story.getStartDate());
        dto.setDueDate(story.getDueDate());
        dto.setBlocker(story.getBlocker());
        return dto;
    }

    private IterationPlanTaskDTO toTask(ProjectTaskDO task, Map<Long, ProjectNodeDO> nodes, Map<Long, UserDO> users) {
        IterationPlanTaskDTO dto = new IterationPlanTaskDTO();
        dto.setId(task.getId());
        dto.setVersion(task.getVersion());
        dto.setProjectId(task.getProjectId());
        dto.setNodeId(task.getNodeId());
        ProjectNodeDO node = nodes.get(task.getNodeId());
        dto.setNodeName(node == null ? null : node.getName());
        dto.setParentId(task.getParentId());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setDeliverable(task.getDeliverable());
        dto.setStatus(task.getStatus());
        dto.setPriority(task.getPriority());
        dto.setAssigneeId(task.getAssigneeId());
        dto.setAssigneeName(Convertors.userDisplayName(user(users, task.getAssigneeId())));
        dto.setDueDate(task.getDueDate());
        dto.setSort(task.getSort());
        return dto;
    }

    private boolean matches(IterationPlanListDTO item, IterationPlanPageQry qry) {
        if (qry.getProjectId() != null && !Objects.equals(qry.getProjectId(), item.getProjectId())) return false;
        if (StringUtils.hasText(qry.getStatus()) && !qry.getStatus().equalsIgnoreCase(item.getStatus())) return false;
        if (!StringUtils.hasText(qry.getKeyword())) return true;
        String keyword = qry.getKeyword().trim().toLowerCase();
        return contains(item.getName(), keyword) || contains(item.getProjectName(), keyword)
                || contains(item.getProjectCode(), keyword) || contains(item.getOwnerName(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private int progress(List<IterationPlanStoryDTO> stories, List<IterationPlanTaskDTO> tasks) {
        if (!stories.isEmpty()) return (int) Math.round(stories.stream().mapToInt(IterationPlanStoryDTO::getProgress).average().orElse(0));
        if (tasks.isEmpty()) return 0;
        return (int) Math.round(tasks.stream().filter(item -> Integer.valueOf(2).equals(item.getStatus())).count() * 100.0 / tasks.size());
    }

    private int effectiveStoryProgress(String status, Integer progress) {
        if ("DONE".equals(status)) return 100;
        if ("NOT_STARTED".equals(status)) return 0;
        return progress == null ? 0 : Math.max(0, Math.min(100, progress));
    }

    private UserDO user(Map<Long, UserDO> users, Long id) {
        return id == null ? null : users.get(id);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record Aggregate(IterationPlanListDTO summary, List<IterationPlanStoryDTO> stories,
                             List<IterationPlanTaskDTO> tasks) { }

    private List<NodeIterationPlanDTO> listConfirmedByProject(Long projectId) {
        ProjectNodeDO planNode = permissionService.findNodeWithComponent(projectId, WorkflowComponentKey.PLAN_RESOURCE_RISK);
        if (planNode == null) return List.of();
        ProjectNodePlanBaselineDO baseline = baselineMapper.selectOne(new LambdaQueryWrapper<ProjectNodePlanBaselineDO>()
                .eq(ProjectNodePlanBaselineDO::getProjectId, projectId)
                .eq(ProjectNodePlanBaselineDO::getNodeId, planNode.getId()));
        if (baseline == null || !Integer.valueOf(1).equals(baseline.getStatus()) || !isCurrent(projectId, baseline)) {
            return List.of();
        }
        return toDTOs(findRows(projectId, planNode.getId()));
    }

    private boolean isCurrent(Long projectId, ProjectNodePlanBaselineDO baseline) {
        ProjectNodeDO designNode = permissionService.findNodeWithComponent(projectId, WorkflowComponentKey.SOLUTION_DESIGN);
        if (designNode == null) return false;
        ProjectNodeSolutionDecisionDO decision = decisionMapper.selectOne(new LambdaQueryWrapper<ProjectNodeSolutionDecisionDO>()
                .eq(ProjectNodeSolutionDecisionDO::getProjectId, projectId)
                .eq(ProjectNodeSolutionDecisionDO::getNodeId, designNode.getId()));
        return decision != null && "CONFIRMED".equals(decision.getStatus())
                && java.util.Objects.equals(baseline.getSolutionDecisionVersion(), decision.getVersion());
    }

    private List<ProjectNodeIterationPlanDO> findRows(Long projectId, Long nodeId) {
        return iterationPlanMapper.selectList(new LambdaQueryWrapper<ProjectNodeIterationPlanDO>()
                .eq(ProjectNodeIterationPlanDO::getProjectId, projectId)
                .eq(ProjectNodeIterationPlanDO::getNodeId, nodeId)
                .orderByAsc(ProjectNodeIterationPlanDO::getSort)
                .orderByAsc(ProjectNodeIterationPlanDO::getId));
    }

    private List<ProjectNodeIterationPlanDO> findRowsByIds(Long projectId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return iterationPlanMapper.selectList(new LambdaQueryWrapper<ProjectNodeIterationPlanDO>()
                .eq(ProjectNodeIterationPlanDO::getProjectId, projectId)
                .in(ProjectNodeIterationPlanDO::getId, ids)
                .orderByAsc(ProjectNodeIterationPlanDO::getSort)
                .orderByAsc(ProjectNodeIterationPlanDO::getId));
    }

    private List<NodeIterationPlanDTO> toDTOs(List<ProjectNodeIterationPlanDO> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        Set<Long> ownerIds = rows.stream().map(ProjectNodeIterationPlanDO::getOwnerId)
                .filter(id -> id != null).collect(Collectors.toSet());
        List<UserDO> users = ownerIds.isEmpty() ? List.of() : userService.listByIds(new ArrayList<>(ownerIds));
        Map<Long, UserDO> userMap = Convertors.userMap(users == null ? List.of() : users);
        return rows.stream().map(row -> {
            NodeIterationPlanDTO dto = new NodeIterationPlanDTO();
            dto.setId(row.getId());
            dto.setName(row.getName());
            dto.setOwnerId(row.getOwnerId());
            dto.setOwnerName(Convertors.userDisplayName(userMap.get(row.getOwnerId())));
            dto.setGoal(row.getGoal());
            dto.setStatus(row.getStatus());
            dto.setStartDate(row.getStartDate());
            dto.setDueDate(row.getDueDate());
            dto.setSort(row.getSort());
            return dto;
        }).collect(Collectors.toList());
    }
}
