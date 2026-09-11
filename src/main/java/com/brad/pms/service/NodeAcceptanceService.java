package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.request.NodeAcceptanceItemCmd;
import com.brad.pms.dto.request.NodeAcceptanceUpdateCmd;
import com.brad.pms.dto.response.NodeAcceptanceDTO;
import com.brad.pms.dto.response.NodeAcceptanceDefectDTO;
import com.brad.pms.dto.response.NodeAcceptanceItemDTO;
import com.brad.pms.entity.ProjectNodeAcceptanceBaselineDO;
import com.brad.pms.entity.ProjectNodeAcceptanceDefectDO;
import com.brad.pms.entity.ProjectNodeAcceptanceItemDO;
import com.brad.pms.entity.ProjectNodeBaselineDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeRequirementDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectNodeAcceptanceBaselineMapper;
import com.brad.pms.mapper.ProjectNodeAcceptanceDefectMapper;
import com.brad.pms.mapper.ProjectNodeAcceptanceItemMapper;
import com.brad.pms.mapper.ProjectNodeBaselineMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectNodeRequirementMapper;
import com.brad.pms.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
public class NodeAcceptanceService {

    private static final String ACCEPTANCE_NODE_KEY = "acceptance";
    private static final String REQUIREMENT_NODE_KEY = "requirement";
    private static final Set<String> RESULTS = Set.of("PENDING", "PASS", "CONDITIONAL_PASS");
    private static final Set<String> ITEM_RESULTS = Set.of("PENDING", "PASS", "FAIL", "BLOCKED");

    private final ProjectNodeAcceptanceBaselineMapper baselineMapper;
    private final ProjectNodeAcceptanceItemMapper itemMapper;
    private final ProjectNodeAcceptanceDefectMapper defectMapper;
    private final ProjectNodeMapper nodeMapper;
    private final ProjectNodeBaselineMapper requirementBaselineMapper;
    private final ProjectNodeRequirementMapper requirementMapper;
    private final ProjectPermissionService permissionService;
    private final UserService userService;
    private final OperationLogService operationLogService;

    public NodeAcceptanceDTO get(Long projectId, Long nodeId) {
        permissionService.requireProjectReadable(projectId);
        ProjectNodeDO node = requireAcceptanceNode(permissionService.requireNode(projectId, nodeId));
        return toDTO(node, findBaseline(projectId, nodeId));
    }

    @Transactional
    public NodeAcceptanceDTO saveDraft(Long projectId, Long nodeId, NodeAcceptanceUpdateCmd cmd) {
        ProjectNodeDO node = requireAcceptanceNode(permissionService.requireManageableNode(
                projectId, nodeId, "保存业务验收与缺陷闭环"));
        ProjectNodeBaselineDO requirementBaseline = confirmedRequirementBaseline(projectId);
        List<ProjectNodeRequirementDO> requirements = availableConfirmedRequirements(projectId, requirementBaseline);
        if (requirements.isEmpty()) throw BusinessException.error("请先确认需求范围基线");
        validatePayload(cmd, requirements);

        ProjectNodeAcceptanceBaselineDO baseline = findBaseline(projectId, nodeId);
        if (baseline == null) {
            baseline = newBaseline(projectId, nodeId);
            try {
                baselineMapper.insert(baseline);
            } catch (DuplicateKeyException ex) {
                throw BusinessException.conflict("业务验收与缺陷闭环已被其他人创建，请刷新后重试");
            }
        } else if (cmd.getVersion() == null || !Objects.equals(cmd.getVersion(), baseline.getVersion())) {
            throw BusinessException.conflict("业务验收与缺陷闭环已被其他人修改，请刷新后重试");
        }

        baseline.setStatus(0);
        baseline.setConfirmedBy(null);
        baseline.setConfirmedAt(null);
        baseline.setResult(normalize(cmd.getResult(), "PENDING"));
        baseline.setResidualItems(trim(cmd.getResidualItems()));
        baseline.setRequirementBaselineVersion(requirementBaseline.getVersion());
        if (baselineMapper.updateById(baseline) != 1) {
            throw BusinessException.conflict("业务验收与缺陷闭环已被其他人修改，请刷新后重试");
        }
        replaceItems(projectId, nodeId, cmd.getItems(), requirements);
        NodeAcceptanceDTO result = toDTO(node, baseline);
        operationLogService.record(AuditEvent.success(
                AuditAction.NODE_ACCEPTANCE_DRAFT_SAVED.name(), AuditResourceType.PROJECT_NODE.name(),
                nodeId, projectId, null, null, result));
        return result;
    }

