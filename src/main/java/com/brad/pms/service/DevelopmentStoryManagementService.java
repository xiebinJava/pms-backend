package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.common.enums.DevelopmentAssignmentType;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.DevelopmentStorySaveCmd;
import com.brad.pms.dto.response.DevelopmentTopicStoryDTO;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.mapper.DevelopmentItemTaskMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.service.ProjectMemberAssignmentService;
import com.brad.pms.workflow.DevelopmentItemType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DevelopmentStoryManagementService {

    private static final String NOT_STARTED = "NOT_STARTED";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String TESTING = "TESTING";
    private static final String DONE = "DONE";
    private static final String BLOCKED = "BLOCKED";

    private final ProjectNodeDevelopmentStoryMapper storyMapper;
    private final DevelopmentTopicManagementService topicManagementService;
    private final DevelopmentItemWorkflowService developmentItemWorkflowService;
    private final DevelopmentItemWorkflowMapper workflowMapper;
    private final DevelopmentItemTaskMapper taskMapper;
    private final UserService userService;
    private final ProjectMemberAssignmentService assignmentService;

    @Transactional
    public Long create(DevelopmentStorySaveCmd cmd) {
        validate(cmd);
        ProjectNodeDevelopmentTopicDO topic = cmd.getTopicId() == null ? null
                : topicManagementService.requireWritableTopic(cmd.getTopicId());
        if (cmd.getOwnerId() != null) userService.requireActiveUser(cmd.getOwnerId());
        ProjectNodeDevelopmentStoryDO story = new ProjectNodeDevelopmentStoryDO();
        apply(story, cmd, topic, 0, resolveTopicWorkflowNodeId(topic));
        story.setCreatedBy(com.brad.pms.security.UserContext.userIdOrNull());
        if (storyMapper.insert(story) != 1 || story.getId() == null) {
            throw BusinessException.conflict("故事创建失败，请重试");
        }
        DevelopmentItemWorkflowDO workflow = developmentItemWorkflowService.createIfDefaultExists(
                DevelopmentItemType.STORY, story.getId(), story.getProjectId(), story.getNodeId());
        if (workflow == null) throw BusinessException.error("请先发布并设置故事流程模板为默认流程");
        replaceOwner(story, null, story.getOwnerId());
        return story.getId();
    }

    @Transactional
    public void update(Long id, DevelopmentStorySaveCmd cmd) {
        if (id == null) throw BusinessException.notFound("故事不存在");
        validate(cmd);
        ProjectNodeDevelopmentStoryDO story = storyMapper.selectByIdForUpdate(id);
        if (story == null) throw BusinessException.notFound("故事不存在");
        ProjectNodeDevelopmentTopicDO oldTopic = story.getTopicId() == null ? null
                : topicManagementService.requireWritableTopic(story.getTopicId());
        ProjectNodeDevelopmentTopicDO targetTopic = cmd.getTopicId() == null ? null
                : topicManagementService.requireWritableTopic(cmd.getTopicId());
        if (cmd.getOwnerId() != null) userService.requireActiveUser(cmd.getOwnerId());

        Long sourceProjectId = story.getProjectId();
        Long oldOwnerId = story.getOwnerId();
        Long targetProjectId = targetTopic == null ? null : targetTopic.getProjectId();
        Long targetTopicWorkflowNodeId = resolveTopicWorkflowNodeId(targetTopic);
        boolean contextChanged = !Objects.equals(story.getTopicId(), cmd.getTopicId())
                || !Objects.equals(story.getProjectId(), targetProjectId)
                || !Objects.equals(story.getNodeId(), targetTopic == null ? null : targetTopic.getNodeId())
                || !Objects.equals(story.getTopicWorkflowNodeId(), targetTopicWorkflowNodeId);
        DevelopmentItemWorkflowDO workflow = workflowMapper.selectForUpdate("STORY", story.getId());
        if (workflow != null) taskMapper.selectByWorkflowIdsForUpdate(List.of(workflow.getId()));

        apply(story, cmd, targetTopic, story.getSort() == null ? 0 : story.getSort(), targetTopicWorkflowNodeId);
        if (contextChanged) story.setIterationPlanId(null);
        if (storyMapper.updateById(story) != 1) throw BusinessException.conflict("故事已被其他人修改，请刷新后重试");

        if (workflow != null && contextChanged) {
            workflow.setProjectId(story.getProjectId());
            workflow.setSourceNodeId(story.getNodeId());
            if (workflowMapper.updateById(workflow) != 1) {
                throw BusinessException.conflict("故事流程已被其他人修改，请刷新后重试");
            }
        }
        DevelopmentItemWorkflowDO targetWorkflow = developmentItemWorkflowService.createIfDefaultExists(
                DevelopmentItemType.STORY, story.getId(), story.getProjectId(), story.getNodeId());
        if (targetWorkflow == null) throw BusinessException.error("请先发布并设置故事流程模板为默认流程");
        if (contextChanged) {
            assignmentService.synchronizeItemAssignments(sourceProjectId, targetProjectId,
                    DevelopmentItemType.STORY, story.getId());
        } else if (!Objects.equals(oldOwnerId, cmd.getOwnerId())) {
            replaceOwner(story, oldOwnerId, cmd.getOwnerId());
        }
    }

    public List<DevelopmentTopicStoryDTO> listByTopic(Long topicId) {
        ProjectNodeDevelopmentTopicDO topic = topicManagementService.requireReadableTopic(topicId);
        List<ProjectNodeDevelopmentStoryDO> stories = storyMapper.selectByTopicId(topic.getId());
        return (stories == null ? List.<ProjectNodeDevelopmentStoryDO>of() : stories).stream()
                .map(this::toDTO).toList();
    }

    private void replaceOwner(ProjectNodeDevelopmentStoryDO story, Long oldUserId, Long newUserId) {
        assignmentService.replaceAssignment(story.getProjectId(), DevelopmentItemType.STORY, story.getId(),
                DevelopmentAssignmentType.STORY_OWNER, story.getId(), newUserId);
    }

    private void validate(DevelopmentStorySaveCmd cmd) {
        if (cmd == null) throw BusinessException.error("故事信息不能为空");
        if (!StringUtils.hasText(cmd.getTitle())) throw BusinessException.error("故事名称不能为空");
        if (cmd.getStartDate() != null && cmd.getDueDate() != null
                && cmd.getStartDate().isAfter(cmd.getDueDate())) {
            throw BusinessException.error("故事开始日期不能晚于结束日期");
        }
        String status = normalizeStatus(cmd.getStatus());
        cmd.setStatus(status);
        int progress = cmd.getProgress() == null ? 0 : cmd.getProgress();
        if (progress < 0 || progress > 100) throw BusinessException.error("故事进度必须在 0 到 100 之间");
        cmd.setProgress(effectiveProgress(status, progress));
        cmd.setStoryPoints(cmd.getStoryPoints() == null ? 0 : cmd.getStoryPoints());
        if (cmd.getStoryPoints() < 0 || cmd.getStoryPoints() > 1000) {
            throw BusinessException.error("故事点必须在 0 到 1000 之间");
        }
        cmd.setTitle(cmd.getTitle().trim());
        cmd.setBlocker(trimToNull(cmd.getBlocker()));
    }

    private Long resolveTopicWorkflowNodeId(ProjectNodeDevelopmentTopicDO topic) {
        if (topic == null) return null;
        Long nodeId = developmentItemWorkflowService.resolveTopicStoryMountNodeId(topic.getId());
        if (nodeId == null) throw BusinessException.error("专题流程未配置故事挂载节点");
        return nodeId;
    }

    private void apply(ProjectNodeDevelopmentStoryDO story, DevelopmentStorySaveCmd cmd,
                       ProjectNodeDevelopmentTopicDO topic, int defaultSort, Long topicWorkflowNodeId) {
        story.setTopicId(topic == null ? null : topic.getId());
        story.setTopicWorkflowNodeId(topicWorkflowNodeId);
        story.setProjectId(topic == null ? null : topic.getProjectId());
        story.setNodeId(topic == null ? null : topic.getNodeId());
        story.setTitle(cmd.getTitle());
        story.setOwnerId(cmd.getOwnerId());
        story.setStatus(cmd.getStatus());
        story.setProgress(cmd.getProgress());
        story.setStoryPoints(cmd.getStoryPoints());
        story.setStartDate(cmd.getStartDate());
        story.setDueDate(cmd.getDueDate());
        story.setBlocker(cmd.getBlocker());
        story.setSort(cmd.getSort() == null ? defaultSort : cmd.getSort());
    }

    private DevelopmentTopicStoryDTO toDTO(ProjectNodeDevelopmentStoryDO story) {
        DevelopmentTopicStoryDTO dto = new DevelopmentTopicStoryDTO();
        dto.setId(story.getId());
        dto.setTitle(story.getTitle());
        dto.setOwnerId(story.getOwnerId());
        dto.setOwnerName(story.getOwnerId() == null ? null
                : userService.listByIdsIncludingDeleted(List.of(story.getOwnerId())).stream().findFirst()
                .map(com.brad.pms.convertor.Convertors::userDisplayName).orElse(null));
        dto.setStatus(story.getStatus());
        dto.setProgress(effectiveProgress(story.getStatus(), story.getProgress()));
        dto.setStoryPoints(story.getStoryPoints());
        dto.setStartDate(story.getStartDate());
        dto.setDueDate(story.getDueDate());
        dto.setBlocker(story.getBlocker());
        dto.setSort(story.getSort());
        return dto;
    }

    private String normalizeStatus(String status) {
        String value = StringUtils.hasText(status) ? status.trim().toUpperCase() : NOT_STARTED;
        if (!List.of(NOT_STARTED, IN_PROGRESS, TESTING, DONE, BLOCKED).contains(value)) {
            throw BusinessException.error("故事状态不合法");
        }
        return value;
    }

    private int effectiveProgress(String status, Integer progress) {
        if (DONE.equals(status)) return 100;
        if (NOT_STARTED.equals(status)) return 0;
        return progress == null ? 0 : Math.max(0, Math.min(100, progress));
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
