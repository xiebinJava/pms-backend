package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
import com.brad.pms.common.enums.ProjectStatus;
import com.brad.pms.common.enums.RequirementExecutionTargetType;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.page.PageResult;
import com.brad.pms.dto.request.RequirementExecutionTargetCmd;
import com.brad.pms.dto.request.RequirementExecutionTargetOptionQry;
import com.brad.pms.dto.response.RequirementExecutionTargetDTO;
import com.brad.pms.dto.response.RequirementExecutionTargetHistoryDTO;
import com.brad.pms.dto.response.RequirementExecutionTargetOptionDTO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.entity.RequirementDO;
import com.brad.pms.entity.RequirementExecutionTargetHistoryDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.mapper.RequirementExecutionTargetHistoryMapper;
import com.brad.pms.mapper.RequirementMapper;
import com.brad.pms.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RequirementExecutionTargetService {
    private final RequirementMapper requirementMapper;
    private final RequirementExecutionTargetHistoryMapper historyMapper;
    private final ProjectMapper projectMapper;
    private final ProjectNodeDevelopmentTopicMapper topicMapper;
    private final ProjectNodeDevelopmentStoryMapper storyMapper;
    private final ProjectPermissionService permissionService;
    private final UserService userService;
    private final OperationLogService operationLogService;

    @Transactional
    public RequirementExecutionTargetDTO link(Long requirementId, RequirementExecutionTargetCmd cmd) {
        RequirementDO requirement = requireRequirementForUpdate(requirementId, cmd == null ? null : cmd.getRequirementVersion());
        if (hasTarget(requirement)) throw BusinessException.conflict("需求已有执行对象，请使用改绑操作");
        TargetSnapshot target = validateTarget(cmd);
        applyTarget(requirement, target);
        updateRequirement(requirement);
        appendHistory(requirement, "LINK", target, null, cmd.getReason());
        audit(requirement, AuditAction.REQUIREMENT_EXECUTION_TARGET_LINKED.name(), null, target, cmd.getReason());
        return toTargetDTO(target);
    }

    @Transactional
    public RequirementExecutionTargetDTO unlink(Long requirementId, Integer requirementVersion, String reason) {
        RequirementDO requirement = requireRequirementForUpdate(requirementId, requirementVersion);
        if (!hasTarget(requirement)) throw BusinessException.conflict("需求当前没有执行对象");
        TargetSnapshot previous = snapshot(requirement.getExecutionTargetType(), requirement.getExecutionTargetId());
        clearTarget(requirement);
        updateRequirement(requirement);
        appendHistory(requirement, "UNLINK", null, previous, reason);
        audit(requirement, AuditAction.REQUIREMENT_EXECUTION_TARGET_UNLINKED.name(), previous, null, reason);
        return null;
    }

    @Transactional
    public RequirementExecutionTargetDTO change(Long requirementId, RequirementExecutionTargetCmd cmd) {
        if (cmd == null || !StringUtils.hasText(cmd.getReason())) {
            throw BusinessException.error("改绑执行对象必须填写原因");
        }
        RequirementDO requirement = requireRequirementForUpdate(requirementId, cmd.getRequirementVersion());
        if (!hasTarget(requirement)) throw BusinessException.conflict("需求当前没有执行对象，请使用关联操作");
        TargetSnapshot previous = snapshot(requirement.getExecutionTargetType(), requirement.getExecutionTargetId());
        TargetSnapshot target = validateTarget(cmd);
        if (previous.sameTarget(target)) throw BusinessException.error("新的执行对象不能与当前对象相同");
        applyTarget(requirement, target);
        updateRequirement(requirement);
        appendHistory(requirement, "REPLACE", target, previous, cmd.getReason());
        audit(requirement, AuditAction.REQUIREMENT_EXECUTION_TARGET_REPLACED.name(), previous, target, cmd.getReason());
        return toTargetDTO(target);
    }

    public PageResult<RequirementExecutionTargetOptionDTO> options(
            Long requirementId, RequirementExecutionTargetOptionQry qry) {
        RequirementDO requirement = requirementId == null ? null : requirementMapper.selectById(requirementId);
        if (requirement == null || Boolean.TRUE.equals(requirement.getDeleted())) {
            throw BusinessException.notFound("需求不存在");
        }
        RequirementExecutionTargetOptionQry query = qry == null ? new RequirementExecutionTargetOptionQry() : qry;
        List<RequirementExecutionTargetOptionDTO> options = new ArrayList<>();
        if (query.getTargetType() == null || query.getTargetType() == RequirementExecutionTargetType.PROJECT) {
            options.addAll(projectOptions(query.getKeyword()));
        }
        if (query.getTargetType() == null || query.getTargetType() == RequirementExecutionTargetType.TOPIC) {
            options.addAll(topicOptions(query.getKeyword()));
        }
        if (query.getTargetType() == null || query.getTargetType() == RequirementExecutionTargetType.STORY) {
            options.addAll(storyOptions(query.getKeyword()));
        }
        int page = query.getCurrPage();
        int size = query.getPageSize();
        int from = Math.min((page - 1) * size, options.size());
        int to = Math.min(from + size, options.size());
        return PageResult.of(options.size(), page, size, options.subList(from, to));
    }

    public List<RequirementExecutionTargetHistoryDTO> history(Long requirementId) {
        List<RequirementExecutionTargetHistoryDO> history = historyMapper.selectList(
                new LambdaQueryWrapper<RequirementExecutionTargetHistoryDO>()
                        .eq(RequirementExecutionTargetHistoryDO::getRequirementId, requirementId)
                        .orderByDesc(RequirementExecutionTargetHistoryDO::getCreatedAt)
                        .orderByDesc(RequirementExecutionTargetHistoryDO::getId));
        if (history == null || history.isEmpty()) return List.of();
        List<Long> operatorIds = history.stream().map(RequirementExecutionTargetHistoryDO::getOperatorId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, UserDO> users = operatorIds.isEmpty() ? Map.of()
                : userService.listByIdsIncludingDeleted(operatorIds).stream()
                .collect(Collectors.toMap(UserDO::getId, Function.identity(), (left, right) -> left));
        return history.stream().map(item -> {
            RequirementExecutionTargetHistoryDTO dto = new RequirementExecutionTargetHistoryDTO();
            dto.setId(item.getId());
            dto.setRequirementId(item.getRequirementId());
            dto.setAction(item.getAction());
            dto.setTargetType(item.getTargetType());
            dto.setTargetId(item.getTargetId());
            dto.setPreviousTargetType(item.getPreviousTargetType());
            dto.setPreviousTargetId(item.getPreviousTargetId());
            dto.setReason(item.getReason());
            dto.setOperatorId(item.getOperatorId());
            UserDO operator = users.get(item.getOperatorId());
            dto.setOperatorName(operator == null ? null : com.brad.pms.convertor.Convertors.userDisplayName(operator));
            dto.setCreatedAt(item.getCreatedAt());
            return dto;
        }).toList();
    }

    private RequirementDO requireRequirementForUpdate(Long requirementId, Integer expectedVersion) {
        if (requirementId == null) throw BusinessException.notFound("需求不存在");
        RequirementDO requirement = requirementMapper.selectByIdForUpdate(requirementId);
        if (requirement == null || Boolean.TRUE.equals(requirement.getDeleted())) {
            throw BusinessException.notFound("需求不存在");
        }
        if (!Objects.equals(requirement.getVersion(), expectedVersion)) {
            throw BusinessException.conflict("需求已被其他人修改，请刷新后重试");
        }
        return requirement;
    }

    private TargetSnapshot validateTarget(RequirementExecutionTargetCmd cmd) {
        if (cmd == null || cmd.getTargetType() == null || cmd.getTargetId() == null) {
            throw BusinessException.error("执行对象信息不完整");
        }
        return switch (cmd.getTargetType()) {
            case PROJECT -> validateProject(cmd.getTargetId());
            case TOPIC -> validateTopic(cmd.getTargetId());
            case STORY -> validateStory(cmd.getTargetId());
        };
    }

    private TargetSnapshot validateProject(Long targetId) {
        ProjectDO project = projectMapper.selectIncludingDeleted(targetId);
        if (project == null || Boolean.TRUE.equals(project.getDeleted())
                || !ProjectStatus.isOpen(project.getStatus())) {
            throw BusinessException.error("执行项目必须是进行中的项目");
        }
        permissionService.requireProjectReadable(targetId);
        return new TargetSnapshot(RequirementExecutionTargetType.PROJECT, targetId, project.getName(), project.getCode(),
                projectStatus(project), project.getOwnerId(), project.getProgress(),
                "project", targetId);
    }

    private TargetSnapshot validateTopic(Long targetId) {
        ProjectNodeDevelopmentTopicDO topic = topicMapper.selectById(targetId);
        if (topic == null || Boolean.TRUE.equals(topic.getDeleted())) throw BusinessException.notFound("专题不存在");
        if (topic.getProjectId() != null) permissionService.requireProjectReadable(topic.getProjectId());
        return new TargetSnapshot(RequirementExecutionTargetType.TOPIC, targetId, topic.getTitle(), null,
                "ACTIVE", topic.getOwnerId(), null, "topic", targetId);
    }

    private TargetSnapshot validateStory(Long targetId) {
        ProjectNodeDevelopmentStoryDO story = storyMapper.selectById(targetId);
        if (story == null) throw BusinessException.notFound("故事不存在");
        if (story.getProjectId() != null) permissionService.requireProjectReadable(story.getProjectId());
        if (story.getTopicId() != null) {
            ProjectNodeDevelopmentTopicDO topic = topicMapper.selectById(story.getTopicId());
            if (topic == null || Boolean.TRUE.equals(topic.getDeleted())) throw BusinessException.notFound("故事所属专题不存在");
        }
        int progress = "DONE".equals(story.getStatus()) ? 100 : story.getProgress() == null ? 0 : story.getProgress();
        return new TargetSnapshot(RequirementExecutionTargetType.STORY, targetId, story.getTitle(), null,
                story.getStatus(), story.getOwnerId(), progress, "story", targetId);
    }

    private TargetSnapshot snapshot(RequirementExecutionTargetType type, Long targetId) {
        return switch (type) {
            case PROJECT -> snapshotProject(targetId);
            case TOPIC -> snapshotTopic(targetId);
            case STORY -> snapshotStory(targetId);
        };
    }

    private TargetSnapshot snapshotProject(Long targetId) {
        ProjectDO project = projectMapper.selectIncludingDeleted(targetId);
        if (project == null) {
            return new TargetSnapshot(RequirementExecutionTargetType.PROJECT, targetId, null, null,
                    "UNAVAILABLE", null, null, "project", targetId);
        }
        return new TargetSnapshot(RequirementExecutionTargetType.PROJECT, targetId, project.getName(), project.getCode(),
                projectStatus(project), project.getOwnerId(), project.getProgress(),
                "project", targetId);
    }

    private TargetSnapshot snapshotTopic(Long targetId) {
        ProjectNodeDevelopmentTopicDO topic = topicMapper.selectById(targetId);
        return topic == null
                ? new TargetSnapshot(RequirementExecutionTargetType.TOPIC, targetId, null, null, "UNAVAILABLE",
                null, null, "topic", targetId)
                : new TargetSnapshot(RequirementExecutionTargetType.TOPIC, targetId, topic.getTitle(), null,
                Boolean.TRUE.equals(topic.getDeleted()) ? "DELETED" : "ACTIVE", topic.getOwnerId(), null,
                "topic", targetId);
    }

    private TargetSnapshot snapshotStory(Long targetId) {
        ProjectNodeDevelopmentStoryDO story = storyMapper.selectById(targetId);
        return story == null
                ? new TargetSnapshot(RequirementExecutionTargetType.STORY, targetId, null, null, "UNAVAILABLE",
                null, null, "story", targetId)
                : new TargetSnapshot(RequirementExecutionTargetType.STORY, targetId, story.getTitle(), null,
                story.getStatus(), story.getOwnerId(), story.getProgress(), "story", targetId);
    }

    private boolean hasTarget(RequirementDO requirement) {
        return requirement.getExecutionTargetType() != null && requirement.getExecutionTargetId() != null;
    }

    private void applyTarget(RequirementDO requirement, TargetSnapshot target) {
        requirement.setExecutionTargetType(target.type());
        requirement.setExecutionTargetId(target.id());
    }

    private void clearTarget(RequirementDO requirement) {
        requirement.setExecutionTargetType(null);
        requirement.setExecutionTargetId(null);
    }

    private void updateRequirement(RequirementDO requirement) {
        if (requirementMapper.updateById(requirement) != 1) {
            throw BusinessException.conflict("需求已被其他人修改，请刷新后重试");
        }
    }

    private void appendHistory(RequirementDO requirement, String action, TargetSnapshot target,
                               TargetSnapshot previous, String reason) {
        RequirementExecutionTargetHistoryDO history = new RequirementExecutionTargetHistoryDO();
        history.setRequirementId(requirement.getId());
        history.setAction(action);
        history.setTargetType(target == null ? null : target.type());
        history.setTargetId(target == null ? null : target.id());
        history.setPreviousTargetType(previous == null ? null : previous.type());
        history.setPreviousTargetId(previous == null ? null : previous.id());
        history.setReason(StringUtils.hasText(reason) ? reason.trim() : null);
        history.setOperatorId(UserContext.userIdOrNull());
        if (historyMapper.insert(history) != 1) throw BusinessException.conflict("执行对象变更记录失败，请重试");
    }

    private void audit(RequirementDO requirement, String action, TargetSnapshot previous,
                       TargetSnapshot target, String reason) {
        operationLogService.record(AuditEvent.success(action, AuditResourceType.REQUIREMENT.name(), requirement.getId(),
                null, reason, auditTarget(previous), auditTarget(target)));
    }

    private Map<String, Object> auditTarget(TargetSnapshot target) {
        if (target == null) return Map.of("target", "NONE");
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("targetType", target.type().name());
        value.put("targetId", target.id());
        return value;
    }

    private List<RequirementExecutionTargetOptionDTO> projectOptions(String keyword) {
        List<ProjectDO> projects = projectMapper.selectList(new LambdaQueryWrapper<ProjectDO>()
                .eq(ProjectDO::getDeleted, false)
                .in(ProjectDO::getStatus, 0, ProjectStatus.ACTIVE.getCode())
                .like(StringUtils.hasText(keyword), ProjectDO::getName, keyword)
                .orderByDesc(ProjectDO::getUpdatedAt));
        return safe(projects).stream().filter(project -> {
            try {
                permissionService.requireProjectReadable(project.getId());
                return true;
            } catch (BusinessException ignored) {
                return false;
            }
        }).map(project -> option(new TargetSnapshot(RequirementExecutionTargetType.PROJECT, project.getId(),
                project.getName(), project.getCode(), projectStatus(project),
                project.getOwnerId(), project.getProgress(), "project", project.getId()))).toList();
    }

    private List<RequirementExecutionTargetOptionDTO> topicOptions(String keyword) {
        return safe(topicMapper.selectList(new LambdaQueryWrapper<ProjectNodeDevelopmentTopicDO>()
                .eq(ProjectNodeDevelopmentTopicDO::getDeleted, false)
                .like(StringUtils.hasText(keyword), ProjectNodeDevelopmentTopicDO::getTitle, keyword)
                .orderByAsc(ProjectNodeDevelopmentTopicDO::getSort)
                .orderByAsc(ProjectNodeDevelopmentTopicDO::getId))).stream()
                .filter(topic -> topic.getProjectId() == null || readable(topic.getProjectId()))
                .map(topic -> option(new TargetSnapshot(RequirementExecutionTargetType.TOPIC, topic.getId(),
                        topic.getTitle(), null, "ACTIVE", topic.getOwnerId(), null, "topic", topic.getId())))
                .toList();
    }

    private List<RequirementExecutionTargetOptionDTO> storyOptions(String keyword) {
        return safe(storyMapper.selectList(new LambdaQueryWrapper<ProjectNodeDevelopmentStoryDO>()
                .like(StringUtils.hasText(keyword), ProjectNodeDevelopmentStoryDO::getTitle, keyword)
                .orderByAsc(ProjectNodeDevelopmentStoryDO::getSort)
                .orderByAsc(ProjectNodeDevelopmentStoryDO::getId))).stream()
                .filter(story -> story.getProjectId() == null || readable(story.getProjectId()))
                .map(story -> option(new TargetSnapshot(RequirementExecutionTargetType.STORY, story.getId(),
                        story.getTitle(), null, story.getStatus(), story.getOwnerId(), story.getProgress(), "story", story.getId())))
                .toList();
    }

    private boolean readable(Long projectId) {
        try {
            permissionService.requireProjectReadable(projectId);
            return true;
        } catch (BusinessException ignored) {
            return false;
        }
    }

    private String projectStatus(ProjectDO project) {
        if (project == null) return "UNAVAILABLE";
        if (Boolean.TRUE.equals(project.getDeleted())) return "DELETED";
        return switch (ProjectStatus.normalize(project.getStatus())) {
            case 1 -> "ACTIVE";
            case 2 -> "COMPLETED";
            case 3 -> "TERMINATED";
            default -> "UNAVAILABLE";
        };
    }

    private RequirementExecutionTargetOptionDTO option(TargetSnapshot target) {
        RequirementExecutionTargetOptionDTO dto = new RequirementExecutionTargetOptionDTO();
        dto.setTargetType(target.type());
        dto.setTargetId(target.id());
        dto.setTitle(target.title());
        dto.setCode(target.code());
        dto.setStatus(target.status());
        dto.setOwnerId(target.ownerId());
        dto.setOwnerName(displayName(target.ownerId()));
        dto.setProgress(target.progress());
        dto.setNavigationType(target.navigationType());
        dto.setNavigationId(target.navigationId());
        return dto;
    }

    private RequirementExecutionTargetDTO toTargetDTO(TargetSnapshot target) {
        RequirementExecutionTargetDTO dto = new RequirementExecutionTargetDTO();
        dto.setTargetType(target.type());
        dto.setTargetId(target.id());
        dto.setTitle(target.title());
        dto.setCode(target.code());
        dto.setStatus(target.status());
        dto.setOwnerId(target.ownerId());
        dto.setOwnerName(displayName(target.ownerId()));
        dto.setProgress(target.progress());
        dto.setNavigationType(target.navigationType());
        dto.setNavigationId(target.navigationId());
        return dto;
    }

    private String displayName(Long userId) {
        if (userId == null) return null;
        List<UserDO> users = userService.listByIdsIncludingDeleted(List.of(userId));
        return users == null ? null : users.stream().findFirst()
                .map(com.brad.pms.convertor.Convertors::userDisplayName).orElse(null);
    }

    private <T> List<T> safe(List<T> value) {
        return value == null ? List.of() : value;
    }

    private record TargetSnapshot(RequirementExecutionTargetType type, Long id, String title, String code,
                                  String status, Long ownerId, Integer progress,
                                  String navigationType, Long navigationId) {
        private boolean sameTarget(TargetSnapshot other) {
            return other != null && type == other.type && Objects.equals(id, other.id);
        }
    }
}
