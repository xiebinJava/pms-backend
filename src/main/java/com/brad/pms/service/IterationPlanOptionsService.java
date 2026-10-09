package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.response.NodeIterationPlanDTO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.SystemDO;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.SystemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class IterationPlanOptionsService {
    private final IterationPlanService iterations;
    private final RequirementSystemReferenceService systems;
    private final ProjectPermissionService permissions;
    private final ProjectNodeDevelopmentStoryMapper stories;
    private final SystemMapper systemMapper;

    public SystemContext projectSystem(Long projectId) {
        permissions.requireProjectReadable(projectId);
        Long systemId = systems.resolveForProject(projectId);
        SystemDO system = systemId == null ? null : systemMapper.selectById(systemId);
        return new SystemContext(systemId, system == null ? null : system.getName());
    }

    public List<NodeIterationPlanDTO> forProject(Long projectId, Set<Long> referencedIds) {
        permissions.requireProjectReadable(projectId);
        Long systemId = systems.resolveForProject(projectId);
        List<NodeIterationPlanDTO> result = new ArrayList<>(iterations.listForDevelopment(projectId, referencedIds));
        result.addAll(iterations.listIndependentForSystem(systemId));
        return result.stream().filter(plan -> systemId == null || plan.getSystemId() == null
                || Objects.equals(plan.getSystemId(), systemId)).distinct().toList();
    }

    public List<NodeIterationPlanDTO> independentForProject(Long projectId) {
        permissions.requireProjectReadable(projectId);
        return iterations.listIndependentForSystem(systems.resolveForProject(projectId));
    }

    public List<NodeIterationPlanDTO> forStory(Long storyId) {
        ProjectNodeDevelopmentStoryDO story = stories.selectById(storyId);
        if (story == null) throw BusinessException.notFound("故事不存在");
        if (story.getProjectId() != null) permissions.requireProjectReadable(story.getProjectId());
        Long systemId = systems.resolveForStory(story);
        List<NodeIterationPlanDTO> result = new ArrayList<>();
        if (story.getProjectId() != null) result.addAll(iterations.listByProject(story.getProjectId()));
        result.addAll(iterations.listIndependentForSystem(systemId));
        return result.stream().filter(plan -> systemId == null || plan.getSystemId() == null
                || Objects.equals(plan.getSystemId(), systemId)).distinct().toList();
    }

    public record SystemContext(Long systemId, String systemName) { }
}
