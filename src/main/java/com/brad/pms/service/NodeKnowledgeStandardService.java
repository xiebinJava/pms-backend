package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
import com.brad.pms.common.enums.NodeStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.request.NodeKnowledgeActionCmd;
import com.brad.pms.dto.request.NodeKnowledgeAssetCmd;
import com.brad.pms.dto.request.NodeKnowledgeStandardUpdateCmd;
import com.brad.pms.dto.response.NodeKnowledgeActionDTO;
import com.brad.pms.dto.response.NodeKnowledgeAssetDTO;
import com.brad.pms.dto.response.NodeKnowledgeStandardDTO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeKnowledgeActionDO;
import com.brad.pms.entity.ProjectNodeKnowledgeAssetDO;
import com.brad.pms.entity.ProjectNodeKnowledgeBaselineDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectNodeKnowledgeActionMapper;
import com.brad.pms.mapper.ProjectNodeKnowledgeAssetMapper;
import com.brad.pms.mapper.ProjectNodeKnowledgeBaselineMapper;
import com.brad.pms.security.UserContext;
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
public class NodeKnowledgeStandardService {

    public static final String KNOWLEDGE_NODE_KEY = "knowledge";
    private static final Set<String> ASSET_TYPES = Set.of("TEMPLATE", "CHECKLIST", "CASE", "STANDARD");
    private static final Set<String> ASSET_STATUSES = Set.of("UPDATED", "REVIEW", "RETAINED", "PENDING");
    private static final Set<String> ACTION_STATUSES = Set.of("NOT_STARTED", "IN_PROGRESS", "DONE");

    private final ProjectNodeKnowledgeBaselineMapper baselineMapper;
    private final ProjectNodeKnowledgeAssetMapper assetMapper;
    private final ProjectNodeKnowledgeActionMapper actionMapper;
    private final MemberService memberService;
    private final ProjectPermissionService permissionService;
    private final UserService userService;
    private final OperationLogService operationLogService;

    public NodeKnowledgeStandardDTO get(Long projectId, Long nodeId) {
        permissionService.requireProjectReadable(projectId);
        ProjectNodeDO node = requireKnowledgeNode(permissionService.requireNode(projectId, nodeId));
        return toDTO(node, findBaseline(projectId, nodeId));
    }

    @Transactional
    public NodeKnowledgeStandardDTO save(Long projectId, Long nodeId, NodeKnowledgeStandardUpdateCmd cmd) {
        ProjectNodeDO node = requireKnowledgeNode(permissionService.requireManageableNode(
                projectId, nodeId, "保存知识沉淀与标准改进"));
        validatePayload(cmd);
        validateOwners(projectId, cmd);

        ProjectNodeKnowledgeBaselineDO baseline = findBaseline(projectId, nodeId);
        if (baseline == null) {
            baseline = newBaseline(projectId, nodeId);
            try {
                baselineMapper.insert(baseline);
            } catch (DuplicateKeyException ex) {
                throw BusinessException.conflict("知识沉淀与标准改进已被其他人创建，请刷新后重试");
            }
        } else if (cmd.getVersion() == null || !Objects.equals(cmd.getVersion(), baseline.getVersion())) {
            throw BusinessException.conflict("知识沉淀与标准改进已被其他人修改，请刷新后重试");
        }

        if (baselineMapper.updateById(baseline) != 1) {
            throw BusinessException.conflict("知识沉淀与标准改进已被其他人修改，请刷新后重试");
        }
        replaceAssets(projectId, nodeId, cmd.getAssets());
        replaceActions(projectId, nodeId, cmd.getActions());
        NodeKnowledgeStandardDTO result = toDTO(node, baseline);
        operationLogService.record(AuditEvent.success(
                AuditAction.NODE_KNOWLEDGE_STANDARD_SAVED.name(), AuditResourceType.PROJECT_NODE.name(),
                nodeId, projectId, null, null, result));
        return result;
    }

