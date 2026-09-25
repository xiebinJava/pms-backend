package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
import com.brad.pms.common.enums.ProjectStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.page.PageResult;
import com.brad.pms.dto.request.DevelopmentTopicProjectQry;
import com.brad.pms.dto.request.DevelopmentTopicUpdateCmd;
import com.brad.pms.dto.response.DevelopmentTopicProjectOptionDTO;
import com.brad.pms.entity.DevelopmentItemTaskDO;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.mapper.DevelopmentItemTaskMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowMapper;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.security.UserContext;
import com.brad.pms.workflow.DevelopmentItemType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DevelopmentTopicManagementService {

    private final ProjectMapper projectMapper;
    private final ProjectNodeMapper nodeMapper;
    private final ProjectNodeDevelopmentTopicMapper topicMapper;
    private final ProjectNodeDevelopmentStoryMapper storyMapper;
    private final DevelopmentItemWorkflowMapper workflowMapper;
    private final DevelopmentItemTaskMapper taskMapper;
    private final ProjectService projectService;
    private final ProjectPermissionService permissionService;
    private final WorkflowTemplateService workflowTemplateService;
    private final DevelopmentItemWorkflowService developmentItemWorkflowService;
    private final OperationLogService operationLogService;
    private final UserService userService;
    private final ProjectMemberAssignmentService projectMemberAssignmentService;

    public ProjectNodeDevelopmentTopicDO requireReadableTopic(Long id) {
        ProjectNodeDevelopmentTopicDO topic = requireTopic(id, false);
        if (topic.getProjectId() != null) permissionService.requireProjectReadable(topic.getProjectId());
        return topic;
    }

    public ProjectNodeDevelopmentTopicDO requireWritableTopic(Long id) {
        ProjectNodeDevelopmentTopicDO topic = requireTopic(id, false);
        if (topic.getProjectId() != null) permissionService.requireProjectWritable(topic.getProjectId(), "维护专题下的故事");
        return topic;
    }

    @Transactional
    public Long create(DevelopmentTopicUpdateCmd cmd) {
        if (cmd == null) throw BusinessException.error("专题信息不能为空");
        String title = trimToNull(cmd.getTitle());
        if (title == null) throw BusinessException.error("专题名称不能为空");
        if (cmd.getOwnerId() != null) userService.requireActiveUser(cmd.getOwnerId());
        String topicNodeKey = cmd.getTemplateVersionId() == null
                ? workflowTemplateService.resolveTopicSourceProjectNodeKey()
                : resolveSelectedTopicNodeKey(cmd.getTemplateVersionId());
        ProjectDO project = cmd.getProjectId() == null ? null : requireActiveManageableTarget(cmd.getProjectId());
        ProjectNodeDO hostNode = project == null ? null : requireTopicSourceNode(project.getId(), topicNodeKey);

        LambdaQueryWrapper<ProjectNodeDevelopmentTopicDO> previousQuery = new LambdaQueryWrapper<>();
        if (project != null) {
            previousQuery.eq(ProjectNodeDevelopmentTopicDO::getProjectId, project.getId())
                    .eq(ProjectNodeDevelopmentTopicDO::getNodeId, hostNode.getId());
        } else {
            previousQuery.isNull(ProjectNodeDevelopmentTopicDO::getProjectId)
                    .isNull(ProjectNodeDevelopmentTopicDO::getNodeId);
        }
        ProjectNodeDevelopmentTopicDO previous = topicMapper.selectOne(previousQuery
                .eq(ProjectNodeDevelopmentTopicDO::getDeleted, false)
                .orderByDesc(ProjectNodeDevelopmentTopicDO::getSort)
                .orderByDesc(ProjectNodeDevelopmentTopicDO::getId)
                .last("LIMIT 1"));
        ProjectNodeDevelopmentTopicDO topic = new ProjectNodeDevelopmentTopicDO();
        topic.setProjectId(project == null ? null : project.getId());
        topic.setNodeId(hostNode == null ? null : hostNode.getId());
        topic.setTitle(title);
        topic.setOwnerId(cmd.getOwnerId());
        topic.setMilestoneId(null);
        topic.setTestStatus("NOT_STARTED");
        topic.setSort(previous == null || previous.getSort() == null ? 0 : previous.getSort() + 1);
        topic.setCreatedBy(UserContext.userIdOrNull());
        topic.setDeleted(false);
        if (topicMapper.insert(topic) != 1 || topic.getId() == null) {
            throw BusinessException.conflict("专题创建失败，请重试");
        }

        DevelopmentItemWorkflowDO topicWorkflow = cmd.getTemplateVersionId() == null
                ? developmentItemWorkflowService.createIfDefaultExists(
                DevelopmentItemType.TOPIC, topic.getId(), topic.getProjectId(), topic.getNodeId())
                : developmentItemWorkflowService.createWithTemplate(
                DevelopmentItemType.TOPIC, topic.getId(), topic.getProjectId(), topic.getNodeId(),
                cmd.getTemplateVersionId());
        if (topicWorkflow == null) {
            throw BusinessException.error("请先发布并设置专题流程模板为默认流程");
        }
        if (project != null && topic.getOwnerId() != null) {
            projectMemberAssignmentService.replaceAssignment(project.getId(), DevelopmentItemType.TOPIC, topic.getId(),
                    com.brad.pms.common.enums.DevelopmentAssignmentType.TOPIC_OWNER, topic.getId(), topic.getOwnerId());
        }
        operationLogService.record(AuditEvent.success(AuditAction.DEVELOPMENT_TOPIC_CREATED.name(),
                AuditResourceType.DEVELOPMENT_TOPIC.name(), topic.getId(), topic.getProjectId(), null,
                null, snapshot(topic)));
        return topic.getId();
    }

    @Transactional
    public void update(Long id, DevelopmentTopicUpdateCmd cmd) {
        if (id == null || cmd == null) throw BusinessException.error("专题信息不能为空");
        String title = trimToNull(cmd.getTitle());
        if (title == null) throw BusinessException.error("专题名称不能为空");
        ProjectNodeDevelopmentTopicDO topic = topicMapper.selectByIdForUpdate(id);
        if (topic == null || Boolean.TRUE.equals(topic.getDeleted())) throw BusinessException.notFound("专题不存在");
        if (cmd.getOwnerId() != null) userService.requireActiveUser(cmd.getOwnerId());
        ProjectDO sourceProject = requireManageableProjectIncludingDeleted(topic.getProjectId());
        boolean rebound = !Objects.equals(topic.getProjectId(), cmd.getProjectId());
        DevelopmentItemWorkflowDO topicWorkflowSnapshot = workflowMapper.selectByItem(
                DevelopmentItemType.TOPIC.name(), topic.getId());
        // Existing topics created before workflow snapshots were introduced do not have a
        // workflow row. Keep their rebind behavior compatible with the current default;
        // once a snapshot exists, always use its persisted mount key so later template
        // changes cannot move the topic to a different project node unexpectedly.
        String topicMountNodeKey = topicWorkflowSnapshot == null
                ? workflowTemplateService.resolveTopicSourceProjectNodeKey()
                : topicWorkflowSnapshot.getProjectMountNodeKey();
        ProjectDO targetProject = cmd.getProjectId() == null ? null
                : (rebound ? requireActiveManageableTarget(cmd.getProjectId()) : sourceProject);
        ProjectNodeDO targetNode = targetProject == null ? null
                : (rebound ? requireTopicSourceNode(targetProject.getId(), topicMountNodeKey)
                : nodeMapper.selectById(topic.getNodeId()));
        if (targetProject != null && (targetNode == null || !Objects.equals(targetNode.getProjectId(), targetProject.getId()))) {
            throw BusinessException.notFound("专题关联节点不存在");
        }

        List<ProjectNodeDevelopmentStoryDO> stories = rebound ? lockStories(topic.getId()) : List.of();
        List<DevelopmentItemWorkflowDO> workflows = rebound ? lockWorkflows(topic, stories) : List.of();
        lockWorkflowTasks(workflows);
        Long sourceProjectId = topic.getProjectId();
        Long sourceOwnerId = topic.getOwnerId();
        Map<Long, Long> sourceStoryProjects = stories.stream()
                .collect(Collectors.toMap(ProjectNodeDevelopmentStoryDO::getId,
                        ProjectNodeDevelopmentStoryDO::getProjectId, (left, right) -> left));

        TopicSnapshot before = snapshot(topic);
        topic.setTitle(title);
        topic.setOwnerId(cmd.getOwnerId());
        if (rebound) {
            topic.setProjectId(targetProject == null ? null : targetProject.getId());
            topic.setNodeId(targetNode == null ? null : targetNode.getId());
            topic.setMilestoneId(null);
        }
        if (topicMapper.updateById(topic) != 1) throw BusinessException.conflict("专题已被其他人修改，请刷新后重试");

        if (rebound) {
            for (ProjectNodeDevelopmentStoryDO story : stories) {
                story.setProjectId(targetProject == null ? null : targetProject.getId());
                story.setNodeId(targetNode == null ? null : targetNode.getId());
                story.setIterationPlanId(null);
                if (storyMapper.updateById(story) != 1) {
                    throw BusinessException.conflict("专题下的故事已被其他人修改，请刷新后重试");
                }
            }
            for (DevelopmentItemWorkflowDO workflow : workflows) {
                workflow.setProjectId(targetProject == null ? null : targetProject.getId());
                workflow.setSourceNodeId(targetNode == null ? null : targetNode.getId());
                if (workflowMapper.updateById(workflow) != 1) {
                    throw BusinessException.conflict("专题或故事流程已被其他人修改，请刷新后重试");
                }
            }
            projectMemberAssignmentService.synchronizeItemAssignments(sourceProjectId, targetProject == null ? null : targetProject.getId(),
                    DevelopmentItemType.TOPIC, topic.getId());
            for (ProjectNodeDevelopmentStoryDO story : stories) {
                projectMemberAssignmentService.synchronizeItemAssignments(
                        sourceStoryProjects.get(story.getId()), targetProject == null ? null : targetProject.getId(),
                        DevelopmentItemType.STORY, story.getId());
            }
        } else if (topic.getProjectId() != null && !Objects.equals(sourceOwnerId, cmd.getOwnerId())) {
            projectMemberAssignmentService.replaceAssignment(topic.getProjectId(), DevelopmentItemType.TOPIC, topic.getId(),
                    com.brad.pms.common.enums.DevelopmentAssignmentType.TOPIC_OWNER, topic.getId(), cmd.getOwnerId());
        }

        operationLogService.record(AuditEvent.success(
                (rebound ? AuditAction.DEVELOPMENT_TOPIC_PROJECT_REBOUND : AuditAction.DEVELOPMENT_TOPIC_UPDATED).name(),
                AuditResourceType.DEVELOPMENT_TOPIC.name(), topic.getId(), topic.getProjectId(), null,
                before, snapshot(topic)));
    }

    @Transactional
    public void softDelete(Long id) {
        ProjectNodeDevelopmentTopicDO topic = requireTopicForUpdate(id, false);
        ProjectDO project = requireManageableProjectIncludingDeleted(topic.getProjectId());
        TopicSnapshot before = snapshot(topic);
        topic.setDeleted(true);
        if (topicMapper.updateById(topic) != 1) throw BusinessException.conflict("专题已被其他人修改，请刷新后重试");
        projectMemberAssignmentService.synchronizeItemAssignments(topic.getProjectId(), null,
                DevelopmentItemType.TOPIC, topic.getId());
        for (ProjectNodeDevelopmentStoryDO story : lockStories(topic.getId())) {
            projectMemberAssignmentService.synchronizeItemAssignments(story.getProjectId(), null,
                    DevelopmentItemType.STORY, story.getId());
        }
        operationLogService.record(AuditEvent.success(AuditAction.DEVELOPMENT_TOPIC_DELETED.name(),
                AuditResourceType.DEVELOPMENT_TOPIC.name(), topic.getId(), project == null ? null : project.getId(), null,
                before, snapshot(topic)));
    }

    @Transactional
    public void restore(Long id) {
        ProjectNodeDevelopmentTopicDO topic = requireTopicForUpdate(id, true);
        ProjectDO project = requireManageableProjectIncludingDeleted(topic.getProjectId());
        TopicSnapshot before = snapshot(topic);
        topic.setDeleted(false);
        if (topicMapper.updateById(topic) != 1) throw BusinessException.conflict("专题已被其他人修改，请刷新后重试");
        projectMemberAssignmentService.synchronizeItemAssignments(topic.getProjectId(), topic.getProjectId(),
                DevelopmentItemType.TOPIC, topic.getId());
        for (ProjectNodeDevelopmentStoryDO story : lockStories(topic.getId())) {
            projectMemberAssignmentService.synchronizeItemAssignments(story.getProjectId(), topic.getProjectId(),
                    DevelopmentItemType.STORY, story.getId());
        }
        operationLogService.record(AuditEvent.success(AuditAction.DEVELOPMENT_TOPIC_RESTORED.name(),
                AuditResourceType.DEVELOPMENT_TOPIC.name(), topic.getId(), project == null ? null : project.getId(), null,
                before, snapshot(topic)));
    }

    public PageResult<DevelopmentTopicProjectOptionDTO> projectOptions(DevelopmentTopicProjectQry qry) {
        DevelopmentTopicProjectQry query = qry == null ? new DevelopmentTopicProjectQry() : qry;
        String topicNodeKey;
        if (query.getTemplateVersionId() == null) {
            if (workflowTemplateService.resolveDefaultForProcessType(DevelopmentItemType.TOPIC.processTypeCode()) == null) {
                return PageResult.of(0, query.getCurrPage(), query.getPageSize(), List.of());
            }
            topicNodeKey = workflowTemplateService.resolveTopicSourceProjectNodeKey();
        } else {
            topicNodeKey = resolveSelectedTopicNodeKey(query.getTemplateVersionId());
        }
        List<Long> readableIds = safeList(projectService.listReadableIds()).stream()
                .filter(Objects::nonNull).distinct().toList();
        if (readableIds.isEmpty()) return PageResult.of(0, query.getCurrPage(), query.getPageSize(), List.of());

        if (!StringUtils.hasText(topicNodeKey)) return PageResult.of(0, query.getCurrPage(), query.getPageSize(), List.of());
        Map<Long, ProjectDO> projectsById = projectMapper.selectBatchIds(readableIds).stream()
                .filter(project -> ProjectStatus.isOpen(project.getStatus()))
                .filter(permissionService::canManageProject)
                .collect(Collectors.toMap(ProjectDO::getId, Function.identity(), (left, right) -> left, HashMap::new));
        if (projectsById.isEmpty()) return PageResult.of(0, query.getCurrPage(), query.getPageSize(), List.of());

        List<ProjectNodeDO> nodes = nodeMapper.selectList(new LambdaQueryWrapper<ProjectNodeDO>()
                .in(ProjectNodeDO::getProjectId, projectsById.keySet())
                .eq(ProjectNodeDO::getNodeKey, topicNodeKey)
                .orderByAsc(ProjectNodeDO::getProjectId)
                .orderByAsc(ProjectNodeDO::getSort)
                .orderByAsc(ProjectNodeDO::getId));
        String keyword = trimToNull(query.getKeyword());
        List<DevelopmentTopicProjectOptionDTO> options = safeList(nodes).stream()
                .filter(node -> projectsById.containsKey(node.getProjectId()))
                .map(node -> {
                    ProjectDO project = projectsById.get(node.getProjectId());
                    return new DevelopmentTopicProjectOptionDTO(project.getId(), project.getCode(), project.getName(),
                            node.getNodeKey(), node.getName());
                })
                .filter(option -> !StringUtils.hasText(keyword)
                        || contains(option.projectName(), keyword) || contains(option.projectCode(), keyword)
                        || contains(option.nodeName(), keyword))
                .sorted(Comparator.comparing(DevelopmentTopicProjectOptionDTO::projectName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(DevelopmentTopicProjectOptionDTO::projectId,
                                Comparator.nullsLast(Long::compareTo)))
                .toList();
        return page(options, query);
    }

    private ProjectNodeDevelopmentTopicDO requireTopicForUpdate(Long id, boolean deleted) {
        if (id == null) throw BusinessException.notFound("专题不存在");
        ProjectNodeDevelopmentTopicDO topic = topicMapper.selectByIdForUpdate(id);
        if (topic == null || !Objects.equals(Boolean.TRUE.equals(topic.getDeleted()), deleted)) {
            throw BusinessException.notFound("专题不存在");
        }
        return topic;
    }

    private ProjectNodeDevelopmentTopicDO requireTopic(Long id, boolean deleted) {
        if (id == null) throw BusinessException.notFound("专题不存在");
        ProjectNodeDevelopmentTopicDO topic = topicMapper.selectById(id);
        if (topic == null || !Objects.equals(Boolean.TRUE.equals(topic.getDeleted()), deleted)) {
            throw BusinessException.notFound("专题不存在");
        }
        return topic;
    }

    private ProjectDO requireManageableProjectIncludingDeleted(Long projectId) {
        if (projectId == null) return null;
        ProjectDO project = projectMapper.selectIncludingDeleted(projectId);
        if (project == null) throw BusinessException.notFound("项目不存在");
        if (!permissionService.canManageProject(project)) {
            throw BusinessException.forbidden("当前用户没有管理此项目的权限");
        }
        return project;
    }

    private ProjectDO requireActiveManageableTarget(Long projectId) {
        ProjectDO project = projectMapper.selectById(projectId);
        if (project == null || !ProjectStatus.isOpen(project.getStatus())) {
            throw BusinessException.error("只能绑定到进行中的项目");
        }
        if (!permissionService.canManageProject(project)) {
            throw BusinessException.forbidden("当前用户没有管理目标项目的权限");
        }
        return project;
    }

    private ProjectNodeDO requireTopicSourceNode(Long projectId) {
        return requireTopicSourceNode(projectId, workflowTemplateService.resolveTopicSourceProjectNodeKey());
    }

    private String resolveSelectedTopicNodeKey(Long templateVersionId) {
        WorkflowTemplateService.WorkflowTemplateBinding binding = workflowTemplateService.resolveForProcessType(
                DevelopmentItemType.TOPIC.processTypeCode(), templateVersionId);
        if (binding == null) throw BusinessException.error("请先发布并选择专题流程模板");
        return workflowTemplateService.resolveTopicSourceProjectNodeKeyForRuntime(binding.version().getId());
    }

    private ProjectNodeDO requireTopicSourceNode(Long projectId, String nodeKey) {
        if (!StringUtils.hasText(nodeKey)) throw BusinessException.error("专题流程未配置项目节点挂载点");
        ProjectNodeDO node = nodeMapper.selectOne(new LambdaQueryWrapper<ProjectNodeDO>()
                .eq(ProjectNodeDO::getProjectId, projectId)
                .eq(ProjectNodeDO::getNodeKey, nodeKey)
                .last("LIMIT 1"));
        if (node == null) throw BusinessException.error("目标项目不包含当前专题流程绑定的项目节点");
        return node;
    }

    private List<ProjectNodeDevelopmentStoryDO> lockStories(Long topicId) {
        List<ProjectNodeDevelopmentStoryDO> rows = safeList(storyMapper.selectList(new LambdaQueryWrapper<ProjectNodeDevelopmentStoryDO>()
                .eq(ProjectNodeDevelopmentStoryDO::getTopicId, topicId)
                .orderByAsc(ProjectNodeDevelopmentStoryDO::getId)));
        List<ProjectNodeDevelopmentStoryDO> locked = new ArrayList<>(rows.size());
        for (ProjectNodeDevelopmentStoryDO row : rows) {
            if (row.getId() == null) continue;
            ProjectNodeDevelopmentStoryDO current = storyMapper.selectByIdForUpdate(row.getId());
            if (current == null || !Objects.equals(current.getTopicId(), topicId)) {
                throw BusinessException.conflict("专题下的故事已被其他人修改，请刷新后重试");
            }
            locked.add(current);
        }
        return locked;
    }

    private List<DevelopmentItemWorkflowDO> lockWorkflows(ProjectNodeDevelopmentTopicDO topic,
                                                           List<ProjectNodeDevelopmentStoryDO> stories) {
        List<DevelopmentItemWorkflowDO> workflows = new ArrayList<>();
        DevelopmentItemWorkflowDO topicWorkflow = workflowMapper.selectForUpdate("TOPIC", topic.getId());
        if (topicWorkflow != null) workflows.add(topicWorkflow);
        for (ProjectNodeDevelopmentStoryDO story : stories) {
            DevelopmentItemWorkflowDO storyWorkflow = workflowMapper.selectForUpdate("STORY", story.getId());
            if (storyWorkflow != null) workflows.add(storyWorkflow);
        }
        return workflows;
    }

    private void lockWorkflowTasks(List<DevelopmentItemWorkflowDO> workflows) {
        List<Long> workflowIds = workflows.stream().map(DevelopmentItemWorkflowDO::getId)
                .filter(Objects::nonNull).sorted().toList();
        if (!workflowIds.isEmpty()) taskMapper.selectByWorkflowIdsForUpdate(workflowIds);
    }

    private TopicSnapshot snapshot(ProjectNodeDevelopmentTopicDO topic) {
        return new TopicSnapshot(topic.getTitle(), topic.getOwnerId(), topic.getProjectId(), topic.getNodeId(),
                topic.getMilestoneId(), topic.getDeleted());
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT)
                .contains(keyword.toLowerCase(java.util.Locale.ROOT));
    }

    private <T> PageResult<T> page(List<T> records, DevelopmentTopicProjectQry query) {
        long page = query.getCurrPage();
        long size = query.getPageSize();
        int from = (int) Math.min((page - 1) * size, records.size());
        int to = (int) Math.min(from + size, records.size());
        return PageResult.of(records.size(), page, size, records.subList(from, to));
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record TopicSnapshot(String title, Long ownerId, Long projectId, Long nodeId,
                                 Long milestoneId, Boolean deleted) { }
}