    @Transactional
    public NodeAcceptanceDTO confirm(Long projectId, Long nodeId) {
        ProjectNodeDO node = requireAcceptanceNode(permissionService.requireManageableNode(
                projectId, nodeId, "确认业务验收与缺陷闭环"));
        ProjectNodeAcceptanceBaselineDO baseline = findBaseline(projectId, nodeId);
        if (baseline == null) throw BusinessException.error("请先保存业务验收与缺陷闭环");
        if (Integer.valueOf(1).equals(baseline.getStatus())) {
            throw BusinessException.conflict("业务验收已确认");
        }
        ProjectNodeBaselineDO requirementBaseline = confirmedRequirementBaseline(projectId);
        NodeAcceptanceDTO current = toDTO(node, baseline);
        if (current.isSourceBaselineChanged()) {
            throw BusinessException.error("需求范围基线已变更，请重新完成业务验收");
        }
        validateComplete(current);

        baseline.setStatus(1);
        baseline.setConfirmedBy(UserContext.userIdOrNull());
        baseline.setConfirmedAt(LocalDateTime.now());
        baseline.setRequirementBaselineVersion(requirementBaseline.getVersion());
        if (baselineMapper.updateById(baseline) != 1) {
            throw BusinessException.conflict("业务验收与缺陷闭环已被其他人修改，请刷新后重试");
        }
        NodeAcceptanceDTO result = toDTO(node, baseline);
        operationLogService.record(AuditEvent.success(
                AuditAction.NODE_ACCEPTANCE_CONFIRMED.name(), AuditResourceType.PROJECT_NODE.name(),
                nodeId, projectId, null, Map.of("status", "DRAFT"), Map.of("status", "CONFIRMED")));
        return result;
    }

    /** Lifecycle completion guard; manual acceptance confirmation remains the source of truth. */
    public void requireConfirmed(Long projectId, Long nodeId) {
        permissionService.requireProjectReadable(projectId);
        ProjectNodeDO node = requireAcceptanceNode(permissionService.requireNode(projectId, nodeId));
        ProjectNodeAcceptanceBaselineDO baseline = findBaseline(projectId, nodeId);
        if (baseline == null || !Integer.valueOf(1).equals(baseline.getStatus())) {
            throw BusinessException.error("请先确认业务验收与缺陷闭环");
        }
        ProjectNodeBaselineDO requirementBaseline = confirmedRequirementBaseline(projectId);
        if (!Objects.equals(baseline.getRequirementBaselineVersion(), requirementBaseline.getVersion())) {
            throw BusinessException.error("需求范围基线已变更，请重新完成业务验收");
        }
    }