    private void validatePayload(NodeKnowledgeStandardUpdateCmd cmd) {
        if (cmd == null) throw BusinessException.error("知识沉淀与标准改进内容不能为空");
        List<NodeKnowledgeAssetCmd> assets = cmd.getAssets() == null ? new ArrayList<>() : cmd.getAssets();
        for (NodeKnowledgeAssetCmd asset : assets) {
            asset.setName(trim(asset.getName()));
            asset.setSource(trim(asset.getSource()));
            asset.setImprovement(trim(asset.getImprovement()));
            asset.setType(normalize(asset.getType(), "CASE"));
            asset.setStatus(normalize(asset.getStatus(), "PENDING"));
            if (asset.getName() == null) throw BusinessException.error("知识资产名称不能为空");
            if (!ASSET_TYPES.contains(asset.getType())) throw BusinessException.error("知识资产类型不合法");
            if (!ASSET_STATUSES.contains(asset.getStatus())) throw BusinessException.error("知识资产状态不合法");
        }
        List<NodeKnowledgeActionCmd> actions = cmd.getActions() == null ? new ArrayList<>() : cmd.getActions();
        for (NodeKnowledgeActionCmd action : actions) {
            action.setTitle(trim(action.getTitle()));
            action.setNote(trim(action.getNote()));
            action.setStatus(normalize(action.getStatus(), "NOT_STARTED"));
            if (action.getTitle() == null) throw BusinessException.error("改进行动名称不能为空");
            if (!ACTION_STATUSES.contains(action.getStatus())) throw BusinessException.error("改进行动状态不合法");
        }
        cmd.setAssets(assets);
        cmd.setActions(actions);
    }

