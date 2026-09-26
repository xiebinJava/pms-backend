package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.common.enums.ProjectStatus;
import com.brad.pms.common.enums.RequirementExecutionTargetType;
import com.brad.pms.dto.response.RequirementExecutionTargetDTO;
import com.brad.pms.dto.response.SourceRequirementSummaryDTO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.entity.RequirementDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.mapper.RequirementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-only direct-source lookup. It never walks a requirement's descendants. */
@Service
@RequiredArgsConstructor
public class RequirementExecutionTargetReadService {
    private final RequirementMapper requirementMapper;
    private final UserService userService;
    private final ProjectMapper projectMapper;
    private final ProjectPermissionService projectPermissionService;
    private final ProjectNodeDevelopmentTopicMapper topicMapper;
    private final ProjectNodeDevelopmentStoryMapper storyMapper;

    /**
     * Reads the current target without applying mutation validation. A target
     * may become unavailable after it was linked; keeping that row visible is
     * important for history and for an explicit unlink/rebind flow.
     */
    public RequirementExecutionTargetDTO findCurrentTarget(RequirementDO requirement) {
        if (requirement == null || requirement.getExecutionTargetType() == null
                || requirement.getExecutionTargetId() == null) return null;
        return switch (requirement.getExecutionTargetType()) {
            case PROJECT -> projectTarget(requirement.getExecutionTargetId());
            case TOPIC -> topicTarget(requirement.getExecutionTargetId());
            case STORY -> storyTarget(requirement.getExecutionTargetId());
        };
    }

    public SourceRequirementSummaryDTO findDirectSourceForTarget(
            RequirementExecutionTargetType targetType, Long targetId) {
        if (targetType == null || targetId == null) return null;
        RequirementDO requirement = requirementMapper.selectByExecutionTarget(targetType, targetId);
        if (requirement == null || Boolean.TRUE.equals(requirement.getDeleted())) return null;
        List<UserDO> owners = requirement.getOwnerId() == null ? List.of()
                : userService.listByIdsIncludingDeleted(List.of(requirement.getOwnerId()));
        UserDO owner = owners == null ? null : owners.stream().findFirst().orElse(null);
        return toSummary(requirement, owner);
    }

    public List<SourceRequirementSummaryDTO> findDirectSourcesForTarget(
            RequirementExecutionTargetType targetType, Long targetId) {
        if (targetType == null || targetId == null) return List.of();
        return findDirectSourcesForTargets(targetType, List.of(targetId))
                .getOrDefault(targetId, List.of());
    }

    public Map<Long, List<SourceRequirementSummaryDTO>> findDirectSourcesForTargets(
            RequirementExecutionTargetType targetType, Collection<Long> targetIds) {
        if (targetType == null || targetIds == null || targetIds.isEmpty()) return Map.of();
        List<Long> ids = targetIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        List<RequirementDO> requirements = requirementMapper.selectByExecutionTargets(targetType, ids);
        if (requirements == null || requirements.isEmpty()) return Map.of();
        List<Long> ownerIds = requirements.stream().map(RequirementDO::getOwnerId)
                .filter(Objects::nonNull).distinct().toList();
        List<UserDO> loadedOwners = ownerIds.isEmpty() ? List.of()
                : userService.listByIdsIncludingDeleted(ownerIds);
        Map<Long, UserDO> owners = loadedOwners == null ? Map.of() : loadedOwners.stream()
                .collect(Collectors.toMap(UserDO::getId, Function.identity(), (left, right) -> left));
        return requirements.stream()
                .filter(requirement -> !Boolean.TRUE.equals(requirement.getDeleted()))
                .filter(requirement -> requirement.getExecutionTargetId() != null)
                .collect(Collectors.groupingBy(RequirementDO::getExecutionTargetId,
                        LinkedHashMap::new,
                        Collectors.mapping(requirement -> toSummary(requirement,
                                owners.get(requirement.getOwnerId())), Collectors.toList())));
    }

    private SourceRequirementSummaryDTO toSummary(RequirementDO requirement, UserDO owner) {
        SourceRequirementSummaryDTO dto = new SourceRequirementSummaryDTO();
        dto.setId(requirement.getId());
        dto.setTitle(requirement.getTitle());
        dto.setStatus(requirement.getStatus());
        dto.setOwnerId(requirement.getOwnerId());
        dto.setOwnerName(owner == null ? null : com.brad.pms.convertor.Convertors.userDisplayName(owner));
        dto.setTargetType(requirement.getExecutionTargetType());
        dto.setTargetId(requirement.getExecutionTargetId());
        return dto;
    }

