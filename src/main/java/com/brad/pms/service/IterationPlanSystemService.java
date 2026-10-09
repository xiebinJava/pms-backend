package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodeIterationPlanCmd;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeIterationPlanDO;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** One binding policy for standalone creation, project workbenches and story/task links. */
@Service
@RequiredArgsConstructor
public class IterationPlanSystemService {
    private final RequirementSystemReferenceService systems;
    private final SystemVersionReferenceService versions;
    private final ProjectNodeIterationPlanMapper plans;

    public Long resolveForSave(ProjectNodeIterationPlanDO plan, NodeIterationPlanCmd cmd, boolean required) {
        Long inherited = plan.getProjectId() == null ? null : systems.resolveForProject(plan.getProjectId());
        Long requested = cmd.isSystemIdSpecified() ? cmd.getSystemId() : plan.getSystemId();
        if (inherited != null && requested != null && !Objects.equals(inherited, requested)) {
            throw BusinessException.conflict("迭代系统与关联需求的所属系统不一致");
        }
        Long result = inherited == null ? requested : inherited;
        if (result == null && (required || cmd.isSystemIdSpecified() && plan.getSystemId() != null)) {
            throw BusinessException.error("请选择所属系统，已设置的迭代系统不能清空");
        }
        if (result != null && !Objects.equals(result, plan.getSystemId())) systems.requireActiveSystem(result);
        if (plan.getId() != null && !Objects.equals(result, plan.getSystemId())) {
            systems.validatePlanSources(plan.getId(), result, null);
        }
        return result;
    }

    /** Caller holds the story and iteration rows; filling and association commit in the same transaction. */
    @Transactional
    public void bindStory(ProjectNodeIterationPlanDO plan, ProjectNodeDevelopmentStoryDO story) {
        requireProjectScope(plan, story.getProjectId());
        bindSystem(plan, systems.resolveForStory(story));
    }

    @Transactional
    public void bindStory(Long planId, ProjectNodeDevelopmentStoryDO story) {
        ProjectNodeIterationPlanDO plan = plans.selectByIdForUpdate(planId);
        if (plan == null) throw BusinessException.notFound("迭代计划不存在");
        bindStory(plan, story);
    }

    @Transactional
    public void bindProject(ProjectNodeIterationPlanDO plan, Long projectId) {
        requireProjectScope(plan, projectId);
        bindSystem(plan, projectId == null ? null : systems.resolveForProject(projectId));
    }

    /** Checks a story's prospective scope even when it currently has no iteration. */
    @Transactional
    public void validateStoryScope(ProjectNodeDevelopmentStoryDO story) {
        Long source = systems.resolveForStory(story);
        systems.validateDestinationSystem(story.getProjectId(), story.getTopicId(), source);
        if (story.getIterationPlanId() != null) {
            ProjectNodeIterationPlanDO plan = plans.selectByIdForUpdate(story.getIterationPlanId());
            if (plan == null) throw BusinessException.notFound("迭代计划不存在");
            requireProjectScope(plan, story.getProjectId());
            bindSystem(plan, source);
        }
    }

    private void bindSystem(ProjectNodeIterationPlanDO plan, Long source) {
        requireSameSystem(plan.getSystemId(), source);
        Long systemId = plan.getSystemId() == null ? source : plan.getSystemId();
        versions.validateSystem(plan.getSystemVersionId(), systemId);
        if (plan.getSystemId() == null && source != null) {
            systems.requireActiveSystem(source);
            systems.validatePlanSources(plan.getId(), source, null);
            if (plans.fillSystem(plan.getId(), source) != 1) {
                throw BusinessException.conflict("迭代系统已被其他人修改，请刷新后重试");
            }
            plan.setSystemId(source);
        }
    }

    private void requireProjectScope(ProjectNodeIterationPlanDO plan, Long projectId) {
        if (plan.getProjectId() != null && !Objects.equals(plan.getProjectId(), projectId)) {
            throw BusinessException.error("故事和迭代计划必须属于同一个项目，任务关联同样受项目范围限制");
        }
    }

    private void requireSameSystem(Long iterationSystem, Long sourceSystem) {
        if (iterationSystem != null && sourceSystem != null && !Objects.equals(iterationSystem, sourceSystem)) {
            throw BusinessException.conflict("迭代所属系统（" + iterationSystem + "）与关联事项所属系统（"
                    + sourceSystem + "）不一致，请选择同系统的迭代");
        }
    }

}
