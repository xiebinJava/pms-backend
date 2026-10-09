package com.brad.pms.service;

import com.brad.pms.common.enums.RequirementExecutionTargetType;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.entity.RequirementDO;
import com.brad.pms.entity.SystemDO;
import com.brad.pms.entity.ProjectNodeIterationPlanDO;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.entity.ProjectTaskDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.mapper.RequirementMapper;
import com.brad.pms.mapper.SystemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Resolves the single system shared by a project's requirement execution chain. */
@Service
@RequiredArgsConstructor
public class RequirementSystemReferenceService {

    private final RequirementMapper requirementMapper;
    private final ProjectNodeDevelopmentTopicMapper topicMapper;
    private final ProjectNodeDevelopmentStoryMapper storyMapper;
    private final SystemMapper systemMapper;
    private final ProjectNodeIterationPlanMapper iterationPlanMapper;
    private final ProjectTaskMapper taskMapper;

    @Transactional(readOnly = true)
    public Long resolveForProject(Long projectId) {
        if (projectId == null) throw BusinessException.error("项目不能为空");
        Long systemId = uniqueSystem(requirementsForProject(projectId, null));
        return systemId;
    }

    @Transactional(readOnly = true)
    public Long resolveForTopic(Long topicId) {
        ProjectNodeDevelopmentTopicDO topic = topic(topicId);
        if (topic == null || Boolean.TRUE.equals(topic.getDeleted())) throw BusinessException.notFound("专题不存在");
        return topic.getProjectId() == null ? uniqueSystem(requirementsForTopic(topicId))
                : resolveForProject(topic.getProjectId());
    }

    @Transactional(readOnly = true)
    public Long resolveForStory(Long storyId) {
        ProjectNodeDevelopmentStoryDO story = story(storyId);
        if (story == null) throw BusinessException.notFound("故事不存在");
        return resolveForStory(story);
    }

    /** Also accepts a prospective story scope, before it has been persisted. */
    public Long resolveForStory(ProjectNodeDevelopmentStoryDO story) {
        return uniqueSystem(requirementsForStory(story, null));
    }

    private List<RequirementDO> requirementsForStory(ProjectNodeDevelopmentStoryDO story, Long excludedId) {
        List<RequirementDO> requirements = new ArrayList<>();
        if (story.getId() != null) requirements.addAll(requirements(RequirementExecutionTargetType.STORY, List.of(story.getId())));
        if (story.getProjectId() != null) requirements.addAll(requirementsForProject(story.getProjectId(), excludedId));
        else if (story.getTopicId() != null) requirements.addAll(requirementsForTopic(story.getTopicId()));
        return requirements.stream().filter(item -> excludedId == null || !Objects.equals(item.getId(), excludedId)).toList();
    }

