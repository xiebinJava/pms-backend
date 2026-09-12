package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.common.enums.NodeStatus;
import com.brad.pms.common.enums.TaskStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
import com.brad.pms.dto.response.ProjectNodeDTO;
import com.brad.pms.dto.request.NodeScheduleUpdateCmd;
import com.brad.pms.dto.request.NodeOwnerUpdateCmd;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectLifecycleLogDO;
import com.brad.pms.entity.ProjectMemberDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.workflow.BuiltInWorkflowTemplate;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import com.brad.pms.workflow.WorkflowTemplateDefinition;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectMemberMapper;
import com.brad.pms.mapper.ProjectLifecycleLogMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.mapper.ProjectFollowerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * 项目管理节点服务：节点初始化、列表、完成流转（完成当前节点自动解锁下一个）
 */
@Service
@RequiredArgsConstructor
public class NodeService {

    private final ProjectNodeMapper nodeMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final UserService userService;
    private final ProjectPermissionService permissionService;
    private final ProjectLifecycleLogMapper lifecycleLogMapper;
    private final ProjectTaskMapper taskMapper;
    private final OperationLogService operationLogService;
    private final NodeRequirementScopeService requirementScopeService;
    private final NodeSolutionDesignService solutionDesignService;
    private final NodePlanResourceRiskService planResourceRiskService;
    private final NodeAcceptanceService acceptanceService;
    private final NodeDevelopmentControlService developmentControlService;
    private final NodeReleaseService releaseService;
    private final NodeValueReviewService valueReviewService;
    private final MemberService memberService;
    private NotificationService notificationService;
    private WorkflowTemplateService workflowTemplateService;
    private NodeCustomFieldService nodeCustomFieldService;
    private ProjectFollowerMapper followerMapper;

