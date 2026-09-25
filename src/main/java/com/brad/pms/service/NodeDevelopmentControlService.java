package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
import com.brad.pms.common.enums.NodeStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.request.NodeDevelopmentControlUpdateCmd;
import com.brad.pms.dto.request.NodeDevelopmentStoryCmd;
import com.brad.pms.dto.request.NodeDevelopmentTopicCmd;
import com.brad.pms.dto.response.NodeDevelopmentControlDTO;
import com.brad.pms.dto.response.NodeDevelopmentStoryDTO;
import com.brad.pms.dto.response.NodeDevelopmentSummaryDTO;
import com.brad.pms.dto.response.NodeDevelopmentTopicDTO;
import com.brad.pms.dto.response.NodeIterationPlanDTO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeDevelopmentBaselineDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectNodeDevelopmentBaselineMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.security.UserContext;
import com.brad.pms.workflow.WorkflowComponentKey;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NodeDevelopmentControlService {

    private static final Set<String> STORY_STATUSES = Set.of("NOT_STARTED", "IN_PROGRESS", "TESTING", "DONE", "BLOCKED");
    private static final Set<String> TEST_STATUSES = Set.of("NOT_STARTED", "TESTING", "PASSED", "FAILED");

    private final ProjectNodeDevelopmentBaselineMapper baselineMapper;
    private final ProjectNodeDevelopmentTopicMapper topicMapper;
    private final ProjectNodeDevelopmentStoryMapper storyMapper;
    private final IterationPlanService iterationPlanService;
    private final MemberService memberService;
    private final ProjectPermissionService permissionService;
    private final UserService userService;
    private final OperationLogService operationLogService;

    public NodeDevelopmentControlDTO get(Long projectId, Long nodeId) {
        permissionService.requireProjectReadable(projectId);
        ProjectNodeDO node = requireDevelopNode(permissionService.requireNode(projectId, nodeId));
        return toDTO(node, findBaseline(projectId, nodeId));
    }

    /**
     * 节点完成前的业务校验。专题状态由故事状态推导，节点完成只要求所有故事已完成。
     */
    public void requireCompleted(Long projectId, Long nodeId) {
        permissionService.requireProjectReadable(projectId);
        ProjectNodeDO node = requireDevelopNode(permissionService.requireNode(projectId, nodeId));
        NodeDevelopmentControlDTO control = toDTO(node, findBaseline(projectId, nodeId));
        if (control.getTopics().isEmpty()) {
            throw BusinessException.error("请先至少建立一个专题");
        }
        List<NodeDevelopmentStoryDTO> stories = control.getTopics().stream()
                .flatMap(topic -> topic.getStories().stream())
                .collect(Collectors.toList());
        if (stories.isEmpty()) {
            throw BusinessException.error("请先至少建立一个故事");
        }

        List<String> incompleteStories = stories.stream()
                .filter(story -> !"DONE".equals(story.getStatus()))
                .map(NodeDevelopmentStoryDTO::getTitle)
                .limit(3)
                .collect(Collectors.toList());
        if (!incompleteStories.isEmpty()) {
            throw BusinessException.error("请先完成全部故事：" + String.join("、", incompleteStories));
        }

    }

    @Transactional
    public NodeDevelopmentControlDTO save(Long projectId, Long nodeId, NodeDevelopmentControlUpdateCmd cmd) {
        ProjectNodeDO node = requireDevelopNode(permissionService.requireManageableNode(
                projectId, nodeId, "保存开发测试与项目控制"));
        validatePayload(cmd);
        validateIterationPlans(projectId, nodeId, cmd);
        validateProjectOwners(projectId, cmd);

        ProjectNodeDevelopmentBaselineDO baseline = findBaseline(projectId, nodeId);
        if (baseline == null) {
            baseline = newBaseline(projectId, nodeId);
            try {
                baselineMapper.insert(baseline);
            } catch (DuplicateKeyException ex) {
                throw BusinessException.conflict("开发控制工作台已被其他人创建，请刷新后重试");
            }
        } else {
            if (cmd.getVersion() == null) {
                throw BusinessException.conflict("开发控制工作台版本号不能为空，请刷新后重试");
            }
            if (!Objects.equals(cmd.getVersion(), baseline.getVersion())) {
                throw BusinessException.conflict("开发控制工作台已被其他人修改，请刷新后重试");
            }
        }

        baseline.setCurrentIteration(trim(cmd.getCurrentIteration()));
        if (baselineMapper.updateById(baseline) != 1) {
            throw BusinessException.conflict("开发控制工作台已被其他人修改，请刷新后重试");
        }
        replaceTopicsAndStories(projectId, nodeId, cmd.getTopics());

        NodeDevelopmentControlDTO result = toDTO(node, baseline);
        operationLogService.record(AuditEvent.success(
                AuditAction.NODE_DEVELOPMENT_CONTROL_SAVED.name(), AuditResourceType.PROJECT_NODE.name(),
                nodeId, projectId, null, null, result));
        return result;
    }

    private void validatePayload(NodeDevelopmentControlUpdateCmd cmd) {
        if (cmd == null) throw BusinessException.error("开发控制工作台内容不能为空");
        cmd.setCurrentIteration(trim(cmd.getCurrentIteration()));
        List<NodeDevelopmentTopicCmd> topics = cmd.getTopics() == null ? new ArrayList<>() : cmd.getTopics();
        for (int topicIndex = 0; topicIndex < topics.size(); topicIndex++) {
            NodeDevelopmentTopicCmd topic = topics.get(topicIndex);
            topic.setTitle(trim(topic.getTitle()));
            topic.setLatestBuildVersion(trim(topic.getLatestBuildVersion()));
            topic.setTestStatus(normalize(topic.getTestStatus(), "NOT_STARTED"));
            topic.setSort(topic.getSort() == null ? topicIndex : topic.getSort());
            if (topic.getTitle() == null) throw BusinessException.error("专题名称不能为空");
            if (!TEST_STATUSES.contains(topic.getTestStatus())) throw BusinessException.error("专题测试状态不合法");

            List<NodeDevelopmentStoryCmd> stories = topic.getStories() == null ? new ArrayList<>() : topic.getStories();
            topic.setStories(stories);
            for (int storyIndex = 0; storyIndex < stories.size(); storyIndex++) {
                NodeDevelopmentStoryCmd story = stories.get(storyIndex);
                story.setTitle(trim(story.getTitle()));
                story.setStatus(normalize(story.getStatus(), "NOT_STARTED"));
                story.setProgress(story.getProgress() == null ? 0 : story.getProgress());
                story.setStoryPoints(story.getStoryPoints() == null ? 0 : story.getStoryPoints());
                story.setBlocker(trim(story.getBlocker()));
                story.setSort(story.getSort() == null ? storyIndex : story.getSort());
                if (story.getTitle() == null) throw BusinessException.error("故事名称不能为空");
                if (!STORY_STATUSES.contains(story.getStatus())) throw BusinessException.error("故事状态不合法");
                if (story.getProgress() < 0 || story.getProgress() > 100) throw BusinessException.error("故事进度必须在 0 到 100 之间");
                story.setProgress(effectiveProgress(story.getStatus(), story.getProgress()));
                if (story.getStoryPoints() < 0 || story.getStoryPoints() > 1000) throw BusinessException.error("故事点必须在 0 到 1000 之间");
                if (story.getStartDate() != null && story.getDueDate() != null
                        && story.getStartDate().isAfter(story.getDueDate())) {
                    throw BusinessException.error("故事开始日期不能晚于结束日期");
                }
            }
        }
        cmd.setTopics(topics);
    }

    private void validateIterationPlans(Long projectId, Long nodeId, NodeDevelopmentControlUpdateCmd cmd) {
        Set<Long> iterationPlanIds = cmd.getTopics().stream()
                .flatMap(topic -> topic.getStories().stream())
                .map(NodeDevelopmentStoryCmd::getIterationPlanId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (iterationPlanIds.isEmpty()) return;
        Set<Long> allowedPlanIds = new HashSet<>(iterationPlanService.confirmedPlanIds(projectId));
        List<ProjectNodeDevelopmentStoryDO> existingStories = storyMapper.selectList(new LambdaQueryWrapper<ProjectNodeDevelopmentStoryDO>()
                .eq(ProjectNodeDevelopmentStoryDO::getProjectId, projectId)
                .eq(ProjectNodeDevelopmentStoryDO::getNodeId, nodeId));
        Map<Long, Long> existingPlanByStoryId = (existingStories == null ? List.<ProjectNodeDevelopmentStoryDO>of() : existingStories).stream()
                .filter(story -> story.getId() != null && story.getIterationPlanId() != null)
                .collect(Collectors.toMap(ProjectNodeDevelopmentStoryDO::getId, ProjectNodeDevelopmentStoryDO::getIterationPlanId, (left, right) -> left));
        cmd.getTopics().stream()
                .flatMap(topic -> topic.getStories().stream())
                .filter(story -> story.getId() != null && story.getIterationPlanId() != null)
                .filter(story -> Objects.equals(existingPlanByStoryId.get(story.getId()), story.getIterationPlanId()))
                .map(NodeDevelopmentStoryCmd::getIterationPlanId)
                .forEach(allowedPlanIds::add);
        if (!allowedPlanIds.containsAll(iterationPlanIds)) {
            throw BusinessException.error("故事所属迭代计划必须来自当前项目已确认的计划基线");
        }
    }

    private void validateProjectOwners(Long projectId, NodeDevelopmentControlUpdateCmd cmd) {
        Set<Long> ownerIds = cmd.getTopics().stream().flatMap(topic -> {
            List<Long> topicAndStoryOwners = new ArrayList<>();
            if (topic.getOwnerId() != null) topicAndStoryOwners.add(topic.getOwnerId());
            topic.getStories().stream().map(NodeDevelopmentStoryCmd::getOwnerId)
                    .filter(Objects::nonNull).forEach(topicAndStoryOwners::add);
            return topicAndStoryOwners.stream();
        }).collect(Collectors.toCollection(HashSet::new));
        memberService.ensureMembers(projectId, ownerIds);
    }

    private void replaceTopicsAndStories(Long projectId, Long nodeId, List<NodeDevelopmentTopicCmd> topics) {
        List<ProjectNodeDevelopmentTopicDO> existingTopics = topicMapper.selectList(new LambdaQueryWrapper<ProjectNodeDevelopmentTopicDO>()
                .eq(ProjectNodeDevelopmentTopicDO::getProjectId, projectId)
                .eq(ProjectNodeDevelopmentTopicDO::getNodeId, nodeId));
        List<ProjectNodeDevelopmentStoryDO> existingStories = storyMapper.selectList(new LambdaQueryWrapper<ProjectNodeDevelopmentStoryDO>()
                .eq(ProjectNodeDevelopmentStoryDO::getProjectId, projectId)
                .eq(ProjectNodeDevelopmentStoryDO::getNodeId, nodeId));

        Map<Long, ProjectNodeDevelopmentTopicDO> topicsById = existingTopics.stream()
                .filter(topic -> topic.getId() != null)
                .collect(Collectors.toMap(ProjectNodeDevelopmentTopicDO::getId, topic -> topic));
        Map<Long, ProjectNodeDevelopmentStoryDO> storiesById = existingStories.stream()
                .filter(story -> story.getId() != null)
                .collect(Collectors.toMap(ProjectNodeDevelopmentStoryDO::getId, story -> story));
        Set<Long> retainedTopicIds = new HashSet<>();
        Set<Long> retainedStoryIds = new HashSet<>();

        for (int topicIndex = 0; topicIndex < topics.size(); topicIndex++) {
            NodeDevelopmentTopicCmd topicCmd = topics.get(topicIndex);
            ProjectNodeDevelopmentTopicDO topic = findOrCreateTopic(projectId, nodeId, topicCmd, topicsById, retainedTopicIds);
            applyTopicFields(topic, topicCmd, topicIndex);
            if (topic.getId() == null) {
                topic.setCreatedBy(UserContext.userIdOrNull());
                if (topicMapper.insert(topic) != 1) throw BusinessException.conflict("专题保存失败，请刷新后重试");
            } else if (topicMapper.updateById(topic) != 1) {
                throw BusinessException.conflict("专题已被其他人修改，请刷新后重试");
            }
            for (NodeDevelopmentStoryCmd storyCmd : topicCmd.getStories()) {
                ProjectNodeDevelopmentStoryDO story = findOrCreateStory(projectId, nodeId, topic, storyCmd, storiesById, retainedStoryIds);
                applyStoryFields(story, topic, storyCmd);
                if (story.getId() == null) {
                    story.setCreatedBy(UserContext.userIdOrNull());
                    if (storyMapper.insert(story) != 1) throw BusinessException.conflict("故事保存失败，请刷新后重试");
                } else if (storyMapper.updateById(story) != 1) {
                    throw BusinessException.conflict("故事已被其他人修改，请刷新后重试");
                }
            }
        }

        List<Long> removedStoryIds = existingStories.stream()
                .map(ProjectNodeDevelopmentStoryDO::getId)
                .filter(Objects::nonNull)
                .filter(storyId -> !retainedStoryIds.contains(storyId))
                .collect(Collectors.toList());
        if (!removedStoryIds.isEmpty()) {
            removedStoryIds.forEach(storyMapper::deleteById);
        }
        existingTopics.stream()
                .filter(topic -> topic.getId() != null && !retainedTopicIds.contains(topic.getId()))
                .forEach(topicMapper::deleteById);
    }

    private ProjectNodeDevelopmentTopicDO findOrCreateTopic(Long projectId, Long nodeId,
                                                              NodeDevelopmentTopicCmd cmd,
                                                              Map<Long, ProjectNodeDevelopmentTopicDO> topicsById,
                                                              Set<Long> retainedTopicIds) {
        if (!isPersistedId(cmd.getId())) {
            ProjectNodeDevelopmentTopicDO topic = new ProjectNodeDevelopmentTopicDO();
            topic.setProjectId(projectId);
            topic.setNodeId(nodeId);
            return topic;
        }
        ProjectNodeDevelopmentTopicDO topic = topicsById.get(cmd.getId());
        if (topic == null) throw BusinessException.error("专题不存在，请刷新后重试");
        if (!retainedTopicIds.add(topic.getId())) throw BusinessException.error("专题不能重复");
        return topic;
    }

    private ProjectNodeDevelopmentStoryDO findOrCreateStory(Long projectId, Long nodeId,
                                                              ProjectNodeDevelopmentTopicDO topic,
                                                              NodeDevelopmentStoryCmd cmd,
                                                              Map<Long, ProjectNodeDevelopmentStoryDO> storiesById,
                                                              Set<Long> retainedStoryIds) {
        if (!isPersistedId(cmd.getId())) {
            ProjectNodeDevelopmentStoryDO story = new ProjectNodeDevelopmentStoryDO();
            story.setProjectId(projectId);
            story.setNodeId(nodeId);
            return story;
        }
        ProjectNodeDevelopmentStoryDO story = storiesById.get(cmd.getId());
        if (story == null) throw BusinessException.error("故事不存在，请刷新后重试");
        if (!retainedStoryIds.add(story.getId())) throw BusinessException.error("故事不能重复");
        return story;
    }

    private void applyTopicFields(ProjectNodeDevelopmentTopicDO topic, NodeDevelopmentTopicCmd cmd, int topicIndex) {
        topic.setTitle(cmd.getTitle());
        topic.setOwnerId(cmd.getOwnerId());
        topic.setLatestBuildVersion(cmd.getLatestBuildVersion());
        topic.setTestStatus(cmd.getTestStatus());
        topic.setSort(cmd.getSort() == null ? topicIndex : cmd.getSort());
    }

    private void applyStoryFields(ProjectNodeDevelopmentStoryDO story,
                                  ProjectNodeDevelopmentTopicDO topic,
                                  NodeDevelopmentStoryCmd cmd) {
        story.setProjectId(topic.getProjectId());
        story.setNodeId(topic.getNodeId());
        story.setTopicId(topic.getId());
        story.setIterationPlanId(cmd.getIterationPlanId());
        story.setTitle(cmd.getTitle());
        story.setOwnerId(cmd.getOwnerId());
        story.setStatus(cmd.getStatus());
        story.setProgress(cmd.getProgress());
        story.setStoryPoints(cmd.getStoryPoints());
        story.setStartDate(cmd.getStartDate());
        story.setDueDate(cmd.getDueDate());
        story.setBlocker(cmd.getBlocker());
        story.setSort(cmd.getSort());
    }

    private boolean isPersistedId(Long id) {
        return id != null && id > 0;
    }

    private NodeDevelopmentControlDTO toDTO(ProjectNodeDO node, ProjectNodeDevelopmentBaselineDO baseline) {
        List<ProjectNodeDevelopmentTopicDO> topics = safeList(topicMapper.selectList(new LambdaQueryWrapper<ProjectNodeDevelopmentTopicDO>()
                .eq(ProjectNodeDevelopmentTopicDO::getProjectId, node.getProjectId())
                .eq(ProjectNodeDevelopmentTopicDO::getNodeId, node.getId())
                .orderByAsc(ProjectNodeDevelopmentTopicDO::getSort)
                .orderByAsc(ProjectNodeDevelopmentTopicDO::getId)));
        List<ProjectNodeDevelopmentStoryDO> stories = safeList(storyMapper.selectList(new LambdaQueryWrapper<ProjectNodeDevelopmentStoryDO>()
                .eq(ProjectNodeDevelopmentStoryDO::getProjectId, node.getProjectId())
                .eq(ProjectNodeDevelopmentStoryDO::getNodeId, node.getId())
                .orderByAsc(ProjectNodeDevelopmentStoryDO::getSort)
                .orderByAsc(ProjectNodeDevelopmentStoryDO::getId)));
        Set<Long> referencedIterationPlanIds = stories.stream()
                .map(ProjectNodeDevelopmentStoryDO::getIterationPlanId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<NodeIterationPlanDTO> developmentPlans = iterationPlanService.listForDevelopment(node.getProjectId(), referencedIterationPlanIds);
        Map<Long, String> iterationPlanNamesById = (developmentPlans == null ? List.<NodeIterationPlanDTO>of() : developmentPlans).stream()
                .filter(plan -> plan.getId() != null)
                .collect(Collectors.toMap(NodeIterationPlanDTO::getId, NodeIterationPlanDTO::getName));
        Set<Long> ownerIds = new HashSet<>();
        topics.stream().map(ProjectNodeDevelopmentTopicDO::getOwnerId)
                .filter(Objects::nonNull).forEach(ownerIds::add);
        stories.stream().map(ProjectNodeDevelopmentStoryDO::getOwnerId)
                .filter(Objects::nonNull).forEach(ownerIds::add);
        List<UserDO> owners = ownerIds.isEmpty() ? List.of() : userService.listByIds(new ArrayList<>(ownerIds));
        Map<Long, UserDO> userMap = Convertors.userMap(owners == null ? List.of() : owners);
        Map<Long, List<ProjectNodeDevelopmentStoryDO>> storiesByTopic = stories.stream()
                .collect(Collectors.groupingBy(ProjectNodeDevelopmentStoryDO::getTopicId));
        NodeDevelopmentControlDTO dto = new NodeDevelopmentControlDTO();
        dto.setProjectId(node.getProjectId());
        dto.setNodeId(node.getId());
        dto.setVersion(baseline == null ? null : baseline.getVersion());
        dto.setCurrentIteration(baseline == null ? null : baseline.getCurrentIteration());
        dto.setCanEdit(!NodeStatus.isReadOnly(node.getStatus()));
        dto.setUpdatedAt(baseline == null ? null : baseline.getUpdatedAt());
        List<NodeDevelopmentTopicDTO> topicDTOs = topics.stream()
                .map(topic -> toTopicDTO(topic, storiesByTopic.getOrDefault(topic.getId(), List.of()), userMap, iterationPlanNamesById))
                .collect(Collectors.toList());
        dto.setTopics(topicDTOs);
        dto.setSummary(toSummary(topicDTOs));
        return dto;
    }

    private NodeDevelopmentTopicDTO toTopicDTO(ProjectNodeDevelopmentTopicDO topic,
                                                List<ProjectNodeDevelopmentStoryDO> stories,
                                                Map<Long, UserDO> userMap,
                                                Map<Long, String> iterationPlanNamesById) {
        NodeDevelopmentTopicDTO dto = new NodeDevelopmentTopicDTO();
        dto.setId(topic.getId());
        dto.setTitle(topic.getTitle());
        dto.setOwnerId(topic.getOwnerId());
        dto.setOwnerName(Convertors.userDisplayName(userMap.get(topic.getOwnerId())));
        dto.setLatestBuildVersion(topic.getLatestBuildVersion());
        dto.setTestStatus(topic.getTestStatus());
        dto.setSort(topic.getSort());
        List<NodeDevelopmentStoryDTO> storyDTOs = stories.stream()
                .map(story -> toStoryDTO(story, userMap.get(story.getOwnerId()), iterationPlanNamesById))
                .collect(Collectors.toList());
        dto.setStories(storyDTOs);
        dto.setProgress(averageProgress(storyDTOs));
        dto.setStatus(deriveStatus(storyDTOs));
        dto.setBlocker(storyDTOs.stream().filter(story -> "BLOCKED".equals(story.getStatus()))
                .map(NodeDevelopmentStoryDTO::getBlocker).filter(value -> value != null && !value.isBlank()).findFirst().orElse(null));
        return dto;
    }

    private NodeDevelopmentStoryDTO toStoryDTO(ProjectNodeDevelopmentStoryDO story,
                                                UserDO owner,
                                                Map<Long, String> iterationPlanNamesById) {
        NodeDevelopmentStoryDTO dto = new NodeDevelopmentStoryDTO();
        dto.setId(story.getId());
        dto.setTitle(story.getTitle());
        dto.setOwnerId(story.getOwnerId());
        dto.setOwnerName(Convertors.userDisplayName(owner));
        dto.setIterationPlanId(story.getIterationPlanId());
        dto.setIterationPlanName(iterationPlanNamesById.get(story.getIterationPlanId()));
        dto.setStatus(story.getStatus());
        dto.setProgress(story.getProgress());
        dto.setStoryPoints(story.getStoryPoints());
        dto.setStartDate(story.getStartDate());
        dto.setDueDate(story.getDueDate());
        dto.setBlocker(story.getBlocker());
        dto.setSort(story.getSort());
        return dto;
    }

    private NodeDevelopmentSummaryDTO toSummary(List<NodeDevelopmentTopicDTO> topics) {
        List<NodeDevelopmentStoryDTO> stories = topics.stream().flatMap(topic -> topic.getStories().stream()).collect(Collectors.toList());
        NodeDevelopmentSummaryDTO summary = new NodeDevelopmentSummaryDTO();
        summary.setTopicCount(topics.size());
        summary.setStoryCount(stories.size());
        summary.setCompletedStoryCount((int) stories.stream().filter(story -> "DONE".equals(story.getStatus())).count());
        summary.setBlockedStoryCount((int) stories.stream().filter(story -> "BLOCKED".equals(story.getStatus())).count());
        summary.setProgress(averageProgress(stories));
        return summary;
    }

    private int averageProgress(List<NodeDevelopmentStoryDTO> stories) {
        return stories.isEmpty() ? 0 : (int) Math.round(stories.stream().mapToInt(this::effectiveProgress).average().orElse(0));
    }

    private int effectiveProgress(NodeDevelopmentStoryDTO story) {
        return effectiveProgress(story.getStatus(), story.getProgress());
    }

    private int effectiveProgress(String status, Integer progress) {
        if ("DONE".equals(status)) return 100;
        if ("NOT_STARTED".equals(status)) return 0;
        return progress == null ? 0 : Math.max(0, Math.min(100, progress));
    }

    private String deriveStatus(List<NodeDevelopmentStoryDTO> stories) {
        if (stories.isEmpty()) return "NOT_STARTED";
        if (stories.stream().allMatch(story -> "DONE".equals(story.getStatus()))) return "DONE";
        if (stories.stream().anyMatch(story -> !"NOT_STARTED".equals(story.getStatus()))) return "IN_PROGRESS";
        return "NOT_STARTED";
    }

    private ProjectNodeDevelopmentBaselineDO findBaseline(Long projectId, Long nodeId) {
        return baselineMapper.selectOne(new LambdaQueryWrapper<ProjectNodeDevelopmentBaselineDO>()
                .eq(ProjectNodeDevelopmentBaselineDO::getProjectId, projectId)
                .eq(ProjectNodeDevelopmentBaselineDO::getNodeId, nodeId));
    }

    private ProjectNodeDevelopmentBaselineDO newBaseline(Long projectId, Long nodeId) {
        ProjectNodeDevelopmentBaselineDO baseline = new ProjectNodeDevelopmentBaselineDO();
        baseline.setProjectId(projectId);
        baseline.setNodeId(nodeId);
        baseline.setVersion(0);
        baseline.setCreatedBy(UserContext.userIdOrNull());
        return baseline;
    }

    private ProjectNodeDO requireDevelopNode(ProjectNodeDO node) {
        permissionService.requireNodeComponent(node, WorkflowComponentKey.DEVELOPMENT_CONTROL,
                "仅配置了开发控制组件的节点支持开发工作台");
        return node;
    }

    private String normalize(String value, String fallback) {
        String text = trim(value);
        return text == null ? fallback : text.toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        String text = value == null ? null : value.trim();
        return text == null || text.isEmpty() ? null : text;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
