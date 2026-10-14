package com.brad.pms.ai.connector.context;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.entity.ProjectNodeIterationPlanDO;
import com.brad.pms.entity.RequirementDO;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import com.brad.pms.mapper.RequirementMapper;
import com.brad.pms.security.AuthorizationService;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

/**
 * Module READ authority for the four AI context resource types and their structured
 * references. The internal seed path (no HTTP identity) can never double as an
 * externally visible bypass: no authentication means no authorization.
 */
@Component
@RequiredArgsConstructor
public class AiContextReadPolicy {

    private static final Set<String> RESOURCE_TYPES = Set.of("project", "requirement", "topic", "story");
    private static final Set<String> REFERENCE_TYPES =
            Set.of("project", "requirement", "topic", "story", "iteration_plan");

    private final ProjectMapper projectMapper;
    private final RequirementMapper requirementMapper;
    private final ProjectNodeDevelopmentTopicMapper topicMapper;
    private final ProjectNodeDevelopmentStoryMapper storyMapper;
    private final ProjectNodeIterationPlanMapper iterationPlanMapper;
    private final AuthorizationService authorizationService;
    private final com.brad.pms.service.ProjectPermissionService projectPermissionService;

    public void requireRead(String type) {
        String normalized = normalizedResourceType(type);
        if (!RESOURCE_TYPES.contains(normalized)) {
            throw BusinessException.error("不支持的流程上下文资源类型: " + normalized);
        }
        if (!hasModuleRead(normalized)) {
            throw BusinessException.forbidden("无权读取该类型对象");
        }
    }

    public boolean canReadReference(String referenceType, Long referenceId) {
        if (referenceType == null || referenceId == null) return false;
        String normalized = normalizedResourceType(referenceType);
        if (!REFERENCE_TYPES.contains(normalized)) return false;
        if (!hasModuleRead(normalized)) return false;
        // Only definitive 403/404 facts may omit a reference; DB failures propagate untouched.
        return switch (normalized) {
            case "project" -> readableProject(projectMapper.selectIncludingDeleted(referenceId));
            case "requirement" -> readableRequirement(requirementMapper.selectById(referenceId));
            case "topic" -> readableTopic(topicMapper.selectById(referenceId));
            case "story" -> readableStory(storyMapper.selectById(referenceId));
            case "iteration_plan" -> readablePlan(iterationPlanMapper.selectById(referenceId));
            default -> false;
        };
    }

    private boolean hasModuleRead(String normalized) {
        if (UserContext.get() == null) return false;
        String required = "requirement".equals(normalized)
                ? PermissionCode.REQUIREMENT_READ : PermissionCode.PROJECT_READ;
        return authorizationService.has(required);
    }

    private boolean readableProject(ProjectDO project) {
        return project != null && !Boolean.TRUE.equals(project.getDeleted())
                && projectPermissionService.canReadProject(project);
    }

    private boolean readableRequirement(RequirementDO requirement) {
        return requirement != null && !Boolean.TRUE.equals(requirement.getDeleted());
    }

    private boolean readableTopic(ProjectNodeDevelopmentTopicDO topic) {
        if (topic == null || Boolean.TRUE.equals(topic.getDeleted())) return false;
        return readableProject(projectMapper.selectById(topic.getProjectId()));
    }

    private boolean readableStory(ProjectNodeDevelopmentStoryDO story) {
        if (story == null) return false;
        return readableProject(projectMapper.selectById(story.getProjectId()));
    }

    private boolean readablePlan(ProjectNodeIterationPlanDO plan) {
        if (plan == null) return false;
        return readableProject(projectMapper.selectById(plan.getProjectId()));
    }

    private String normalizedResourceType(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
    }
}