    private void validatePayload(NodeAcceptanceUpdateCmd cmd, List<ProjectNodeRequirementDO> requirements) {
        if (cmd == null) throw BusinessException.error("业务验收内容不能为空");
        String result = normalize(cmd.getResult(), "PENDING");
        if (!RESULTS.contains(result)) throw BusinessException.error("验收结论不合法");
        List<NodeAcceptanceItemCmd> items = cmd.getItems() == null ? new ArrayList<>() : cmd.getItems();
        Map<Long, ProjectNodeRequirementDO> requirementMap = requirements.stream()
                .collect(Collectors.toMap(ProjectNodeRequirementDO::getId, item -> item));
        Set<Long> ids = new HashSet<>();
        for (NodeAcceptanceItemCmd item : items) {
            if (item == null) throw BusinessException.error("验收项不能为空");
            if (item.getRequirementId() == null || !requirementMap.containsKey(item.getRequirementId())) {
                throw BusinessException.error("验收项必须来自已确认需求");
            }
            if (!ids.add(item.getRequirementId())) throw BusinessException.error("验收项不能重复");
            item.setResult(normalize(item.getResult(), "PENDING"));
            if (!ITEM_RESULTS.contains(item.getResult())) throw BusinessException.error("验收项结果不合法");
            item.setNote(trim(item.getNote()));
            if (item.getNote() != null && item.getNote().length() > 1000) {
                throw BusinessException.error("验收记录不能超过1000个字符");
            }
        }
        if (items.size() != requirements.size()) throw BusinessException.error("请覆盖全部已确认需求");
        cmd.setResult(result);
        cmd.setResidualItems(trim(cmd.getResidualItems()));
        if (cmd.getResidualItems() != null && cmd.getResidualItems().length() > 2000) {
            throw BusinessException.error("遗留事项不能超过2000个字符");
        }
        cmd.setItems(items);
    }

    private void validateComplete(NodeAcceptanceDTO current) {
        if (current.getItems().isEmpty()
                || current.getItems().stream().anyMatch(item -> !"PASS".equals(item.getResult()))) {
            throw BusinessException.error("请先完成全部验收项");
        }
        if (!Set.of("PASS", "CONDITIONAL_PASS").contains(current.getResult())) {
            throw BusinessException.error("请先填写验收结论");
        }
        if ("CONDITIONAL_PASS".equals(current.getResult()) && trim(current.getResidualItems()) == null) {
            throw BusinessException.error("条件通过时请填写遗留事项");
        }
    }

    private void replaceItems(Long projectId, Long nodeId, List<NodeAcceptanceItemCmd> items,
                              List<ProjectNodeRequirementDO> requirements) {
        Map<Long, ProjectNodeRequirementDO> requirementMap = requirements.stream()
                .collect(Collectors.toMap(ProjectNodeRequirementDO::getId, item -> item));
        itemMapper.delete(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ProjectNodeAcceptanceItemDO>()
                .eq(ProjectNodeAcceptanceItemDO::getProjectId, projectId)
                .eq(ProjectNodeAcceptanceItemDO::getNodeId, nodeId));
        for (int i = 0; i < items.size(); i++) {
            NodeAcceptanceItemCmd cmd = items.get(i);
            ProjectNodeRequirementDO requirement = requirementMap.get(cmd.getRequirementId());
            ProjectNodeAcceptanceItemDO item = new ProjectNodeAcceptanceItemDO();
            item.setProjectId(projectId);
            item.setNodeId(nodeId);
            item.setRequirementId(requirement.getId());
            item.setRequirementCode(requirement.getCode());
            item.setRequirementName(requirement.getName());
            item.setAcceptanceCriteria(requirement.getAcceptanceCriteria());
            item.setResult(cmd.getResult());
            item.setNote(cmd.getNote());
            item.setSort(cmd.getSort() == null ? i : cmd.getSort());
            item.setCreatedBy(UserContext.userIdOrNull());
            itemMapper.insert(item);
        }
    }

