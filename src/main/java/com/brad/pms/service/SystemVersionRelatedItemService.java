package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.dto.response.SystemVersionRelatedIterationDTO;
import com.brad.pms.dto.response.SystemVersionRelatedItemsDTO;
import com.brad.pms.dto.response.SystemVersionRelatedProjectDTO;
import com.brad.pms.dto.response.SystemVersionRelatedStoryDTO;
import com.brad.pms.dto.response.SystemVersionRelatedTopicDTO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.entity.ProjectNodeIterationPlanDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import com.brad.pms.convertor.Convertors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the version detail tree from existing iteration, project, topic, and
 * story rows. It deliberately creates no relation and does not mutate any row.
 */
@Service
@RequiredArgsConstructor
public class SystemVersionRelatedItemService {

    private final ProjectNodeIterationPlanMapper iterationPlanMapper;
    private final ProjectNodeDevelopmentStoryMapper storyMapper;
    private final ProjectNodeDevelopmentTopicMapper topicMapper;
    private final ProjectService projectService;
    private final UserService userService;

    public SystemVersionRelatedItemsDTO load(Long versionId) {
        SystemVersionRelatedItemsDTO result = new SystemVersionRelatedItemsDTO();
        if (versionId == null) return result;

        List<ProjectNodeIterationPlanDO> plans = safe(iterationPlanMapper.selectList(
                new LambdaQueryWrapper<ProjectNodeIterationPlanDO>()
                        .eq(ProjectNodeIterationPlanDO::getSystemVersionId, versionId)
                        .orderByAsc(ProjectNodeIterationPlanDO::getStartDate)
                        .orderByAsc(ProjectNodeIterationPlanDO::getId)));
        if (plans.isEmpty()) return result;

        Map<Long, ProjectNodeIterationPlanDO> plansById = plans.stream()
                .filter(plan -> plan.getId() != null)
                .collect(Collectors.toMap(ProjectNodeIterationPlanDO::getId, Function.identity(), (left, right) -> left,
                        LinkedHashMap::new));
        if (plansById.isEmpty()) return result;
        Set<Long> planIds = plansById.keySet();
        List<ProjectNodeDevelopmentStoryDO> storyRows = safe(storyMapper.selectList(
                new LambdaQueryWrapper<ProjectNodeDevelopmentStoryDO>()
                        .in(ProjectNodeDevelopmentStoryDO::getIterationPlanId, planIds)
                        .orderByAsc(ProjectNodeDevelopmentStoryDO::getSort)
                        .orderByAsc(ProjectNodeDevelopmentStoryDO::getId)));

        Set<Long> readableProjectIds = new HashSet<>(safe(projectService.listReadableIds()));
        storyRows = storyRows.stream()
                .filter(story -> story.getProjectId() == null || readableProjectIds.contains(story.getProjectId()))
                .toList();

        Set<Long> topicIds = storyRows.stream().map(ProjectNodeDevelopmentStoryDO::getTopicId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ProjectNodeDevelopmentTopicDO> topicsById = topicIds.isEmpty() ? Map.of()
                : safe(topicMapper.selectList(new LambdaQueryWrapper<ProjectNodeDevelopmentTopicDO>()
                                .in(ProjectNodeDevelopmentTopicDO::getId, topicIds)
                                .eq(ProjectNodeDevelopmentTopicDO::getDeleted, false)))
                        .stream().filter(topic -> topic.getId() != null)
                        .filter(topic -> topic.getProjectId() == null || readableProjectIds.contains(topic.getProjectId()))
                        .collect(Collectors.toMap(ProjectNodeDevelopmentTopicDO::getId, Function.identity(), (left, right) -> left));

        Set<Long> projectIds = new HashSet<>();
        plans.stream().map(ProjectNodeIterationPlanDO::getProjectId).filter(Objects::nonNull)
                .filter(readableProjectIds::contains).forEach(projectIds::add);
        storyRows.stream().map(ProjectNodeDevelopmentStoryDO::getProjectId).filter(Objects::nonNull)
                .filter(readableProjectIds::contains).forEach(projectIds::add);
        topicsById.values().stream().map(ProjectNodeDevelopmentTopicDO::getProjectId).filter(Objects::nonNull)
                .filter(readableProjectIds::contains).forEach(projectIds::add);
        Map<Long, ProjectDTO> projectsById = projectIds.isEmpty() ? Map.of()
                : safe(projectService.listReadableByIds(projectIds)).stream()
                        .filter(project -> project.getId() != null)
                        .collect(Collectors.toMap(ProjectDTO::getId, Function.identity(), (left, right) -> left,
                                LinkedHashMap::new));

        Map<Long, UserDO> users = loadUsers(storyRows.stream().map(ProjectNodeDevelopmentStoryDO::getOwnerId)
                .filter(Objects::nonNull).collect(Collectors.toSet()),
                topicsById.values().stream().map(ProjectNodeDevelopmentTopicDO::getOwnerId)
                        .filter(Objects::nonNull).collect(Collectors.toSet()));

        Map<Long, List<ProjectNodeDevelopmentStoryDO>> storiesById = storyRows.stream()
                .filter(story -> story.getId() != null)
                .collect(Collectors.groupingBy(ProjectNodeDevelopmentStoryDO::getId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, SystemVersionRelatedStoryDTO> stories = new LinkedHashMap<>();
        storiesById.forEach((storyId, rows) -> stories.put(storyId,
                toStory(rows, plansById, topicsById, users)));

        Map<Long, SystemVersionRelatedTopicDTO> topicDtos = new LinkedHashMap<>();
        topicsById.values().stream().sorted(topicComparator()).forEach(topic -> {
            SystemVersionRelatedTopicDTO dto = toTopic(topic, users);
            topicDtos.put(topic.getId(), dto);
        });
        stories.values().forEach(story -> {
            if (story.getTopicId() != null && topicDtos.containsKey(story.getTopicId())) {
                topicDtos.get(story.getTopicId()).getStories().add(story);
            }
        });
        topicDtos.values().forEach(this::finishTopic);

        Map<Long, SystemVersionRelatedProjectDTO> projectDtos = new LinkedHashMap<>();
        projectsById.values().stream().sorted(Comparator.comparing(this::projectName)
                .thenComparing(project -> project.getId() == null ? Long.MAX_VALUE : project.getId()))
                .forEach(project -> projectDtos.put(project.getId(), toProject(project)));
        topicDtos.values().stream().forEach(topic -> {
            if (topic.getProjectId() != null && projectDtos.containsKey(topic.getProjectId())) {
                projectDtos.get(topic.getProjectId()).getTopics().add(topic);
            }
        });
        stories.values().stream().filter(story -> story.getTopicId() == null || !topicDtos.containsKey(story.getTopicId()))
                .forEach(story -> {
                    if (story.getProjectId() != null && projectDtos.containsKey(story.getProjectId())) {
                        projectDtos.get(story.getProjectId()).getStories().add(story);
                    }
                });
        result.setProjects(new ArrayList<>(projectDtos.values()));
        result.setUnboundTopics(topicDtos.values().stream().filter(topic -> topic.getProjectId() == null).toList());
        result.setUnboundStories(stories.values().stream()
                .filter(story -> (story.getTopicId() == null || !topicDtos.containsKey(story.getTopicId()))
                        && story.getProjectId() == null).toList());
        return result;
    }

    private SystemVersionRelatedProjectDTO toProject(ProjectDTO project) {
        SystemVersionRelatedProjectDTO dto = new SystemVersionRelatedProjectDTO();
        dto.setId(project.getId());
        dto.setCode(project.getCode());
        dto.setName(project.getName());
        dto.setStatus(project.getStatus());
        dto.setOwnerId(project.getOwnerId());
        dto.setOwnerName(project.getOwnerName());
        dto.setProgress(project.getProgress());
        dto.setStartDate(project.getStartDate());
        dto.setEndDate(project.getEndDate());
        return dto;
    }

    private SystemVersionRelatedTopicDTO toTopic(ProjectNodeDevelopmentTopicDO topic, Map<Long, UserDO> users) {
        SystemVersionRelatedTopicDTO dto = new SystemVersionRelatedTopicDTO();
        dto.setId(topic.getId());
        dto.setTitle(topic.getTitle());
        dto.setProjectId(topic.getProjectId());
        dto.setOwnerId(topic.getOwnerId());
        dto.setOwnerName(Convertors.userDisplayName(topic.getOwnerId() == null ? null : users.get(topic.getOwnerId())));
        return dto;
    }

    private SystemVersionRelatedStoryDTO toStory(List<ProjectNodeDevelopmentStoryDO> rows,
                                                  Map<Long, ProjectNodeIterationPlanDO> plans,
                                                  Map<Long, ProjectNodeDevelopmentTopicDO> topics,
                                                  Map<Long, UserDO> users) {
        ProjectNodeDevelopmentStoryDO source = rows.get(0);
        SystemVersionRelatedStoryDTO dto = new SystemVersionRelatedStoryDTO();
        dto.setId(source.getId());
        dto.setTitle(source.getTitle());
        dto.setProjectId(source.getProjectId());
        dto.setTopicId(source.getTopicId());
        dto.setStatus(source.getStatus());
        dto.setOwnerId(source.getOwnerId());
        dto.setOwnerName(Convertors.userDisplayName(source.getOwnerId() == null ? null : users.get(source.getOwnerId())));
        dto.setProgress(effectiveProgress(source.getStatus(), source.getProgress()));
        dto.setStartDate(source.getStartDate());
        dto.setDueDate(source.getDueDate());
        dto.setSourceIterations(rows.stream().map(row -> plans.get(row.getIterationPlanId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(ProjectNodeIterationPlanDO::getId, Function.identity(), (left, right) -> left,
                        LinkedHashMap::new)).values().stream().map(this::toIteration)
                .sorted(Comparator.comparing(SystemVersionRelatedIterationDTO::getStartDate,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(item -> item.getId() == null ? Long.MAX_VALUE : item.getId()))
                .toList());
        if (dto.getProjectId() == null && source.getTopicId() != null) {
            ProjectNodeDevelopmentTopicDO topic = topics.get(source.getTopicId());
            if (topic != null) dto.setProjectId(topic.getProjectId());
        }
        return dto;
    }

    private SystemVersionRelatedIterationDTO toIteration(ProjectNodeIterationPlanDO plan) {
        SystemVersionRelatedIterationDTO dto = new SystemVersionRelatedIterationDTO();
        dto.setId(plan.getId());
        dto.setName(plan.getName());
        dto.setStatus(plan.getStatus());
        dto.setStartDate(plan.getStartDate());
        dto.setDueDate(plan.getDueDate());
        return dto;
    }

    private void finishTopic(SystemVersionRelatedTopicDTO topic) {
        List<SystemVersionRelatedStoryDTO> stories = topic.getStories();
        topic.setStoryCount(stories.size());
        topic.setProgress(stories.isEmpty() ? 0 : (int) Math.round(stories.stream()
                .mapToInt(item -> item.getProgress() == null ? 0 : item.getProgress()).average().orElse(0)));
        topic.setStatus(derivedStatus(stories.stream().map(SystemVersionRelatedStoryDTO::getStatus).toList()));
        stories.sort(Comparator.comparing((SystemVersionRelatedStoryDTO item) -> safe(item.getTitle()))
                .thenComparing(item -> item.getId() == null ? Long.MAX_VALUE : item.getId()));
    }

    private int effectiveProgress(String status, Integer progress) {
        if ("DONE".equals(status)) return 100;
        if ("NOT_STARTED".equals(status)) return 0;
        return progress == null ? 0 : Math.max(0, Math.min(100, progress));
    }

    private String derivedStatus(List<String> statuses) {
        if (statuses.isEmpty()) return "NOT_STARTED";
        if (statuses.stream().allMatch("DONE"::equals)) return "DONE";
        if (statuses.stream().anyMatch(status -> !"NOT_STARTED".equals(status))) return "IN_PROGRESS";
        return "NOT_STARTED";
    }

    private Map<Long, UserDO> loadUsers(Collection<Long>... groups) {
        Set<Long> ids = new HashSet<>();
        for (Collection<Long> group : groups) if (group != null) ids.addAll(group);
        if (ids.isEmpty()) return Map.of();
        List<UserDO> users = userService.listByIdsIncludingDeleted(ids);
        return users == null ? Map.of() : users.stream().filter(Objects::nonNull).filter(user -> user.getId() != null)
                .collect(Collectors.toMap(UserDO::getId, Function.identity(), (left, right) -> left));
    }

    private Comparator<ProjectNodeDevelopmentTopicDO> topicComparator() {
        return Comparator.comparing((ProjectNodeDevelopmentTopicDO topic) -> safe(topic.getTitle()))
                .thenComparing(topic -> topic.getId() == null ? Long.MAX_VALUE : topic.getId());
    }

    private String projectName(ProjectDTO project) {
        return safe(project.getName());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