    @Autowired
    public void setNotificationService(@Lazy NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Autowired
    public void setWorkflowTemplateService(WorkflowTemplateService workflowTemplateService) {
        this.workflowTemplateService = workflowTemplateService;
    }

    @Autowired
    public void setNodeCustomFieldService(NodeCustomFieldService nodeCustomFieldService) {
        this.nodeCustomFieldService = nodeCustomFieldService;
    }

    @Autowired
    public void setFollowerMapper(ProjectFollowerMapper followerMapper) {
        this.followerMapper = followerMapper;
    }

    @Transactional
    public void initDefault(Long projectId) {
        initDefault(projectId, null);
    }

    @Transactional
    public void initDefault(Long projectId, Long creatorId) {
        initFromDefinition(projectId, creatorId, BuiltInWorkflowTemplate.compatibilityDefinition());
    }

    @Transactional
    public void initFromDefinition(Long projectId, Long creatorId, WorkflowTemplateDefinition definition) {
        WorkflowTemplateDefinition validated = com.brad.pms.workflow.WorkflowTemplateDefinitionValidator.validate(definition);
        for (int i = 0; i < validated.nodes().size(); i++) {
            WorkflowNodeDefinition def = validated.nodes().get(i);
            ProjectNodeDO node = new ProjectNodeDO();
            node.setProjectId(projectId);
            node.setNodeKey(def.key());
            node.setName(def.name());
            node.setDescription(def.description());
            node.setDeliverable(def.deliverable());
            node.setRoles(def.roles());
            node.setStatus(i == 0 ? 1 : 0);
            node.setSort(i);
            if (i == 0 && creatorId != null) {
                node.setOwnerId(creatorId);
            }
            nodeMapper.insert(node);
        }
    }

    static Long defaultNodeOwnerId(boolean firstNode, Long creatorId, Long projectManagerId) {
        return firstNode ? creatorId : projectManagerId;
    }

    static boolean shouldAssignDefaultOwner(Long currentOwnerId, Long previousManagerId, Long nextOwnerId) {
        if (nextOwnerId == null || java.util.Objects.equals(currentOwnerId, nextOwnerId)) return false;
        return currentOwnerId == null || java.util.Objects.equals(currentOwnerId, previousManagerId);
    }

    /**
     * 首节点默认项目创建人；后续节点默认项目经理。
     * 只填充空负责人，或跟着上一任项目经理走；已完成/已终止节点和人工指定的负责人不覆盖。
     */
    @Transactional
    public void applyDefaultOwners(ProjectDO project, Long previousManagerId) {
        if (project == null || project.getId() == null) return;
        Long creatorId = project.getCreatedBy() != null ? project.getCreatedBy() : project.getOwnerId();
        Long managerId = project.getProjectManagerId();
        List<ProjectNodeDO> nodes = nodeMapper.selectList(new LambdaQueryWrapper<ProjectNodeDO>()
                .eq(ProjectNodeDO::getProjectId, project.getId())
                .orderByAsc(ProjectNodeDO::getSort)
                .orderByAsc(ProjectNodeDO::getId));
        for (int i = 0; i < nodes.size(); i++) {
            ProjectNodeDO node = nodes.get(i);
            if (NodeStatus.isReadOnly(node.getStatus())) continue;
            boolean firstNode = i == 0;
            Long nextOwnerId = defaultNodeOwnerId(firstNode, creatorId, managerId);
            if (!shouldAssignDefaultOwner(node.getOwnerId(), firstNode ? null : previousManagerId, nextOwnerId)) {
                continue;
            }
            Long previousOwnerId = node.getOwnerId();
            node.setOwnerId(nextOwnerId);
            if (nodeMapper.updateById(node) != 1) {
                throw BusinessException.conflict("节点已被其他人修改，请刷新后重试");
            }
            memberService.ensureMember(project.getId(), nextOwnerId);
            if (!java.util.Objects.equals(previousOwnerId, nextOwnerId)) {
                operationLogService.record(AuditEvent.success(
                        AuditAction.NODE_OWNER_CHANGED.name(), AuditResourceType.PROJECT_NODE.name(), node.getId(), project.getId(),
                        null, personSnapshot(previousOwnerId), personSnapshot(nextOwnerId)));
            }
        }
    }

    public List<ProjectNodeDTO> list(Long projectId) {
        ProjectDO project = permissionService.requireProject(projectId);
        applyDefaultOwners(project, null);
        List<ProjectNodeDO> nodes = nodeMapper.selectList(new LambdaQueryWrapper<ProjectNodeDO>()
                        .eq(ProjectNodeDO::getProjectId, projectId)
                        .orderByAsc(ProjectNodeDO::getSort));
        java.util.Map<Long, UserDO> owners = userService.listByIds(nodes.stream()
                        .map(ProjectNodeDO::getOwnerId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(UserDO::getId, user -> user));
        return nodes.stream().map(node -> toDTO(node, owners.get(node.getOwnerId()), project)).collect(Collectors.toList());
    }

    @Transactional
    public ProjectNodeDTO updateOwner(Long projectId, Long nodeId, NodeOwnerUpdateCmd cmd) {
        ProjectDO project = permissionService.requireProjectManageable(projectId, "分配节点负责人");
        ProjectNodeDO node = permissionService.requireNode(projectId, nodeId);
        requireCurrentVersion(node.getVersion(), cmd.getVersion());
        Long ownerId = cmd.getOwnerId();
        if (NodeStatus.isReadOnly(node.getStatus())) {
            throw BusinessException.forbidden("节点已锁定，回滚后才可以分配节点负责人");
        }
        if (ownerId != null) {
            memberService.ensureMember(projectId, ownerId);
        }
        Long previousOwnerId = node.getOwnerId();
        node.setOwnerId(ownerId);
        if (nodeMapper.updateById(node) != 1) {
            throw BusinessException.conflict("节点已被其他人修改，请刷新后重试");
        }
        if (!java.util.Objects.equals(previousOwnerId, ownerId)) {
            operationLogService.record(AuditEvent.success(
                    AuditAction.NODE_OWNER_CHANGED.name(), AuditResourceType.PROJECT_NODE.name(), nodeId, projectId,
                    null, personSnapshot(previousOwnerId), personSnapshot(ownerId)));
        }
        UserDO owner = ownerId == null ? null : userService.listByIds(java.util.Collections.singletonList(ownerId))
                .stream().findFirst().orElse(null);
        return toDTO(node, owner, project);
    }

    @Transactional
    public ProjectNodeDTO updateSchedule(Long projectId, Long nodeId, NodeScheduleUpdateCmd cmd) {
        ProjectDO project = permissionService.requireProjectReadable(projectId);
        ProjectNodeDO node = permissionService.requireManageableNode(projectId, nodeId, "编辑节点排期");
        requireCurrentVersion(node.getVersion(), cmd.getVersion());
        if (NodeStatus.isReadOnly(node.getStatus())) {
            throw BusinessException.forbidden("节点已锁定，回滚后才可以编辑节点排期");
        }
        if (cmd.getStartDate() != null && cmd.getEndDate() != null
                && cmd.getStartDate().isAfter(cmd.getEndDate())) {
            throw BusinessException.error("节点排期开始日期不能晚于结束日期");
        }
        java.time.LocalDate previousStartDate = node.getStartDate();
        java.time.LocalDate previousEndDate = node.getEndDate();
        node.setStartDate(cmd.getStartDate());
        node.setEndDate(cmd.getEndDate());
        if (nodeMapper.updateById(node) != 1) {
            throw BusinessException.conflict("节点已被其他人修改，请刷新后重试");
        }
        if (!java.util.Objects.equals(previousStartDate, node.getStartDate())
                || !java.util.Objects.equals(previousEndDate, node.getEndDate())) {
            operationLogService.record(AuditEvent.success(
                    AuditAction.NODE_SCHEDULE_CHANGED.name(), AuditResourceType.PROJECT_NODE.name(), nodeId, projectId,
                    null,
                    java.util.Map.of("startDate", String.valueOf(previousStartDate), "endDate", String.valueOf(previousEndDate)),
                    java.util.Map.of("startDate", String.valueOf(node.getStartDate()), "endDate", String.valueOf(node.getEndDate()))));
        }
        UserDO owner = node.getOwnerId() == null ? null : userService.listByIds(java.util.Collections.singletonList(node.getOwnerId()))
                .stream().findFirst().orElse(null);
        return toDTO(node, owner, project);
    }

    private void requireCurrentVersion(Integer currentVersion, Integer requestedVersion) {
        if (!java.util.Objects.equals(currentVersion, requestedVersion)) {
            throw BusinessException.conflict("节点已被其他人修改，请刷新后重试");
        }
    }

    /**
     * 完成当前节点：标记已完成，自动解锁下一个节点；全部完成时项目收尾。
     */
    @Transactional
    public List<ProjectNodeDTO> complete(Long projectId, Long nodeId) {
        ProjectDO project = permissionService.requireProject(projectId);
        ProjectNodeDO node = permissionService.requireCompletableNode(projectId, nodeId);
        WorkflowNodeDefinition definition = resolveNodeDefinition(project, node);
        if (definition != null && definition.projectBasicInfo()) validateKickoffProfile(projectId, node, definition);
        if (node.getOwnerId() == null) {
            throw BusinessException.error("请先分配节点负责人");
        }
        Long unfinished = taskMapper.selectCount(new LambdaQueryWrapper<ProjectTaskDO>()
                .eq(ProjectTaskDO::getProjectId, projectId)
                .eq(ProjectTaskDO::getNodeId, nodeId)
                .ne(ProjectTaskDO::getStatus, TaskStatus.DONE.getCode()));
        if (unfinished != null && unfinished > 0) {
            throw BusinessException.error("请先完成当前节点的未完成任务（" + unfinished + "）");
        }
        if (nodeCustomFieldService != null) nodeCustomFieldService.requireRequiredFields(projectId, nodeId, definition);
        validateAttachedComponents(projectId, nodeId, definition);
        node.setStatus(NodeStatus.COMPLETED.getCode());
        nodeMapper.updateById(node);
        operationLogService.record(AuditEvent.success(
                AuditAction.NODE_COMPLETED.name(), AuditResourceType.PROJECT_NODE.name(), nodeId, projectId,
                null, java.util.Map.of("status", NodeStatus.IN_PROGRESS.name()),
                java.util.Map.of("status", NodeStatus.COMPLETED.name())));

        ProjectNodeDO next = nodeMapper.selectOne(new LambdaQueryWrapper<ProjectNodeDO>()
                .eq(ProjectNodeDO::getProjectId, projectId)
                .eq(ProjectNodeDO::getStatus, NodeStatus.NOT_STARTED.getCode())
                .orderByAsc(ProjectNodeDO::getSort)
                .last("LIMIT 1"));
        if (next != null) {
            next.setStatus(NodeStatus.IN_PROGRESS.getCode());
            nodeMapper.updateById(next);
            refreshProgress(projectId);
        } else {
            if (project != null) {
                project.setStatus(2);
                project.setProgress(100);
                projectMapper.updateById(project);
            }
        }
        notificationService.notifyNodeCompleted(
                projectId,
                node.getId(),
                node.getName(),
                project == null ? null : project.getProjectManagerId(),
                node.getOwnerId(),
                next == null ? null : next.getOwnerId());
        return list(projectId);
    }

    private void validateKickoffProfile(Long projectId, ProjectNodeDO node, WorkflowNodeDefinition definition) {
        ProjectDO project = projectMapper.selectById(projectId);
        if (project == null) {
            throw BusinessException.error("项目不存在");
        }

        List<String> missing = new ArrayList<>();
        for (var field : definition.projectBasicInfoFields()) {
            if (!field.visible() || !field.required()) continue;
            switch (field.key()) {
                case "description" -> { if (project.getDescription() == null || project.getDescription().isBlank()) missing.add(field.label()); }
                case "priority" -> { if (project.getPriority() == null) missing.add(field.label()); }
                case "projectLevel" -> { if (project.getProjectLevel() == null) missing.add(field.label()); }
                case "schedule" -> { if (project.getStartDate() == null || project.getEndDate() == null) missing.add(field.label()); }
                case "businessLine" -> { if (project.getOrgUnitId() == null) missing.add(field.label()); }
                case "projectManager" -> { if (project.getProjectManagerId() == null) missing.add(field.label()); }
                case "projectMembers" -> {
                    long count = memberMapper.selectCount(new LambdaQueryWrapper<ProjectMemberDO>()
                            .eq(ProjectMemberDO::getProjectId, projectId));
                    if (count == 0) missing.add(field.label());
                }
                case "followers" -> {
                    Long count = followerMapper == null ? 0L : followerMapper.selectCount(new LambdaQueryWrapper<com.brad.pms.entity.ProjectFollowerDO>()
                            .eq(com.brad.pms.entity.ProjectFollowerDO::getProjectId, projectId));
                    if (count == null || count == 0) missing.add(field.label());
                }
                default -> missing.add(field.label());
            }
        }

        if (!missing.isEmpty()) {
            throw BusinessException.error("请先完善" + String.join("、", missing));
        }
    }

    /**
     * 回滚到指定节点：指定节点设为进行中，之前节点标记完成，之后节点恢复待开始。
     */
    @Transactional
    public List<ProjectNodeDTO> rollback(Long projectId, Long nodeId, String reason) {
        ProjectDO project = permissionService.requireProject(projectId);
        ProjectNodeDO target = permissionService.requireRollbackableNode(projectId, nodeId);
        List<ProjectNodeDO> nodes = nodeMapper.selectList(new LambdaQueryWrapper<ProjectNodeDO>()
                .eq(ProjectNodeDO::getProjectId, projectId)
                .orderByAsc(ProjectNodeDO::getSort));
        for (ProjectNodeDO node : nodes) {
            int nextStatus = node.getSort() < target.getSort() ? NodeStatus.COMPLETED.getCode()
                    : node.getId().equals(target.getId()) ? NodeStatus.IN_PROGRESS.getCode()
                    : NodeStatus.NOT_STARTED.getCode();
            if (!Integer.valueOf(nextStatus).equals(node.getStatus())) {
                node.setStatus(nextStatus);
                nodeMapper.updateById(node);
            }
        }

        if (project != null) {
            int fromStatus = project.getStatus() == null ? 1 : project.getStatus();
            project.setStatus(1);
            projectMapper.updateById(project);
            ProjectLifecycleLogDO log = new ProjectLifecycleLogDO();
            log.setProjectId(projectId);
            log.setAction("ROLLBACK_NODE");
            log.setReason(reason);
            log.setFromStatus(fromStatus);
            log.setToStatus(1);
            log.setOperatorId(com.brad.pms.security.UserContext.userId());
            lifecycleLogMapper.insert(log);
        }
        operationLogService.record(AuditEvent.success(
                AuditAction.NODE_ROLLED_BACK.name(), AuditResourceType.PROJECT_NODE.name(), nodeId, projectId,
                reason, java.util.Map.of("status", NodeStatus.COMPLETED.name()),
                java.util.Map.of("status", NodeStatus.IN_PROGRESS.name())));
        refreshProgress(projectId);
        notificationService.notifyNodeRolledBack(
                projectId,
                target.getId(),
                target.getName(),
                project == null ? null : project.getProjectManagerId(),
                target.getOwnerId(),
                reason);
        return list(projectId);
    }

    private void refreshProgress(Long projectId) {
        ProjectDO project = projectMapper.selectById(projectId);
        if (project == null || project.getStatus() == 2 || project.getStatus() == 3) {
            return;
        }
        Long total = nodeMapper.selectCount(new LambdaQueryWrapper<ProjectNodeDO>()
                .eq(ProjectNodeDO::getProjectId, projectId));
        Long done = nodeMapper.selectCount(new LambdaQueryWrapper<ProjectNodeDO>()
                .eq(ProjectNodeDO::getProjectId, projectId)
                .eq(ProjectNodeDO::getStatus, NodeStatus.COMPLETED.getCode()));
        int progress = total == null || total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
        project.setProgress(progress);
        projectMapper.updateById(project);
    }

    private java.util.Map<String, Object> personSnapshot(Long userId) {
        return java.util.Map.of("userId", userId == null ? "UNASSIGNED" : userId);
    }

    private ProjectNodeDTO toDTO(ProjectNodeDO node, UserDO owner, ProjectDO project) {
        ProjectNodeDTO dto = new ProjectNodeDTO();
        dto.setId(node.getId());
        dto.setVersion(node.getVersion());
        dto.setProjectId(node.getProjectId());
        dto.setNodeKey(node.getNodeKey());
        dto.setName(node.getName());
        dto.setDescription(node.getDescription());
        dto.setDeliverable(node.getDeliverable());
        dto.setRoles(node.getRoles());
        dto.setOwnerId(node.getOwnerId());
        dto.setOwnerName(com.brad.pms.convertor.Convertors.userDisplayName(owner));
        dto.setOwnerAvatar(owner == null ? null : owner.getAvatar());
        dto.setStatus(node.getStatus());
        dto.setSort(node.getSort());
        dto.setStartDate(node.getStartDate());
        dto.setEndDate(node.getEndDate());
        dto.setCreatedAt(node.getCreatedAt());
        dto.setPermissions(permissionService.nodePermissions(project, node));
        WorkflowNodeDefinition definition = resolveNodeDefinition(project, node);
        if (definition != null) {
            dto.setComponents(definition.components());
            dto.setFields(definition.fields());
            dto.setProjectBasicInfo(definition.projectBasicInfo());
            dto.setProjectBasicInfoFields(definition.projectBasicInfoFields());
        }
        if (nodeCustomFieldService != null && definition != null) {
            var values = nodeCustomFieldService.getValuesForNode(project.getId(), node.getId());
            dto.setFieldValues(values.getValues());
            dto.setFieldValueVersions(values.getVersions());
            dto.setFieldAttachments(values.getAttachments());
        }
        dto.setFixedBlocks(List.of("owner", "schedule", "task-board"));
        return dto;
    }

    private WorkflowNodeDefinition resolveNodeDefinition(ProjectDO project, ProjectNodeDO node) {
        if (project != null && project.getWorkflowTemplateVersionId() != null) {
            return workflowTemplateService == null ? null : workflowTemplateService.getNodeDefinition(
                    project.getWorkflowTemplateVersionId(), node.getNodeKey());
        }
        return BuiltInWorkflowTemplate.compatibilityDefinition().nodes().stream()
                .filter(definition -> definition.key().equals(node.getNodeKey())).findFirst().orElse(null);
    }

    private void validateAttachedComponents(Long projectId, Long nodeId, WorkflowNodeDefinition definition) {
        if (definition == null || definition.components() == null) {
            throw BusinessException.error("节点工作流配置缺失或无效，请联系管理员");
        }
        for (String component : definition.components()) {
            switch (component) {
                case "requirement-scope" -> requirementScopeService.requireConfirmed(projectId, nodeId);
                case "solution-design" -> solutionDesignService.requireConfirmed(projectId, nodeId);
                case "plan-resource-risk" -> planResourceRiskService.requireConfirmed(projectId, nodeId);
                case "business-acceptance" -> acceptanceService.requireConfirmed(projectId, nodeId);
                case "development-control" -> developmentControlService.requireCompleted(projectId, nodeId);
                case "release-handover" -> releaseService.requireCompleted(projectId, nodeId);
                case "value-review" -> valueReviewService.requireCompleted(projectId, nodeId);
                case "project-basic-info", "knowledge-standard" -> { }
                default -> throw BusinessException.error("节点包含无法识别的工作台组件: " + component);
            }
        }
    }
}