    private NodeAcceptanceDTO toDTO(ProjectNodeDO node, ProjectNodeAcceptanceBaselineDO baseline) {
        NodeAcceptanceDTO dto = new NodeAcceptanceDTO();
        dto.setProjectId(node.getProjectId());
        dto.setNodeId(node.getId());
        dto.setVersion(baseline == null ? null : baseline.getVersion());
        dto.setStatus(baseline == null || baseline.getStatus() == null ? 0 : baseline.getStatus());
        dto.setResult(baseline == null ? "PENDING" : normalize(baseline.getResult(), "PENDING"));
        dto.setResidualItems(baseline == null ? null : baseline.getResidualItems());
        dto.setConfirmedBy(baseline == null ? null : baseline.getConfirmedBy());
        dto.setConfirmedAt(baseline == null ? null : baseline.getConfirmedAt());
        ProjectNodeBaselineDO requirementBaseline = findRequirementBaseline(node.getProjectId());
        dto.setSourceBaselineVersion(requirementBaseline == null ? null : requirementBaseline.getVersion());
        boolean sourceBaselineChanged = baseline != null
                && !Objects.equals(baseline.getRequirementBaselineVersion(), dto.getSourceBaselineVersion());
        dto.setSourceBaselineChanged(sourceBaselineChanged);
        if (sourceBaselineChanged) {
            dto.setResult("PENDING");
            dto.setConfirmedBy(null);
            dto.setConfirmedByName(null);
            dto.setConfirmedAt(null);
        }
        dto.setCanEdit(!com.brad.pms.common.enums.NodeStatus.isReadOnly(node.getStatus()));
        if (dto.getConfirmedBy() != null) {
            List<UserDO> users = userService.listByIds(List.of(dto.getConfirmedBy()));
            UserDO confirmedBy = users == null ? null : users.stream().findFirst().orElse(null);
            dto.setConfirmedByName(Convertors.userDisplayName(confirmedBy));
        }

        List<ProjectNodeRequirementDO> requirements = availableConfirmedRequirements(node.getProjectId(), requirementBaseline);
        Map<Long, ProjectNodeAcceptanceItemDO> existingItems = itemMapper.selectList(new LambdaQueryWrapper<ProjectNodeAcceptanceItemDO>()
                        .eq(ProjectNodeAcceptanceItemDO::getProjectId, node.getProjectId())
                        .eq(ProjectNodeAcceptanceItemDO::getNodeId, node.getId())
                        .orderByAsc(ProjectNodeAcceptanceItemDO::getSort)
                        .orderByAsc(ProjectNodeAcceptanceItemDO::getId))
                .stream().collect(Collectors.toMap(ProjectNodeAcceptanceItemDO::getRequirementId, item -> item, (left, right) -> left));
        dto.setItems(requirements.stream().map(requirement -> toItemDTO(
                        requirement, existingItems.get(requirement.getId()), sourceBaselineChanged))
                .collect(Collectors.toList()));
        dto.setDefects(defectMapper.selectList(new LambdaQueryWrapper<ProjectNodeAcceptanceDefectDO>()
                        .eq(ProjectNodeAcceptanceDefectDO::getProjectId, node.getProjectId())
                        .eq(ProjectNodeAcceptanceDefectDO::getNodeId, node.getId())
                        .orderByAsc(ProjectNodeAcceptanceDefectDO::getId))
                .stream().map(this::toDefectDTO).collect(Collectors.toList()));
        return dto;
    }

    private NodeAcceptanceItemDTO toItemDTO(ProjectNodeRequirementDO requirement,
                                             ProjectNodeAcceptanceItemDO existing,
                                             boolean sourceBaselineChanged) {
        NodeAcceptanceItemDTO dto = new NodeAcceptanceItemDTO();
        dto.setId(existing == null ? null : existing.getId());
        dto.setRequirementId(requirement.getId());
        dto.setRequirementCode(requirement.getCode());
        dto.setRequirementName(requirement.getName());
        dto.setAcceptanceCriteria(requirement.getAcceptanceCriteria());
        dto.setResult(sourceBaselineChanged || existing == null ? "PENDING" : normalize(existing.getResult(), "PENDING"));
        dto.setNote(sourceBaselineChanged || existing == null ? null : existing.getNote());
        dto.setSort(existing == null ? requirement.getSort() : existing.getSort());
        return dto;
    }

