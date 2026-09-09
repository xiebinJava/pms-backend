package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodeAcceptanceItemCmd;
import com.brad.pms.dto.request.NodeAcceptanceUpdateCmd;
import com.brad.pms.entity.ProjectNodeAcceptanceBaselineDO;
import com.brad.pms.entity.ProjectNodeAcceptanceItemDO;
import com.brad.pms.entity.ProjectNodeBaselineDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeRequirementDO;
import com.brad.pms.mapper.ProjectNodeAcceptanceBaselineMapper;
import com.brad.pms.mapper.ProjectNodeAcceptanceDefectMapper;
import com.brad.pms.mapper.ProjectNodeAcceptanceItemMapper;
import com.brad.pms.mapper.ProjectNodeBaselineMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectNodeRequirementMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeAcceptanceServiceTest {

    @Mock ProjectNodeAcceptanceBaselineMapper baselineMapper;
    @Mock ProjectNodeAcceptanceItemMapper itemMapper;
    @Mock ProjectNodeAcceptanceDefectMapper defectMapper;
    @Mock ProjectNodeMapper nodeMapper;
    @Mock ProjectNodeBaselineMapper requirementBaselineMapper;
    @Mock ProjectNodeRequirementMapper requirementMapper;
    @Mock ProjectPermissionService permissionService;
    @Mock UserService userService;
    @Mock OperationLogService operationLogService;

    @InjectMocks NodeAcceptanceService service;

    @Test
    void rejectsNonAcceptanceNodes() {
        when(permissionService.requireNode(1L, 10L)).thenReturn(node("plan"));

        assertThatThrownBy(() -> service.get(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("业务验收与缺陷闭环");
    }

    @Test
    void savesAcceptanceDraftAgainstConfirmedRequirements() {
        when(permissionService.requireManageableNode(1L, 10L, "保存业务验收与缺陷闭环")).thenReturn(node("acceptance"));
        when(nodeMapper.selectOne(any())).thenReturn(node("requirement"));
        when(requirementBaselineMapper.selectOne(any())).thenReturn(requirementBaseline());
        when(baselineMapper.selectOne(any())).thenReturn(null);
        when(baselineMapper.insert(any(ProjectNodeAcceptanceBaselineDO.class))).thenReturn(1);
        when(baselineMapper.updateById(any(ProjectNodeAcceptanceBaselineDO.class))).thenReturn(1);
        when(requirementMapper.selectList(any())).thenReturn(List.of(requirement(21L, "REQ-001")));
        when(itemMapper.insert(any(ProjectNodeAcceptanceItemDO.class))).thenReturn(1);
        when(itemMapper.selectList(any())).thenReturn(List.of());
        when(defectMapper.selectList(any())).thenReturn(List.of());

        service.saveDraft(1L, 10L, update("PASS", ""));

        ArgumentCaptor<ProjectNodeAcceptanceItemDO> itemCaptor = ArgumentCaptor.forClass(ProjectNodeAcceptanceItemDO.class);
        verify(itemMapper).insert(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getRequirementId()).isEqualTo(21L);
        assertThat(itemCaptor.getValue().getResult()).isEqualTo("PASS");
        verify(operationLogService).record(org.mockito.ArgumentMatchers.argThat(event ->
                AuditAction.NODE_ACCEPTANCE_DRAFT_SAVED.name().equals(event.action())
                        && Long.valueOf(10L).equals(event.resourceId())
                        && Long.valueOf(1L).equals(event.projectId())));
    }

    @Test
    void rejectsConfirmationWithIncompleteItemsOrMissingConditionalResiduals() {
        when(permissionService.requireManageableNode(1L, 10L, "确认业务验收与缺陷闭环")).thenReturn(node("acceptance"));
        when(nodeMapper.selectOne(any())).thenReturn(node("requirement"));
        when(requirementBaselineMapper.selectOne(any())).thenReturn(requirementBaseline());
        when(baselineMapper.selectOne(any())).thenReturn(baseline());
        when(requirementMapper.selectList(any())).thenReturn(List.of(requirement(21L, "REQ-001")));
        ProjectNodeAcceptanceItemDO item = new ProjectNodeAcceptanceItemDO();
        item.setRequirementId(21L);
        item.setResult("PENDING");
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(defectMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.confirm(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验收项");
        verify(baselineMapper, never()).updateById(any(ProjectNodeAcceptanceBaselineDO.class));
    }

    @Test
    void confirmsConditionalAcceptanceAndAuditsIt() {
        when(permissionService.requireManageableNode(1L, 10L, "确认业务验收与缺陷闭环")).thenReturn(node("acceptance"));
        when(nodeMapper.selectOne(any())).thenReturn(node("requirement"));
        when(requirementBaselineMapper.selectOne(any())).thenReturn(requirementBaseline());
        when(baselineMapper.selectOne(any())).thenReturn(baseline());
        when(requirementMapper.selectList(any())).thenReturn(List.of(requirement(21L, "REQ-001")));
        ProjectNodeAcceptanceItemDO item = new ProjectNodeAcceptanceItemDO();
        item.setRequirementId(21L);
        item.setResult("PASS");
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(defectMapper.selectList(any())).thenReturn(List.of());
        when(baselineMapper.updateById(any(ProjectNodeAcceptanceBaselineDO.class))).thenReturn(1);

        ProjectNodeAcceptanceBaselineDO baseline = baseline();
        baseline.setResult("CONDITIONAL_PASS");
        baseline.setResidualItems("上线后观察支付超时指标");
        when(this.baselineMapper.selectOne(any())).thenReturn(baseline);

        var result = service.confirm(1L, 10L);

        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(result.getResult()).isEqualTo("CONDITIONAL_PASS");
        verify(operationLogService).record(org.mockito.ArgumentMatchers.argThat(event ->
                AuditAction.NODE_ACCEPTANCE_CONFIRMED.name().equals(event.action())
                        && Long.valueOf(10L).equals(event.resourceId())));
    }

    @Test
    void editsAConfirmedAcceptanceDirectlyAndClearsItsConfirmation() {
        when(permissionService.requireManageableNode(1L, 10L, "保存业务验收与缺陷闭环")).thenReturn(node("acceptance"));
        when(nodeMapper.selectOne(any())).thenReturn(node("requirement"));
        when(requirementBaselineMapper.selectOne(any())).thenReturn(requirementBaseline());
        when(baselineMapper.selectOne(any())).thenReturn(confirmedAcceptanceBaseline());
        when(requirementMapper.selectList(any())).thenReturn(List.of(requirement(21L, "REQ-001")));
        when(itemMapper.selectList(any())).thenReturn(List.of());
        when(defectMapper.selectList(any())).thenReturn(List.of());
        when(baselineMapper.updateById(any(ProjectNodeAcceptanceBaselineDO.class))).thenReturn(1);

        var result = service.saveDraft(1L, 10L, update("PASS", ""));

        assertThat(result.getStatus()).isZero();
        ArgumentCaptor<ProjectNodeAcceptanceBaselineDO> captor = ArgumentCaptor.forClass(ProjectNodeAcceptanceBaselineDO.class);
        verify(baselineMapper).updateById(captor.capture());
        assertThat(captor.getValue().getConfirmedAt()).isNull();
    }

    @Test
    void rejectsConfirmingLockedAcceptanceWithoutAContentChange() {
        when(permissionService.requireManageableNode(1L, 10L, "确认业务验收与缺陷闭环")).thenReturn(node("acceptance"));
        ProjectNodeAcceptanceBaselineDO locked = baseline();
        locked.setStatus(1);
        locked.setRequirementBaselineVersion(2);
        when(baselineMapper.selectOne(any())).thenReturn(locked);

        assertThatThrownBy(() -> service.confirm(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已确认");
        verify(baselineMapper, never()).updateById(any(ProjectNodeAcceptanceBaselineDO.class));
    }

    private ProjectNodeAcceptanceBaselineDO confirmedAcceptanceBaseline() {
        ProjectNodeAcceptanceBaselineDO baseline = baseline();
        baseline.setStatus(1);
        return baseline;
    }

    @Test
    void rejectsCompletionWhenRequirementBaselineVersionChanged() {
        when(permissionService.requireProjectReadable(1L)).thenReturn(null);
        when(permissionService.requireNode(1L, 10L)).thenReturn(node("acceptance"));
        when(nodeMapper.selectOne(any())).thenReturn(node("requirement"));
        ProjectNodeAcceptanceBaselineDO accepted = baseline();
        accepted.setStatus(1);
        accepted.setRequirementBaselineVersion(1);
        when(baselineMapper.selectOne(any())).thenReturn(accepted);
        ProjectNodeBaselineDO currentRequirementBaseline = requirementBaseline();
        currentRequirementBaseline.setVersion(2);
        when(requirementBaselineMapper.selectOne(any())).thenReturn(currentRequirementBaseline);

        assertThatThrownBy(() -> service.requireConfirmed(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("需求范围基线已变更");
    }

    @Test
    void rejectsOversizedAcceptanceNotesBeforeWriting() {
        when(permissionService.requireManageableNode(1L, 10L, "保存业务验收与缺陷闭环")).thenReturn(node("acceptance"));
        when(nodeMapper.selectOne(any())).thenReturn(node("requirement"));
        when(requirementBaselineMapper.selectOne(any())).thenReturn(requirementBaseline());
        when(requirementMapper.selectList(any())).thenReturn(List.of(requirement(21L, "REQ-001")));
        NodeAcceptanceItemCmd item = new NodeAcceptanceItemCmd();
        item.setRequirementId(21L);
        item.setResult("PASS");
        item.setNote("x".repeat(1001));
        NodeAcceptanceUpdateCmd cmd = new NodeAcceptanceUpdateCmd();
        cmd.setItems(List.of(item));

        assertThatThrownBy(() -> service.saveDraft(1L, 10L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验收记录不能超过");
        verify(baselineMapper, never()).insert(any(ProjectNodeAcceptanceBaselineDO.class));
    }

    @Test
    void rejectsNullAcceptanceItemsBeforeWriting() {
        when(permissionService.requireManageableNode(1L, 10L, "保存业务验收与缺陷闭环")).thenReturn(node("acceptance"));
        when(nodeMapper.selectOne(any())).thenReturn(node("requirement"));
        when(requirementBaselineMapper.selectOne(any())).thenReturn(requirementBaseline());
        when(requirementMapper.selectList(any())).thenReturn(List.of(requirement(21L, "REQ-001")));
        NodeAcceptanceUpdateCmd cmd = new NodeAcceptanceUpdateCmd();
        cmd.setItems(Arrays.asList((NodeAcceptanceItemCmd) null));

        assertThatThrownBy(() -> service.saveDraft(1L, 10L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验收项不能为空");
        verify(baselineMapper, never()).insert(any(ProjectNodeAcceptanceBaselineDO.class));
    }

    @Test
    void requiresConfirmedAcceptanceForNodeCompletion() {
        when(permissionService.requireProjectReadable(1L)).thenReturn(null);
        when(permissionService.requireNode(1L, 10L)).thenReturn(node("acceptance"));
        when(baselineMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.requireConfirmed(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("确认业务验收");
    }

    private ProjectNodeDO node(String key) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setNodeKey(key);
        node.setStatus(1);
        return node;
    }

    private ProjectNodeRequirementDO requirement(Long id, String code) {
        ProjectNodeRequirementDO requirement = new ProjectNodeRequirementDO();
        requirement.setId(id);
        requirement.setProjectId(1L);
        requirement.setNodeId(2L);
        requirement.setCode(code);
        requirement.setName("支付流程");
        requirement.setAcceptanceCriteria("支付成功可查询");
        requirement.setStatus(1);
        return requirement;
    }

    private ProjectNodeAcceptanceBaselineDO baseline() {
        ProjectNodeAcceptanceBaselineDO baseline = new ProjectNodeAcceptanceBaselineDO();
        baseline.setId(100L);
        baseline.setProjectId(1L);
        baseline.setNodeId(10L);
        baseline.setStatus(0);
        baseline.setVersion(0);
        baseline.setRequirementBaselineVersion(2);
        return baseline;
    }

    private ProjectNodeBaselineDO requirementBaseline() {
        ProjectNodeBaselineDO baseline = new ProjectNodeBaselineDO();
        baseline.setId(200L);
        baseline.setProjectId(1L);
        baseline.setNodeId(2L);
        baseline.setStatus(1);
        baseline.setVersion(2);
        return baseline;
    }

    private NodeAcceptanceUpdateCmd update(String result, String residualItems) {
        NodeAcceptanceItemCmd item = new NodeAcceptanceItemCmd();
        item.setRequirementId(21L);
        item.setResult(result);
        item.setNote("支付成功后验证查询");
        NodeAcceptanceUpdateCmd cmd = new NodeAcceptanceUpdateCmd();
        cmd.setVersion(0);
        cmd.setResult(result);
        cmd.setResidualItems(residualItems);
        cmd.setItems(List.of(item));
        return cmd;
    }
}
