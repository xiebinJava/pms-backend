package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodePlanResourceRiskUpdateCmd;
import com.brad.pms.dto.request.NodeResourceCmd;
import com.brad.pms.dto.request.NodeRiskCmd;
import com.brad.pms.dto.request.NodeIterationPlanCmd;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodePlanBaselineDO;
import com.brad.pms.entity.ProjectNodeResourceDO;
import com.brad.pms.entity.ProjectNodeRiskDO;
import com.brad.pms.entity.ProjectNodeIterationPlanDO;
import com.brad.pms.entity.ProjectNodeSolutionDecisionDO;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectMemberMapper;
import com.brad.pms.mapper.ProjectNodePlanBaselineMapper;
import com.brad.pms.mapper.ProjectNodeResourceMapper;
import com.brad.pms.mapper.ProjectNodeRiskMapper;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeSolutionDecisionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodePlanResourceRiskServiceTest {

    @Mock ProjectNodePlanBaselineMapper baselineMapper;
    @Mock ProjectNodeResourceMapper resourceMapper;
    @Mock ProjectNodeRiskMapper riskMapper;
    @Mock ProjectNodeIterationPlanMapper iterationPlanMapper;
    @Mock ProjectNodeDevelopmentStoryMapper storyMapper;
    @Mock ProjectNodeSolutionDecisionMapper decisionMapper;
    @Mock ProjectNodeMapper nodeMapper;
    @Mock ProjectMemberMapper memberMapper;
    @Mock MemberService memberService;
    @Mock ProjectPermissionService permissionService;
    @Mock UserService userService;
    @Mock OperationLogService operationLogService;
    @InjectMocks NodePlanResourceRiskService service;

    @BeforeEach
    void stubConfirmedSolutionDecision() {
        lenient().when(nodeMapper.selectOne(any())).thenReturn(node("design"));
        lenient().when(decisionMapper.selectOne(any())).thenReturn(confirmedSolutionDecision());
        lenient().when(iterationPlanMapper.selectList(any())).thenReturn(List.of(iterationPlan()));
        lenient().when(iterationPlanMapper.insert(any(ProjectNodeIterationPlanDO.class))).thenReturn(1);
        lenient().when(iterationPlanMapper.updateById(any(ProjectNodeIterationPlanDO.class))).thenReturn(1);
        lenient().when(iterationPlanMapper.deleteById(any(Long.class))).thenReturn(1);
    }

    @Test
    void rejectsNonPlanNodes() {
        ProjectNodeDO node = node("design");
        when(permissionService.requireNode(1L, 10L)).thenReturn(node);

        assertThatThrownBy(() -> service.get(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("计划、资源与风险基线");
    }

    @Test
    void savesTrimmedDraftRows() {
        ProjectNodeDO node = node("plan");
        when(permissionService.requireManageableNode(1L, 10L, "保存计划、资源与风险基线")).thenReturn(node);
        when(baselineMapper.selectOne(any())).thenReturn(null);
        when(baselineMapper.insert(any(ProjectNodePlanBaselineDO.class))).thenReturn(1);
        when(baselineMapper.updateById(any(ProjectNodePlanBaselineDO.class))).thenReturn(1);
        when(resourceMapper.insert(any(ProjectNodeResourceDO.class))).thenReturn(1);
        when(riskMapper.insert(any(ProjectNodeRiskDO.class))).thenReturn(1);

        service.saveDraft(1L, 10L, updateCommand());

        ArgumentCaptor<ProjectNodePlanBaselineDO> baselineCaptor = ArgumentCaptor.forClass(ProjectNodePlanBaselineDO.class);
        verify(baselineMapper).insert(baselineCaptor.capture());
        assertThat(baselineCaptor.getValue().getStatus()).isZero();
        verify(resourceMapper).insert(any(ProjectNodeResourceDO.class));
        verify(riskMapper).insert(any(ProjectNodeRiskDO.class));
        verify(operationLogService).record(org.mockito.ArgumentMatchers.argThat(event ->
                AuditAction.NODE_PLAN_RESOURCE_RISK_DRAFT_SAVED.name().equals(event.action())
                        && Long.valueOf(10L).equals(event.resourceId())
                        && Long.valueOf(1L).equals(event.projectId())));
    }

    @Test
    void rejectsConfirmationWithoutACompleteBaseline() {
        ProjectNodeDO node = node("plan");
        when(permissionService.requireManageableNode(1L, 10L, "确认计划、资源与风险基线")).thenReturn(node);
        when(baselineMapper.selectOne(any())).thenReturn(baseline());
        when(resourceMapper.selectList(any())).thenReturn(List.of());
        when(riskMapper.selectList(any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.confirm(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("明确所有关键角色负责人和投入重点");
        verify(baselineMapper, never()).updateById(any(ProjectNodePlanBaselineDO.class));
    }

    @Test
    void rejectsConfirmingAnAlreadyConfirmedBaselineWithoutAContentChange() {
        ProjectNodeDO node = node("plan");
        ProjectNodePlanBaselineDO baseline = baseline();
        baseline.setStatus(1);
        when(permissionService.requireManageableNode(1L, 10L, "确认计划、资源与风险基线")).thenReturn(node);
        when(baselineMapper.selectOne(any())).thenReturn(baseline);

        assertThatThrownBy(() -> service.confirm(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已确认");
        verify(baselineMapper, never()).updateById(any(ProjectNodePlanBaselineDO.class));
    }

    @Test
    void editsAConfirmedBaselineDirectlyAndClearsItsConfirmation() {
        ProjectNodeDO node = node("plan");
        ProjectNodePlanBaselineDO baseline = baseline();
        baseline.setStatus(1);
        when(permissionService.requireManageableNode(1L, 10L, "保存计划、资源与风险基线")).thenReturn(node);
        when(baselineMapper.selectOne(any())).thenReturn(baseline);
        when(baselineMapper.updateById(any(ProjectNodePlanBaselineDO.class))).thenReturn(1);

        var result = service.saveDraft(1L, 10L, updateCommand());

        assertThat(result.getBaselineStatus()).isZero();
        ArgumentCaptor<ProjectNodePlanBaselineDO> captor = ArgumentCaptor.forClass(ProjectNodePlanBaselineDO.class);
        verify(baselineMapper).updateById(captor.capture());
        assertThat(captor.getValue().getConfirmedAt()).isNull();
    }

    @Test
    void requiresAConfirmedSolutionDecisionBeforeSavingDraft() {
        ProjectNodeDO node = node("plan");
        ProjectNodeSolutionDecisionDO decision = confirmedSolutionDecision();
        decision.setStatus("DRAFT");
        when(permissionService.requireManageableNode(1L, 10L, "保存计划、资源与风险基线")).thenReturn(node);
        when(nodeMapper.selectOne(any())).thenReturn(node("design"));
        when(decisionMapper.selectOne(any())).thenReturn(decision);

        assertThatThrownBy(() -> service.saveDraft(1L, 10L, updateCommand()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("先确认方案决策");
        verify(baselineMapper, never()).insert(any(ProjectNodePlanBaselineDO.class));
    }

    @Test
    void confirmsACompletePlanResourceRiskBaselineAndAuditsIt() {
        ProjectNodeDO node = node("plan");
        ProjectNodePlanBaselineDO baseline = baseline();
        ProjectNodeResourceDO resource = new ProjectNodeResourceDO();
        resource.setRoleName("技术负责人");
        resource.setOwnerId(21L);
        resource.setFocus("接口梳理");
        ProjectNodeRiskDO risk = new ProjectNodeRiskDO();
        risk.setTitle("数据质量");
        risk.setOwnerId(21L);
        risk.setResponse("先做样本核验");
        when(permissionService.requireManageableNode(1L, 10L, "确认计划、资源与风险基线")).thenReturn(node);
        when(baselineMapper.selectOne(any())).thenReturn(baseline);
        when(resourceMapper.selectList(any())).thenReturn(List.of(resource));
        when(riskMapper.selectList(any())).thenReturn(List.of(risk));
        when(baselineMapper.updateById(any(ProjectNodePlanBaselineDO.class))).thenReturn(1);

        var result = service.confirm(1L, 10L);

        assertThat(result.getBaselineStatus()).isEqualTo(1);
        verify(operationLogService).record(org.mockito.ArgumentMatchers.argThat(event ->
                AuditAction.NODE_PLAN_RESOURCE_RISK_CONFIRMED.name().equals(event.action())
                        && Long.valueOf(10L).equals(event.resourceId())
                && Long.valueOf(1L).equals(event.projectId())));
    }

    @Test
    void rejectsConfirmationWhenTheSolutionDecisionVersionChanged() {
        ProjectNodeDO node = node("plan");
        ProjectNodePlanBaselineDO baseline = baseline();
        baseline.setSolutionDecisionVersion(1);
        ProjectNodeSolutionDecisionDO decision = confirmedSolutionDecision();
        decision.setVersion(2);
        when(permissionService.requireManageableNode(1L, 10L, "确认计划、资源与风险基线")).thenReturn(node);
        when(baselineMapper.selectOne(any())).thenReturn(baseline);
        when(nodeMapper.selectOne(any())).thenReturn(node("design"));
        when(decisionMapper.selectOne(any())).thenReturn(decision);

        assertThatThrownBy(() -> service.confirm(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("方案决策已变更");
        verify(baselineMapper, never()).updateById(any(ProjectNodePlanBaselineDO.class));
    }

    @Test
    void requiresConfirmedPlanBaselineForNodeCompletion() {
        when(permissionService.requireProjectReadable(1L)).thenReturn(null);
        when(permissionService.requireNode(1L, 10L)).thenReturn(node("plan"));
        when(baselineMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.requireConfirmed(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("确认计划、资源与风险基线");
    }

    @Test
    void mapsConcurrentBaselineCreationToConflict() {
        ProjectNodeDO node = node("plan");
        when(permissionService.requireManageableNode(1L, 10L, "保存计划、资源与风险基线")).thenReturn(node);
        when(baselineMapper.selectOne(any())).thenReturn(null);
        when(baselineMapper.insert(any(ProjectNodePlanBaselineDO.class))).thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> service.saveDraft(1L, 10L, updateCommand()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被其他人创建");
    }

    @Test
    void rejectsRemovingAnIterationPlanUsedByAStory() {
        ProjectNodeDO node = node("plan");
        when(permissionService.requireManageableNode(1L, 10L, "保存计划、资源与风险基线")).thenReturn(node);
        when(baselineMapper.selectOne(any())).thenReturn(baseline());
        when(baselineMapper.updateById(any(ProjectNodePlanBaselineDO.class))).thenReturn(1);
        when(storyMapper.selectCount(any())).thenReturn(1L);

        NodePlanResourceRiskUpdateCmd cmd = updateCommand();
        cmd.setIterationPlans(List.of());

        assertThatThrownBy(() -> service.saveDraft(1L, 10L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被故事使用");
    }

    private ProjectNodeDO node(String key) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setNodeKey(key);
        node.setStatus(1);
        return node;
    }

    private ProjectNodePlanBaselineDO baseline() {
        ProjectNodePlanBaselineDO baseline = new ProjectNodePlanBaselineDO();
        baseline.setId(100L);
        baseline.setProjectId(1L);
        baseline.setNodeId(10L);
        baseline.setStatus(0);
        baseline.setVersion(0);
        baseline.setSolutionDecisionVersion(1);
        return baseline;
    }

    private ProjectNodeSolutionDecisionDO confirmedSolutionDecision() {
        ProjectNodeSolutionDecisionDO decision = new ProjectNodeSolutionDecisionDO();
        decision.setId(200L);
        decision.setProjectId(1L);
        decision.setNodeId(20L);
        decision.setStatus("CONFIRMED");
        decision.setVersion(1);
        return decision;
    }

    private NodePlanResourceRiskUpdateCmd updateCommand() {
        NodeResourceCmd resource = new NodeResourceCmd();
        resource.setRole("技术负责人");
        resource.setOwnerId(21L);
        resource.setFocus(" 接口梳理 ");
        resource.setStatus("CONFIRMED");

        NodeRiskCmd risk = new NodeRiskCmd();
        risk.setTitle(" 历史数据质量不稳定 ");
        risk.setLevel("HIGH");
        risk.setOwnerId(21L);
        risk.setResponse(" 先做样本核验 ");
        risk.setStatus("OPEN");

        NodePlanResourceRiskUpdateCmd cmd = new NodePlanResourceRiskUpdateCmd();
        cmd.setVersion(0);
        cmd.setResources(List.of(resource));
        cmd.setRisks(List.of(risk));
        NodeIterationPlanCmd iterationPlan = new NodeIterationPlanCmd();
        iterationPlan.setName("第一迭代");
        iterationPlan.setOwnerId(21L);
        iterationPlan.setGoal("完成主流程");
        iterationPlan.setStatus("PLANNED");
        iterationPlan.setStartDate(LocalDate.of(2026, 9, 1));
        iterationPlan.setDueDate(LocalDate.of(2026, 9, 14));
        cmd.setIterationPlans(List.of(iterationPlan));
        return cmd;
    }

    private ProjectNodeIterationPlanDO iterationPlan() {
        ProjectNodeIterationPlanDO item = new ProjectNodeIterationPlanDO();
        item.setId(300L);
        item.setProjectId(1L);
        item.setNodeId(10L);
        item.setName("第一迭代");
        item.setOwnerId(21L);
        item.setStartDate(LocalDate.of(2026, 9, 1));
        item.setDueDate(LocalDate.of(2026, 9, 14));
        item.setStatus("PLANNED");
        return item;
    }
}