    private NodeAcceptanceDefectDTO toDefectDTO(ProjectNodeAcceptanceDefectDO defect) {
        NodeAcceptanceDefectDTO dto = new NodeAcceptanceDefectDTO();
        dto.setId(defect.getId());
        dto.setSourceDefectId(defect.getSourceDefectId());
        dto.setDefectKey(defect.getDefectKey());
        dto.setTitle(defect.getTitle());
        dto.setSeverity(defect.getSeverity());
        dto.setStatus(defect.getStatus());
        dto.setImpact(defect.getImpact());
        return dto;
    }

    private ProjectNodeBaselineDO confirmedRequirementBaseline(Long projectId) {
        ProjectNodeBaselineDO baseline = findRequirementBaseline(projectId);
        if (baseline == null || !Integer.valueOf(1).equals(baseline.getStatus())) {
            throw BusinessException.error("请先确认需求范围基线");
        }
        return baseline;
    }

    private ProjectNodeBaselineDO findRequirementBaseline(Long projectId) {
        ProjectNodeDO requirementNode = nodeMapper.selectOne(new LambdaQueryWrapper<ProjectNodeDO>()
                .eq(ProjectNodeDO::getProjectId, projectId)
                .eq(ProjectNodeDO::getNodeKey, REQUIREMENT_NODE_KEY));
        if (requirementNode == null) return null;
        return requirementBaselineMapper.selectOne(new LambdaQueryWrapper<ProjectNodeBaselineDO>()
                .eq(ProjectNodeBaselineDO::getProjectId, projectId)
                .eq(ProjectNodeBaselineDO::getNodeId, requirementNode.getId()));
    }

    private List<ProjectNodeRequirementDO> availableConfirmedRequirements(
            Long projectId, ProjectNodeBaselineDO requirementBaseline) {
        if (requirementBaseline == null || !Integer.valueOf(1).equals(requirementBaseline.getStatus())) {
            return List.of();
        }
        ProjectNodeDO requirementNode = nodeMapper.selectOne(new LambdaQueryWrapper<ProjectNodeDO>()
                .eq(ProjectNodeDO::getProjectId, projectId)
                .eq(ProjectNodeDO::getNodeKey, REQUIREMENT_NODE_KEY));
        if (requirementNode == null) return List.of();
        List<ProjectNodeRequirementDO> requirements = requirementMapper.selectList(new LambdaQueryWrapper<ProjectNodeRequirementDO>()
                .eq(ProjectNodeRequirementDO::getProjectId, projectId)
                .eq(ProjectNodeRequirementDO::getNodeId, requirementNode.getId())
                .eq(ProjectNodeRequirementDO::getStatus, 1)
                .orderByAsc(ProjectNodeRequirementDO::getSort)
                .orderByAsc(ProjectNodeRequirementDO::getId));
        return requirements == null ? List.of() : requirements;
    }

    private ProjectNodeAcceptanceBaselineDO findBaseline(Long projectId, Long nodeId) {
        return baselineMapper.selectOne(new LambdaQueryWrapper<ProjectNodeAcceptanceBaselineDO>()
                .eq(ProjectNodeAcceptanceBaselineDO::getProjectId, projectId)
                .eq(ProjectNodeAcceptanceBaselineDO::getNodeId, nodeId));
    }

    private ProjectNodeAcceptanceBaselineDO newBaseline(Long projectId, Long nodeId) {
        ProjectNodeAcceptanceBaselineDO baseline = new ProjectNodeAcceptanceBaselineDO();
        baseline.setProjectId(projectId);
        baseline.setNodeId(nodeId);
        baseline.setStatus(0);
        baseline.setResult("PENDING");
        baseline.setVersion(0);
        return baseline;
    }

    private ProjectNodeDO requireAcceptanceNode(ProjectNodeDO node) {
        if (node == null || !ACCEPTANCE_NODE_KEY.equals(node.getNodeKey())) {
            throw BusinessException.error("仅业务验收与缺陷闭环节点支持验收工作台");
        }
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
}