    private RequirementExecutionTargetDTO projectTarget(Long targetId) {
        ProjectDO project = projectMapper.selectIncludingDeleted(targetId);
        RequirementExecutionTargetDTO dto = target(RequirementExecutionTargetType.PROJECT, targetId, "project");
        if (project == null) {
            dto.setStatus("UNAVAILABLE");
            return dto;
        }
        if (!projectPermissionService.canReadProject(project)) return unavailable(dto);
        dto.setTitle(project.getName());
        dto.setCode(project.getCode());
        dto.setStatus(projectStatus(project));
        dto.setOwnerId(project.getOwnerId());
        dto.setOwnerName(displayName(project.getOwnerId()));
        dto.setProgress(project.getProgress());
        return dto;
    }

    private RequirementExecutionTargetDTO topicTarget(Long targetId) {
        ProjectNodeDevelopmentTopicDO topic = topicMapper.selectById(targetId);
        RequirementExecutionTargetDTO dto = target(RequirementExecutionTargetType.TOPIC, targetId, "topic");
        if (topic == null) {
            dto.setStatus("UNAVAILABLE");
            return dto;
        }
        if (topic.getProjectId() != null) {
            ProjectDO project = projectMapper.selectIncludingDeleted(topic.getProjectId());
            if (!projectPermissionService.canReadProject(project)) return unavailable(dto);
        }
        dto.setTitle(topic.getTitle());
        dto.setStatus(Boolean.TRUE.equals(topic.getDeleted()) ? "DELETED" : "ACTIVE");
        dto.setOwnerId(topic.getOwnerId());
        dto.setOwnerName(displayName(topic.getOwnerId()));
        return dto;
    }

    private RequirementExecutionTargetDTO storyTarget(Long targetId) {
        ProjectNodeDevelopmentStoryDO story = storyMapper.selectById(targetId);
        RequirementExecutionTargetDTO dto = target(RequirementExecutionTargetType.STORY, targetId, "story");
        if (story == null) {
            dto.setStatus("UNAVAILABLE");
            return dto;
        }
        if (story.getProjectId() != null) {
            ProjectDO project = projectMapper.selectIncludingDeleted(story.getProjectId());
            if (!projectPermissionService.canReadProject(project)) return unavailable(dto);
        }
        dto.setTitle(story.getTitle());
        dto.setStatus(story.getStatus());
        dto.setOwnerId(story.getOwnerId());
        dto.setOwnerName(displayName(story.getOwnerId()));
        dto.setProgress(story.getProgress());
        return dto;
    }

    private RequirementExecutionTargetDTO target(RequirementExecutionTargetType type, Long targetId,
                                                  String navigationType) {
        RequirementExecutionTargetDTO dto = new RequirementExecutionTargetDTO();
        dto.setTargetType(type);
        dto.setTargetId(targetId);
        dto.setNavigationType(navigationType);
        dto.setNavigationId(targetId);
        return dto;
    }

    private RequirementExecutionTargetDTO unavailable(RequirementExecutionTargetDTO dto) {
        dto.setStatus("UNAVAILABLE");
        dto.setTitle(null);
        dto.setCode(null);
        dto.setOwnerId(null);
        dto.setOwnerName(null);
        dto.setProgress(null);
        dto.setNavigationId(null);
        return dto;
    }

    private String projectStatus(ProjectDO project) {
        if (Boolean.TRUE.equals(project.getDeleted())) return "DELETED";
        return switch (ProjectStatus.normalize(project.getStatus())) {
            case 1 -> "ACTIVE";
            case 2 -> "COMPLETED";
            case 3 -> "TERMINATED";
            default -> "UNAVAILABLE";
        };
    }

    private String displayName(Long userId) {
        if (userId == null) return null;
        List<UserDO> users = userService.listByIdsIncludingDeleted(List.of(userId));
        return users == null ? null : users.stream().findFirst()
                .map(com.brad.pms.convertor.Convertors::userDisplayName).orElse(null);
    }
}
