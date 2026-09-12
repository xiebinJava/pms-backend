package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodeRequirementCmd;
import com.brad.pms.dto.request.NodeRequirementScopeUpdateCmd;
import com.brad.pms.dto.request.NodeScopeItemCmd;
import com.brad.pms.dto.response.NodeRequirementDTO;
import com.brad.pms.dto.response.NodeRequirementScopeDTO;
import com.brad.pms.dto.response.NodeScopeItemDTO;
import com.brad.pms.entity.ProjectNodeBaselineDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeRequirementDO;
import com.brad.pms.entity.ProjectNodeScopeItemDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.entity.ProjectTaskRequirementDO;
import com.brad.pms.mapper.ProjectNodeBaselineMapper;
import com.brad.pms.mapper.ProjectNodeRequirementMapper;
import com.brad.pms.mapper.ProjectNodeScopeItemMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.mapper.ProjectTaskRequirementMapper;
import com.brad.pms.security.UserContext;
import com.brad.pms.workflow.WorkflowComponentKey;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NodeRequirementScopeService {


    private final ProjectNodeBaselineMapper baselineMapper;
    private final ProjectNodeScopeItemMapper scopeItemMapper;
    private final ProjectNodeRequirementMapper requirementMapper;
    private final ProjectTaskMapper taskMapper;
    private final ProjectTaskRequirementMapper taskRequirementMapper;
    private final ProjectPermissionService permissionService;
    private final OperationLogService operationLogService;

    public NodeRequirementScopeDTO get(Long projectId, Long nodeId) {
        permissionService.requireProjectReadable(projectId);
        ProjectNodeDO node = requireRequirementNode(projectId, nodeId);
        return toDTO(node, findBaseline(projectId, nodeId));
    }

    @Transactional
    public NodeRequirementScopeDTO saveDraft(Long projectId, Long nodeId, NodeRequirementScopeUpdateCmd cmd) {
        ProjectNodeDO node = permissionService.requireManageableNode(projectId, nodeId, "保存需求范围基线");
        requireRequirementNode(node);
        validatePayload(cmd);

        ProjectNodeBaselineDO baseline = findBaseline(projectId, nodeId);
        List<ProjectNodeRequirementDO> existingRequirements = requirementMapper.selectList(new LambdaQueryWrapper<ProjectNodeRequirementDO>()
                .eq(ProjectNodeRequirementDO::getProjectId, projectId)
                .eq(ProjectNodeRequirementDO::getNodeId, nodeId));
        if (baseline == null) {
            baseline = newBaseline(projectId, nodeId);
            try {
                baselineMapper.insert(baseline);
            } catch (DuplicateKeyException ex) {
                throw BusinessException.conflict("需求范围基线已被其他人创建，请刷新后重试");
            }
        } else if (cmd.getVersion() == null || !Objects.equals(cmd.getVersion(), baseline.getVersion())) {
            throw BusinessException.conflict("需求范围基线已被其他人修改，请刷新后重试");
        }

        baseline.setStatus(0);
        baseline.setConfirmedBy(null);
        baseline.setConfirmedAt(null);
        if (baselineMapper.updateById(baseline) != 1) {
            throw BusinessException.conflict("需求范围基线已被其他人修改，请刷新后重试");
        }
        replaceScopeItems(projectId, nodeId, cmd.getScopeItems());
        replaceRequirements(projectId, nodeId, cmd.getRequirements(), existingRequirements);
        NodeRequirementScopeDTO result = toDTO(node, baseline);
        operationLogService.record(AuditEvent.success(
                AuditAction.NODE_REQUIREMENT_SCOPE_DRAFT_SAVED.name(), AuditResourceType.PROJECT_NODE.name(),
                nodeId, projectId, null, null, result));
        return result;
    }

    @Transactional
    public NodeRequirementScopeDTO confirm(Long projectId, Long nodeId) {
        ProjectNodeDO node = permissionService.requireManageableNode(projectId, nodeId, "确认需求范围基线");
        requireRequirementNode(node);
        ProjectNodeBaselineDO baseline = findBaseline(projectId, nodeId);
        NodeRequirementScopeDTO current = toDTO(node, baseline);
        validateComplete(current);

        baseline.setStatus(1);
        baseline.setConfirmedBy(UserContext.userIdOrNull());
        baseline.setConfirmedAt(LocalDateTime.now());
        if (baselineMapper.updateById(baseline) != 1) {
            throw BusinessException.conflict("需求范围基线已被其他人修改，请刷新后重试");
        }
        NodeRequirementScopeDTO result = toDTO(node, baseline);
        operationLogService.record(AuditEvent.success(
                AuditAction.NODE_REQUIREMENT_SCOPE_CONFIRMED.name(), AuditResourceType.PROJECT_NODE.name(),
                nodeId, projectId, null, java.util.Map.of("status", "DRAFT"), java.util.Map.of("status", "CONFIRMED")));
        return result;
    }

    /** Called by node lifecycle completion so a client cannot bypass baseline confirmation. */
    public void requireConfirmed(Long projectId, Long nodeId) {
        permissionService.requireProjectReadable(projectId);
        requireRequirementNode(projectId, nodeId);
        ProjectNodeBaselineDO baseline = findBaseline(projectId, nodeId);
        if (baseline == null || !Integer.valueOf(1).equals(baseline.getStatus())) {
            throw BusinessException.error("请先确认需求范围基线");
        }
    }

    private ProjectNodeBaselineDO findBaseline(Long projectId, Long nodeId) {
        return baselineMapper.selectOne(new LambdaQueryWrapper<ProjectNodeBaselineDO>()
                .eq(ProjectNodeBaselineDO::getProjectId, projectId)
                .eq(ProjectNodeBaselineDO::getNodeId, nodeId));
    }

    private ProjectNodeBaselineDO newBaseline(Long projectId, Long nodeId) {
        ProjectNodeBaselineDO baseline = new ProjectNodeBaselineDO();
        baseline.setProjectId(projectId);
        baseline.setNodeId(nodeId);
        baseline.setStatus(0);
        baseline.setVersion(0);
        return baseline;
    }

    private ProjectNodeDO requireRequirementNode(Long projectId, Long nodeId) {
        return requireRequirementNode(permissionService.requireNode(projectId, nodeId));
    }

    private ProjectNodeDO requireRequirementNode(ProjectNodeDO node) {
        permissionService.requireNodeComponent(node, WorkflowComponentKey.REQUIREMENT_SCOPE,
                "仅配置了需求范围组件的节点支持需求工作台");
        return node;
    }

    private void validatePayload(NodeRequirementScopeUpdateCmd cmd) {
        if (cmd == null) throw BusinessException.error("需求范围基线内容不能为空");
        List<NodeScopeItemCmd> scopeItems = cmd.getScopeItems() == null ? List.of() : cmd.getScopeItems();
        for (NodeScopeItemCmd item : scopeItems) {
            String direction = upper(item.getDirection());
            if (!Set.of("IN", "OUT").contains(direction)) {
                throw BusinessException.error("范围项方向不合法");
            }
            if (trim(item.getTitle()) == null) throw BusinessException.error("范围项名称不能为空");
            item.setDirection(direction);
            item.setTitle(trim(item.getTitle()));
        }

        List<NodeRequirementCmd> requirements = cmd.getRequirements() == null ? List.of() : cmd.getRequirements();
        Set<String> codes = new HashSet<>();
        for (NodeRequirementCmd requirement : requirements) {
            String type = upper(requirement.getType());
            if (!Set.of("BUSINESS", "FUNCTIONAL", "CONSTRAINT").contains(type)) {
                throw BusinessException.error("需求类型不合法");
            }
            if (trim(requirement.getName()) == null) throw BusinessException.error("需求名称不能为空");
            if (trim(requirement.getAcceptanceCriteria()) == null) throw BusinessException.error("验收标准不能为空");
            if (requirement.getPriority() == null || requirement.getPriority() < 1 || requirement.getPriority() > 3) {
                throw BusinessException.error("需求优先级不合法");
            }
            int status = requirement.getStatus() == null ? 0 : requirement.getStatus();
            if (status < 0 || status > 1) throw BusinessException.error("需求状态不合法");
            requirement.setType(type);
            requirement.setName(trim(requirement.getName()));
            requirement.setDescription(trim(requirement.getDescription()));
            requirement.setAcceptanceCriteria(trim(requirement.getAcceptanceCriteria()));
            requirement.setStatus(status);
            String code = trim(requirement.getCode());
            if (code != null && !codes.add(code)) throw BusinessException.error("需求编号不能重复");
            requirement.setCode(code);
        }
        cmd.setScopeItems(scopeItems);
        cmd.setRequirements(requirements);
    }

    private void validateComplete(NodeRequirementScopeDTO current) {
        long inScopeCount = current.getScopeItems().stream().filter(item -> "IN".equals(item.getDirection())).count();
        if (inScopeCount == 0) throw BusinessException.error("至少保留一条纳入范围项");
        if (current.getRequirements().isEmpty()) throw BusinessException.error("至少添加一条需求");
        if (current.getRequirements().stream().anyMatch(item -> !Integer.valueOf(1).equals(item.getStatus()))) {
            throw BusinessException.error("请先确认需求清单中的全部需求");
        }
    }

    private void replaceScopeItems(Long projectId, Long nodeId, List<NodeScopeItemCmd> items) {
        scopeItemMapper.delete(new LambdaQueryWrapper<ProjectNodeScopeItemDO>()
                .eq(ProjectNodeScopeItemDO::getProjectId, projectId)
                .eq(ProjectNodeScopeItemDO::getNodeId, nodeId));
        for (int i = 0; i < items.size(); i++) {
            NodeScopeItemCmd cmd = items.get(i);
            ProjectNodeScopeItemDO item = new ProjectNodeScopeItemDO();
            item.setProjectId(projectId);
            item.setNodeId(nodeId);
            item.setDirection(cmd.getDirection());
            item.setTitle(cmd.getTitle());
            item.setSort(cmd.getSort() == null ? i : cmd.getSort());
            item.setCreatedBy(UserContext.userIdOrNull());
            scopeItemMapper.insert(item);
        }
    }

    private void replaceRequirements(Long projectId, Long nodeId, List<NodeRequirementCmd> items,
                                     List<ProjectNodeRequirementDO> existingItems) {
        Map<Long, ProjectNodeRequirementDO> existingById = new HashMap<>();
        for (ProjectNodeRequirementDO existing : existingItems == null ? List.<ProjectNodeRequirementDO>of() : existingItems) {
            if (existing.getId() != null) existingById.put(existing.getId(), existing);
        }
        Set<Long> retainedIds = new HashSet<>();
        Set<String> reservedCodes = items.stream()
                .map(NodeRequirementCmd::getCode)
                .map(this::trim)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> usedCodes = new HashSet<>();
        AtomicInteger next = new AtomicInteger(1);
        for (int i = 0; i < items.size(); i++) {
            NodeRequirementCmd cmd = items.get(i);
            String code = cmd.getCode();
            if (code == null) {
                do {
                    code = String.format(Locale.ROOT, "REQ-%03d", next.getAndIncrement());
                } while (reservedCodes.contains(code) || !usedCodes.add(code));
            } else {
                usedCodes.add(code);
            }
            ProjectNodeRequirementDO existing = cmd.getId() == null ? null : existingById.get(cmd.getId());
            if (cmd.getId() != null && existing == null) {
                throw BusinessException.error("需求不存在或不属于当前节点");
            }
            ProjectNodeRequirementDO item = new ProjectNodeRequirementDO();
            item.setId(existing == null ? null : existing.getId());
            item.setProjectId(projectId);
            item.setNodeId(nodeId);
            item.setCode(code);
            item.setName(cmd.getName());
            item.setDescription(cmd.getDescription());
            item.setType(cmd.getType());
            item.setPriority(cmd.getPriority());
            item.setAcceptanceCriteria(cmd.getAcceptanceCriteria());
            item.setStatus(resolveRequirementStatus(cmd, existingById));
            item.setSort(cmd.getSort() == null ? i : cmd.getSort());
            item.setCreatedBy(existing == null ? UserContext.userIdOrNull() : existing.getCreatedBy());
            if (item.getId() == null) requirementMapper.insert(item);
            else {
                retainedIds.add(item.getId());
                requirementMapper.updateById(item);
            }
        }
        for (ProjectNodeRequirementDO existing : existingById.values()) {
            if (existing.getId() == null || retainedIds.contains(existing.getId())) continue;
            taskRequirementMapper.delete(new LambdaQueryWrapper<ProjectTaskRequirementDO>()
                    .eq(ProjectTaskRequirementDO::getRequirementId, existing.getId()));
            requirementMapper.deleteById(existing.getId());
        }
    }

    private int resolveRequirementStatus(NodeRequirementCmd cmd, Map<Long, ProjectNodeRequirementDO> existingById) {
        ProjectNodeRequirementDO existing = cmd.getId() == null ? null : existingById.get(cmd.getId());
        if (existing == null || requirementContentChanged(cmd, existing)) return 0;
        return cmd.getStatus() == null ? 0 : cmd.getStatus();
    }

    private boolean requirementContentChanged(NodeRequirementCmd cmd, ProjectNodeRequirementDO existing) {
        return !Objects.equals(trim(cmd.getCode()), trim(existing.getCode()))
                || !Objects.equals(trim(cmd.getName()), trim(existing.getName()))
                || !Objects.equals(trim(cmd.getDescription()), trim(existing.getDescription()))
                || !Objects.equals(upper(cmd.getType()), upper(existing.getType()))
                || !Objects.equals(cmd.getPriority(), existing.getPriority())
                || !Objects.equals(trim(cmd.getAcceptanceCriteria()), trim(existing.getAcceptanceCriteria()));
    }

    private NodeRequirementScopeDTO toDTO(ProjectNodeDO node, ProjectNodeBaselineDO baseline) {
        NodeRequirementScopeDTO dto = new NodeRequirementScopeDTO();
        dto.setProjectId(node.getProjectId());
        dto.setNodeId(node.getId());
        dto.setVersion(baseline == null ? null : baseline.getVersion());
        dto.setBaselineStatus(baseline == null || baseline.getStatus() == null ? 0 : baseline.getStatus());
        dto.setConfirmedBy(baseline == null ? null : baseline.getConfirmedBy());
        dto.setConfirmedAt(baseline == null ? null : baseline.getConfirmedAt());
        dto.setCanEdit(!com.brad.pms.common.enums.NodeStatus.isReadOnly(node.getStatus()));
        List<ProjectNodeScopeItemDO> scopes = scopeItemMapper.selectList(new LambdaQueryWrapper<ProjectNodeScopeItemDO>()
                .eq(ProjectNodeScopeItemDO::getProjectId, node.getProjectId())
                .eq(ProjectNodeScopeItemDO::getNodeId, node.getId())
                .orderByAsc(ProjectNodeScopeItemDO::getSort)
                .orderByAsc(ProjectNodeScopeItemDO::getId));
        dto.setScopeItems((scopes == null ? List.<ProjectNodeScopeItemDO>of() : scopes).stream()
                .map(this::toScopeDTO).collect(Collectors.toList()));
        List<ProjectNodeRequirementDO> requirements = requirementMapper.selectList(new LambdaQueryWrapper<ProjectNodeRequirementDO>()
                .eq(ProjectNodeRequirementDO::getProjectId, node.getProjectId())
                .eq(ProjectNodeRequirementDO::getNodeId, node.getId())
                .orderByAsc(ProjectNodeRequirementDO::getSort)
                .orderByAsc(ProjectNodeRequirementDO::getId));
        List<ProjectNodeRequirementDO> safeRequirements = requirements == null
                ? List.of() : requirements;
        Map<Long, int[]> taskProgress = taskProgress(node.getProjectId(), node.getId(), safeRequirements);
        dto.setRequirements(safeRequirements.stream()
                .map(item -> toRequirementDTO(item, taskProgress.get(item.getId())))
                .collect(Collectors.toList()));
        return dto;
    }

    private NodeScopeItemDTO toScopeDTO(ProjectNodeScopeItemDO item) {
        NodeScopeItemDTO dto = new NodeScopeItemDTO();
        dto.setId(item.getId());
        dto.setDirection(item.getDirection());
        dto.setTitle(item.getTitle());
        dto.setSort(item.getSort());
        return dto;
    }

    private NodeRequirementDTO toRequirementDTO(ProjectNodeRequirementDO item, int[] progress) {
        NodeRequirementDTO dto = new NodeRequirementDTO();
        dto.setId(item.getId());
        dto.setCode(item.getCode());
        dto.setName(item.getName());
        dto.setDescription(item.getDescription());
        dto.setType(item.getType());
        dto.setPriority(item.getPriority());
        dto.setAcceptanceCriteria(item.getAcceptanceCriteria());
        dto.setStatus(item.getStatus());
        dto.setSort(item.getSort());
        dto.setTaskCount(progress == null ? 0 : progress[0]);
        dto.setCompletedTaskCount(progress == null ? 0 : progress[1]);
        return dto;
    }

    private Map<Long, int[]> taskProgress(Long projectId, Long nodeId, List<ProjectNodeRequirementDO> requirements) {
        List<Long> requirementIds = requirements.stream()
                .map(ProjectNodeRequirementDO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (requirementIds.isEmpty()) return new HashMap<>();
        List<ProjectTaskRequirementDO> links = taskRequirementMapper.selectList(new LambdaQueryWrapper<ProjectTaskRequirementDO>()
                .eq(ProjectTaskRequirementDO::getProjectId, projectId)
                .eq(ProjectTaskRequirementDO::getNodeId, nodeId)
                .in(ProjectTaskRequirementDO::getRequirementId, requirementIds));
        List<ProjectTaskRequirementDO> safeLinks = links == null ? List.of() : links;
        List<Long> taskIds = safeLinks.stream().map(ProjectTaskRequirementDO::getTaskId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (taskIds.isEmpty()) return new HashMap<>();
        List<ProjectTaskDO> tasks = taskMapper.selectBatchIds(taskIds);
        Map<Long, ProjectTaskDO> taskById = (tasks == null ? List.<ProjectTaskDO>of() : tasks).stream()
                .collect(Collectors.toMap(ProjectTaskDO::getId, item -> item, (left, right) -> left));
        Map<Long, int[]> result = new HashMap<>();
        for (ProjectTaskRequirementDO link : safeLinks) {
            ProjectTaskDO task = taskById.get(link.getTaskId());
            if (task == null) continue;
            int[] counts = result.computeIfAbsent(link.getRequirementId(), ignored -> new int[2]);
            counts[0]++;
            if (Integer.valueOf(2).equals(task.getStatus())) counts[1]++;
        }
        return result;
    }

    private String upper(String value) {
        String text = trim(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        String text = value == null ? null : value.trim();
        return text == null || text.isEmpty() ? null : text;
    }
}