    private void validateOwners(Long projectId, NodeKnowledgeStandardUpdateCmd cmd) {
        Set<Long> ownerIds = cmd.getActions().stream()
                .map(NodeKnowledgeActionCmd::getOwnerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        memberService.ensureMembers(projectId, ownerIds);
    }

    private void replaceAssets(Long projectId, Long nodeId, List<NodeKnowledgeAssetCmd> items) {
        assetMapper.delete(new LambdaQueryWrapper<ProjectNodeKnowledgeAssetDO>()
                .eq(ProjectNodeKnowledgeAssetDO::getProjectId, projectId)
                .eq(ProjectNodeKnowledgeAssetDO::getNodeId, nodeId));
        for (int i = 0; i < items.size(); i++) {
            NodeKnowledgeAssetCmd cmd = items.get(i);
            ProjectNodeKnowledgeAssetDO item = new ProjectNodeKnowledgeAssetDO();
            item.setProjectId(projectId);
            item.setNodeId(nodeId);
            item.setName(cmd.getName());
            item.setSource(cmd.getSource());
            item.setAssetType(cmd.getType());
            item.setImprovement(cmd.getImprovement());
            item.setStatus(cmd.getStatus());
            item.setSort(cmd.getSort() == null ? i : cmd.getSort());
            item.setCreatedBy(UserContext.userIdOrNull());
            if (assetMapper.insert(item) != 1) throw BusinessException.conflict("知识资产保存失败，请刷新后重试");
        }
    }

    private void replaceActions(Long projectId, Long nodeId, List<NodeKnowledgeActionCmd> items) {
        actionMapper.delete(new LambdaQueryWrapper<ProjectNodeKnowledgeActionDO>()
                .eq(ProjectNodeKnowledgeActionDO::getProjectId, projectId)
                .eq(ProjectNodeKnowledgeActionDO::getNodeId, nodeId));
        for (int i = 0; i < items.size(); i++) {
            NodeKnowledgeActionCmd cmd = items.get(i);
            ProjectNodeKnowledgeActionDO item = new ProjectNodeKnowledgeActionDO();
            item.setProjectId(projectId);
            item.setNodeId(nodeId);
            item.setTitle(cmd.getTitle());
            item.setNote(cmd.getNote());
            item.setOwnerId(cmd.getOwnerId());
            item.setDueDate(cmd.getDueDate());
            item.setStatus(cmd.getStatus());
            item.setSort(cmd.getSort() == null ? i : cmd.getSort());
            item.setCreatedBy(UserContext.userIdOrNull());
            if (actionMapper.insert(item) != 1) throw BusinessException.conflict("改进行动保存失败，请刷新后重试");
        }
    }

    private NodeKnowledgeStandardDTO toDTO(ProjectNodeDO node, ProjectNodeKnowledgeBaselineDO baseline) {
        List<ProjectNodeKnowledgeAssetDO> assets = assetMapper.selectList(new LambdaQueryWrapper<ProjectNodeKnowledgeAssetDO>()
                .eq(ProjectNodeKnowledgeAssetDO::getProjectId, node.getProjectId())
                .eq(ProjectNodeKnowledgeAssetDO::getNodeId, node.getId())
                .orderByAsc(ProjectNodeKnowledgeAssetDO::getSort)
                .orderByAsc(ProjectNodeKnowledgeAssetDO::getId));
        List<ProjectNodeKnowledgeActionDO> actions = actionMapper.selectList(new LambdaQueryWrapper<ProjectNodeKnowledgeActionDO>()
                .eq(ProjectNodeKnowledgeActionDO::getProjectId, node.getProjectId())
                .eq(ProjectNodeKnowledgeActionDO::getNodeId, node.getId())
                .orderByAsc(ProjectNodeKnowledgeActionDO::getSort)
                .orderByAsc(ProjectNodeKnowledgeActionDO::getId));
        Set<Long> ownerIds = actions.stream().map(ProjectNodeKnowledgeActionDO::getOwnerId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<UserDO> ownerUsers = ownerIds.isEmpty() ? List.of() : userService.listByIds(new ArrayList<>(ownerIds));
        Map<Long, UserDO> users = Convertors.userMap(ownerUsers == null ? List.of() : ownerUsers);
        NodeKnowledgeStandardDTO dto = new NodeKnowledgeStandardDTO();
        dto.setProjectId(node.getProjectId());
        dto.setNodeId(node.getId());
        dto.setVersion(baseline == null ? null : baseline.getVersion());
        dto.setUpdatedAt(baseline == null ? null : baseline.getUpdatedAt());
        dto.setCanEdit(!NodeStatus.isReadOnly(node.getStatus()));
        dto.setAssets(assets.stream().map(this::toAssetDTO).collect(Collectors.toList()));
        dto.setActions(actions.stream().map(item -> toActionDTO(item, users.get(item.getOwnerId()))).collect(Collectors.toList()));
        return dto;
    }

    private NodeKnowledgeAssetDTO toAssetDTO(ProjectNodeKnowledgeAssetDO item) {
        NodeKnowledgeAssetDTO dto = new NodeKnowledgeAssetDTO();
        dto.setId(item.getId()); dto.setName(item.getName()); dto.setSource(item.getSource());
        dto.setType(item.getAssetType()); dto.setImprovement(item.getImprovement());
        dto.setStatus(item.getStatus()); dto.setSort(item.getSort());
        return dto;
    }

    private NodeKnowledgeActionDTO toActionDTO(ProjectNodeKnowledgeActionDO item, UserDO owner) {
        NodeKnowledgeActionDTO dto = new NodeKnowledgeActionDTO();
        dto.setId(item.getId()); dto.setTitle(item.getTitle()); dto.setNote(item.getNote());
        dto.setOwnerId(item.getOwnerId()); dto.setOwnerName(Convertors.userDisplayName(owner));
        dto.setDueDate(item.getDueDate()); dto.setStatus(item.getStatus()); dto.setSort(item.getSort());
        return dto;
    }

    private ProjectNodeKnowledgeBaselineDO findBaseline(Long projectId, Long nodeId) {
        return baselineMapper.selectOne(new LambdaQueryWrapper<ProjectNodeKnowledgeBaselineDO>()
                .eq(ProjectNodeKnowledgeBaselineDO::getProjectId, projectId)
                .eq(ProjectNodeKnowledgeBaselineDO::getNodeId, nodeId));
    }

    private ProjectNodeKnowledgeBaselineDO newBaseline(Long projectId, Long nodeId) {
        ProjectNodeKnowledgeBaselineDO baseline = new ProjectNodeKnowledgeBaselineDO();
        baseline.setProjectId(projectId); baseline.setNodeId(nodeId); baseline.setVersion(0);
        baseline.setCreatedBy(UserContext.userIdOrNull());
        return baseline;
    }

    private ProjectNodeDO requireKnowledgeNode(ProjectNodeDO node) {
        if (node == null || !KNOWLEDGE_NODE_KEY.equals(node.getNodeKey())) {
            throw BusinessException.error("仅知识沉淀与标准改进节点支持知识工作台");
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
