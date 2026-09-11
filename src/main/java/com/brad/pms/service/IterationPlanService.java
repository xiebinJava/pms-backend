package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.response.NodeIterationPlanDTO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeIterationPlanDO;
import com.brad.pms.entity.ProjectNodePlanBaselineDO;
import com.brad.pms.entity.ProjectNodeSolutionDecisionDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectNodePlanBaselineMapper;
import com.brad.pms.mapper.ProjectNodeSolutionDecisionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IterationPlanService {

    private static final String PLAN_NODE_KEY = "plan";

    private final ProjectNodeIterationPlanMapper iterationPlanMapper;
    private final ProjectNodeMapper nodeMapper;
    private final ProjectNodePlanBaselineMapper baselineMapper;
    private final ProjectNodeSolutionDecisionMapper decisionMapper;
    private final ProjectPermissionService permissionService;
    private final UserService userService;

    public List<NodeIterationPlanDTO> listByProject(Long projectId) {
        permissionService.requireProjectReadable(projectId);
        return listConfirmedByProject(projectId);
    }

    public Set<Long> confirmedPlanIds(Long projectId) {
        return listConfirmedByProject(projectId).stream()
                .map(NodeIterationPlanDTO::getId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
    }

    /**
     * Returns current plans plus plans already referenced by development stories.
     * A solution-decision revision can make an old plan non-current; keeping the
     * old row readable prevents an unchanged story from looking blank or being
     * rejected merely because the project moved to a newer baseline.
     */
    public List<NodeIterationPlanDTO> listForDevelopment(Long projectId, Set<Long> referencedPlanIds) {
        List<NodeIterationPlanDTO> current = new ArrayList<>(listConfirmedByProject(projectId));
        Set<Long> visibleIds = current.stream()
                .map(NodeIterationPlanDTO::getId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Set<Long> missingIds = referencedPlanIds == null ? Set.of() : referencedPlanIds.stream()
                .filter(id -> id != null && !visibleIds.contains(id))
                .collect(Collectors.toSet());
        if (missingIds.isEmpty()) return current;
        current.addAll(toDTOs(findRowsByIds(projectId, missingIds)));
        return current;
    }

    public List<NodeIterationPlanDTO> listForPlanNode(Long projectId, Long nodeId) {
        List<ProjectNodeIterationPlanDO> rows = findRows(projectId, nodeId);
        return toDTOs(rows);
    }

    private List<NodeIterationPlanDTO> listConfirmedByProject(Long projectId) {
        ProjectNodeDO planNode = nodeMapper.selectOne(new LambdaQueryWrapper<ProjectNodeDO>()
                .eq(ProjectNodeDO::getProjectId, projectId)
                .eq(ProjectNodeDO::getNodeKey, PLAN_NODE_KEY));
        if (planNode == null) return List.of();
        ProjectNodePlanBaselineDO baseline = baselineMapper.selectOne(new LambdaQueryWrapper<ProjectNodePlanBaselineDO>()
                .eq(ProjectNodePlanBaselineDO::getProjectId, projectId)
                .eq(ProjectNodePlanBaselineDO::getNodeId, planNode.getId()));
        if (baseline == null || !Integer.valueOf(1).equals(baseline.getStatus()) || !isCurrent(projectId, baseline)) {
            return List.of();
        }
        return toDTOs(findRows(projectId, planNode.getId()));
    }

    private boolean isCurrent(Long projectId, ProjectNodePlanBaselineDO baseline) {
        ProjectNodeDO designNode = nodeMapper.selectOne(new LambdaQueryWrapper<ProjectNodeDO>()
                .eq(ProjectNodeDO::getProjectId, projectId)
                .eq(ProjectNodeDO::getNodeKey, NodeSolutionDesignService.DESIGN_NODE_KEY));
        if (designNode == null) return false;
        ProjectNodeSolutionDecisionDO decision = decisionMapper.selectOne(new LambdaQueryWrapper<ProjectNodeSolutionDecisionDO>()
                .eq(ProjectNodeSolutionDecisionDO::getProjectId, projectId)
                .eq(ProjectNodeSolutionDecisionDO::getNodeId, designNode.getId()));
        return decision != null && "CONFIRMED".equals(decision.getStatus())
                && java.util.Objects.equals(baseline.getSolutionDecisionVersion(), decision.getVersion());
    }

    private List<ProjectNodeIterationPlanDO> findRows(Long projectId, Long nodeId) {
        return iterationPlanMapper.selectList(new LambdaQueryWrapper<ProjectNodeIterationPlanDO>()
                .eq(ProjectNodeIterationPlanDO::getProjectId, projectId)
                .eq(ProjectNodeIterationPlanDO::getNodeId, nodeId)
                .orderByAsc(ProjectNodeIterationPlanDO::getSort)
                .orderByAsc(ProjectNodeIterationPlanDO::getId));
    }

    private List<ProjectNodeIterationPlanDO> findRowsByIds(Long projectId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return iterationPlanMapper.selectList(new LambdaQueryWrapper<ProjectNodeIterationPlanDO>()
                .eq(ProjectNodeIterationPlanDO::getProjectId, projectId)
                .in(ProjectNodeIterationPlanDO::getId, ids)
                .orderByAsc(ProjectNodeIterationPlanDO::getSort)
                .orderByAsc(ProjectNodeIterationPlanDO::getId));
    }

    private List<NodeIterationPlanDTO> toDTOs(List<ProjectNodeIterationPlanDO> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        Set<Long> ownerIds = rows.stream().map(ProjectNodeIterationPlanDO::getOwnerId)
                .filter(id -> id != null).collect(Collectors.toSet());
        List<UserDO> users = ownerIds.isEmpty() ? List.of() : userService.listByIds(new ArrayList<>(ownerIds));
        Map<Long, UserDO> userMap = Convertors.userMap(users == null ? List.of() : users);
        return rows.stream().map(row -> {
            NodeIterationPlanDTO dto = new NodeIterationPlanDTO();
            dto.setId(row.getId());
            dto.setName(row.getName());
            dto.setOwnerId(row.getOwnerId());
            dto.setOwnerName(Convertors.userDisplayName(userMap.get(row.getOwnerId())));
            dto.setGoal(row.getGoal());
            dto.setStatus(row.getStatus());
            dto.setStartDate(row.getStartDate());
            dto.setDueDate(row.getDueDate());
            dto.setSort(row.getSort());
            return dto;
        }).collect(Collectors.toList());
    }
}