    private Long uniqueSystem(List<RequirementDO> requirements) {
        Set<Long> systemIds = requirements.stream()
                .map(RequirementDO::getSystemId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (systemIds.size() > 1) {
            throw BusinessException.conflict("关联需求包含多个不同系统，请先统一需求流程中的系统");
        }
        Long systemId = systemIds.stream().findFirst().orElse(null);
        return systemId;
    }

    @Transactional
    public void validateRequirementSystem(RequirementDO requirement) {
        if (requirement == null || requirement.getSystemId() == null) return;
        requireActiveSystem(requirement.getSystemId());
        Long projectId = projectIdOf(requirement);
        List<RequirementDO> related;
        List<ProjectNodeDevelopmentStoryDO> stories;
        if (projectId != null) {
            related = requirementsForProject(projectId, requirement.getId());
            stories = projectStories(projectId);
        } else if (requirement.getExecutionTargetType() == RequirementExecutionTargetType.TOPIC) {
            related = requirementsForTopic(requirement.getExecutionTargetId());
            stories = topicStories(requirement.getExecutionTargetId());
        } else if (requirement.getExecutionTargetType() == RequirementExecutionTargetType.STORY) {
            ProjectNodeDevelopmentStoryDO story = story(requirement.getExecutionTargetId());
            related = story != null && story.getTopicId() != null ? requirementsForTopic(story.getTopicId())
                    : requirements(RequirementExecutionTargetType.STORY, List.of(requirement.getExecutionTargetId()));
            stories = story == null ? List.of() : story.getTopicId() == null ? List.of(story)
                    : topicStories(story.getTopicId());
        } else return;
        boolean conflict = related.stream().filter(item -> !Objects.equals(item.getId(), requirement.getId()))
                .map(RequirementDO::getSystemId)
                .filter(Objects::nonNull)
                .anyMatch(systemId -> !Objects.equals(systemId, requirement.getSystemId()));
        if (conflict) throw BusinessException.conflict("同一执行链路下的需求必须使用同一个系统");
        validateIterations(requirement.getSystemId(), projectId, stories, requirement.getId());
    }

    /** A source moved into a scope changes inference for every item already in that scope. */
    @Transactional
    public void validateDestinationSystem(Long projectId, Long topicId, Long systemId) {
        if (systemId == null) return;
        validateIterations(systemId, projectId, projectId != null ? projectStories(projectId)
                : topicId != null ? topicStories(topicId) : List.of(), null);
    }

    private void validateIterations(Long systemId, Long projectId, List<ProjectNodeDevelopmentStoryDO> stories, Long excludedId) {
        Set<Long> planIds = stories.stream().map(ProjectNodeDevelopmentStoryDO::getIterationPlanId)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        if (projectId != null) {
            safe(taskMapper.selectList(new LambdaQueryWrapper<ProjectTaskDO>()
                    .eq(ProjectTaskDO::getProjectId, projectId)
                    .isNotNull(ProjectTaskDO::getIterationPlanId).last(lockSuffix()))).stream()
                    .map(ProjectTaskDO::getIterationPlanId).filter(Objects::nonNull).forEach(planIds::add);
        }
        if (projectId == null && planIds.isEmpty()) return;
        LambdaQueryWrapper<ProjectNodeIterationPlanDO> query = new LambdaQueryWrapper<>();
        query.and(scope -> {
            if (projectId != null) scope.eq(ProjectNodeIterationPlanDO::getProjectId, projectId);
            if (!planIds.isEmpty()) {
                if (projectId != null) scope.or();
                scope.in(ProjectNodeIterationPlanDO::getId, planIds);
            }
        });
        query.orderByAsc(ProjectNodeIterationPlanDO::getId).last(lockSuffix());
        for (ProjectNodeIterationPlanDO plan : safe(iterationPlanMapper.selectList(query))) {
            if (plan.getSystemId() != null && !Objects.equals(plan.getSystemId(), systemId)) {
                throw BusinessException.conflict("需求系统与已关联迭代的系统不一致，请先调整迭代关联");
            }
            if (plan.getSystemId() == null) {
                validatePlanSources(plan.getId(), systemId, excludedId);
                if (iterationPlanMapper.fillSystem(plan.getId(), systemId) != 1) {
                    throw BusinessException.conflict("迭代系统已被其他人修改，请刷新后重试");
                }
            }
        }
    }

    public void validatePlanSources(Long planId, Long systemId, Long excludedRequirementId) {
        for (ProjectNodeDevelopmentStoryDO linked : safe(storyMapper.selectList(
                new LambdaQueryWrapper<ProjectNodeDevelopmentStoryDO>()
                        .eq(ProjectNodeDevelopmentStoryDO::getIterationPlanId, planId).orderByAsc(ProjectNodeDevelopmentStoryDO::getId).last(lockSuffix())))) {
            Long source = uniqueSystem(requirementsForStory(linked, excludedRequirementId));
            if (source != null && !Objects.equals(source, systemId)) {
                throw BusinessException.conflict("迭代中已有故事的所属系统不一致，请先调整关联");
            }
        }
        for (ProjectTaskDO task : safe(taskMapper.selectList(new LambdaQueryWrapper<ProjectTaskDO>()
                .eq(ProjectTaskDO::getIterationPlanId, planId).orderByAsc(ProjectTaskDO::getId).last(lockSuffix())))) {
            Long source = task.getProjectId() == null ? null : uniqueSystem(requirementsForProject(task.getProjectId(), excludedRequirementId));
            if (source != null && !Objects.equals(source, systemId)) {
                throw BusinessException.conflict("迭代中已有任务的所属系统不一致，请先调整关联");
            }
        }
    }

    public void requireActiveSystem(Long systemId) {
        if (systemId == null) throw BusinessException.error("系统不能为空");
        SystemDO system = systemMapper.selectById(systemId);
        if (system == null || !"ACTIVE".equalsIgnoreCase(system.getStatus())) {
            throw BusinessException.error("系统不存在或已停用");
        }
    }

    private List<RequirementDO> requirementsForProject(Long projectId, Long excludedRequirementId) {
        List<Long> topicIds = safe(writes() ? topicMapper.selectByProjectIdForUpdate(projectId)
                : topicMapper.selectActiveByProjectId(projectId)).stream()
                .map(ProjectNodeDevelopmentTopicDO::getId).filter(Objects::nonNull).toList();
        List<Long> storyIds = projectStories(projectId).stream()
                .map(ProjectNodeDevelopmentStoryDO::getId).filter(Objects::nonNull).toList();
        List<RequirementDO> result = new ArrayList<>();
        result.addAll(requirements(RequirementExecutionTargetType.PROJECT, List.of(projectId)));
        if (!topicIds.isEmpty()) result.addAll(requirements(RequirementExecutionTargetType.TOPIC, topicIds));
        if (!storyIds.isEmpty()) result.addAll(requirements(RequirementExecutionTargetType.STORY, storyIds));
        return result.stream().filter(item -> excludedRequirementId == null
                || !Objects.equals(item.getId(), excludedRequirementId)).toList();
    }

    private List<RequirementDO> requirementsForTopic(Long topicId) {
        if (topicId == null) return List.of();
        List<RequirementDO> result = new ArrayList<>(requirements(RequirementExecutionTargetType.TOPIC, List.of(topicId)));
        List<Long> ids = topicStories(topicId).stream()
                .map(ProjectNodeDevelopmentStoryDO::getId).filter(Objects::nonNull).toList();
        if (!ids.isEmpty()) result.addAll(requirements(RequirementExecutionTargetType.STORY, ids));
        return result;
    }

    private Long projectIdOf(RequirementDO requirement) {
        if (requirement.getExecutionTargetType() == null || requirement.getExecutionTargetId() == null) return null;
        return switch (requirement.getExecutionTargetType()) {
            case PROJECT -> requirement.getExecutionTargetId();
            case TOPIC -> {
                ProjectNodeDevelopmentTopicDO topic = topic(requirement.getExecutionTargetId());
                yield topic == null ? null : topic.getProjectId();
            }
            case STORY -> {
                ProjectNodeDevelopmentStoryDO story = story(requirement.getExecutionTargetId());
                yield story == null ? null : story.getProjectId();
            }
        };
    }

    // Mutation inference uses current locking reads, never a stale MVCC snapshot.
    // Associations and source edits protect the same story/requirement/iteration rows.
    private boolean writes() { return TransactionSynchronizationManager.isActualTransactionActive()
            && !TransactionSynchronizationManager.isCurrentTransactionReadOnly(); }
    private String lockSuffix() { return writes() ? "FOR UPDATE" : ""; }
    private ProjectNodeDevelopmentStoryDO story(Long id) { return writes() ? storyMapper.selectByIdForUpdate(id) : storyMapper.selectById(id); }
    private ProjectNodeDevelopmentTopicDO topic(Long id) { return writes() ? topicMapper.selectByIdForUpdate(id) : topicMapper.selectById(id); }
    private List<ProjectNodeDevelopmentStoryDO> projectStories(Long id) { return safe(writes() ? storyMapper.selectByProjectIdForUpdate(id) : storyMapper.selectActiveByProjectId(id)); }
    private List<ProjectNodeDevelopmentStoryDO> topicStories(Long id) { return safe(writes() ? storyMapper.selectByTopicIdForUpdate(id) : storyMapper.selectByTopicId(id)); }
    private List<RequirementDO> requirements(RequirementExecutionTargetType type, List<Long> ids) {
        return safe(writes() ? requirementMapper.selectByExecutionTargetsForUpdate(type, ids) : requirementMapper.selectByExecutionTargets(type, ids));
    }

    private <T> List<T> safe(List<T> items) { return items == null ? List.of() : items; }
}
