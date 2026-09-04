package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeBaselineDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeRequirementDO;
import com.brad.pms.entity.ProjectNodeScopeItemDO;
import com.brad.pms.mapper.ProjectNodeBaselineMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectNodeRequirementMapper;
import com.brad.pms.mapper.ProjectNodeScopeItemMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import com.brad.pms.dto.request.NodeRequirementScopeUpdateCmd;
import com.brad.pms.dto.request.NodeRequirementCmd;
import com.brad.pms.dto.request.NodeScopeItemCmd;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeRequirementScopeServiceTest {

    @Mock ProjectNodeBaselineMapper baselineMapper;
    @Mock ProjectNodeScopeItemMapper scopeItemMapper;
    @Mock ProjectNodeRequirementMapper requirementMapper;
    @Mock ProjectNodeMapper nodeMapper;
    @Mock ProjectPermissionService permissionService;

    @InjectMocks NodeRequirementScopeService service;

    @org.junit.jupiter.api.BeforeEach
    void initTableMetadata() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        TableInfoHelper.initTableInfo(assistant, ProjectNodeRequirementDO.class);
    }

    @Test
    void rejectsConfirmWhenTheBaselineIsIncomplete() {
        ProjectNodeDO node = requirementNode();
        ProjectNodeBaselineDO baseline = baseline();
        baseline.setObjective("明确本阶段要做什么");
        baseline.setDeliverable("可评审的需求基线");
        when(permissionService.requireManageableNode(1L, 10L, "确认需求范围基线")).thenReturn(node);
        when(baselineMapper.selectOne(any())).thenReturn(baseline);
        when(scopeItemMapper.selectList(any())).thenReturn(List.of());
        when(requirementMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.confirm(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少保留一条纳入范围项");
    }

    @Test
    void confirmsACompleteBaselineAndRecordsItsStatus() {
        ProjectDO project = project();
        ProjectNodeDO node = requirementNode();
        ProjectNodeBaselineDO baseline = baseline();
        baseline.setObjective("明确本阶段要做什么");
        baseline.setDeliverable("可评审的需求基线");
        ProjectNodeScopeItemDO scope = new ProjectNodeScopeItemDO();
        scope.setDirection("IN");
        scope.setTitle("订单状态流转");
        ProjectNodeRequirementDO requirement = new ProjectNodeRequirementDO();
        requirement.setCode("REQ-001");
        requirement.setName("统一订单状态");
        requirement.setAcceptanceCriteria("状态变更可追踪");
        requirement.setStatus(1);
        when(permissionService.requireManageableNode(1L, 10L, "确认需求范围基线")).thenReturn(node);
        when(baselineMapper.selectOne(any())).thenReturn(baseline);
        when(scopeItemMapper.selectList(any())).thenReturn(List.of(scope));
        when(requirementMapper.selectList(any())).thenReturn(List.of(requirement));
        when(baselineMapper.updateById(any(ProjectNodeBaselineDO.class))).thenReturn(1);

        var result = service.confirm(1L, 10L);

        assertThat(result.getBaselineStatus()).isEqualTo(1);
        assertThat(result.getConfirmedAt()).isNotNull();
        verify(baselineMapper).updateById(baseline);
    }

    @Test
    void reopeningKeepsTheExistingContentAndOnlyClearsConfirmation() {
        ProjectNodeDO node = requirementNode();
        ProjectNodeBaselineDO baseline = baseline();
        baseline.setStatus(1);
        when(permissionService.requireManageableNode(1L, 10L, "重新打开需求范围基线")).thenReturn(node);
        when(baselineMapper.selectOne(any())).thenReturn(baseline);
        when(baselineMapper.updateById(any(ProjectNodeBaselineDO.class))).thenReturn(1);

        var result = service.reopen(1L, 10L);

        assertThat(result.getBaselineStatus()).isEqualTo(0);
        assertThat(result.getConfirmedAt()).isNull();
        verify(baselineMapper).updateById(baseline);
        verify(requirementMapper).update(any(), any());
    }

    @Test
    void savesTheDraftContentAsAReplaceableNodeSnapshot() {
        ProjectNodeDO node = requirementNode();
        when(permissionService.requireManageableNode(1L, 10L, "保存需求范围基线")).thenReturn(node);
        when(baselineMapper.selectOne(any())).thenReturn(null);
        when(baselineMapper.updateById(any(ProjectNodeBaselineDO.class))).thenReturn(1);

        NodeRequirementScopeUpdateCmd cmd = new NodeRequirementScopeUpdateCmd();
        cmd.setObjective("明确本阶段要做什么");
        cmd.setDeliverable("可评审的需求基线");
        NodeScopeItemCmd scope = new NodeScopeItemCmd();
        scope.setDirection("IN");
        scope.setTitle("订单状态流转");
        cmd.setScopeItems(List.of(scope));
        NodeRequirementCmd requirement = new NodeRequirementCmd();
        requirement.setName("统一订单状态");
        requirement.setType("BUSINESS");
        requirement.setPriority(3);
        requirement.setAcceptanceCriteria("状态变更可追踪");
        requirement.setStatus(0);
        cmd.setRequirements(List.of(requirement));

        service.saveDraft(1L, 10L, cmd);

        verify(scopeItemMapper).delete(any());
        verify(requirementMapper).delete(any());
        verify(baselineMapper).insert(any(ProjectNodeBaselineDO.class));
        verify(scopeItemMapper).insert(any(ProjectNodeScopeItemDO.class));
        verify(requirementMapper).insert(any(ProjectNodeRequirementDO.class));
    }

    @Test
    void rejectsAStaleDraftBeforeReplacingItsChildren() {
        ProjectNodeDO node = requirementNode();
        ProjectNodeBaselineDO baseline = baseline();
        baseline.setVersion(2);
        when(permissionService.requireManageableNode(1L, 10L, "保存需求范围基线")).thenReturn(node);
        when(baselineMapper.selectOne(any())).thenReturn(baseline);

        NodeRequirementScopeUpdateCmd cmd = new NodeRequirementScopeUpdateCmd();
        cmd.setVersion(1);

        assertThatThrownBy(() -> service.saveDraft(1L, 10L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("其他人修改");

        verify(baselineMapper, never()).updateById(any(ProjectNodeBaselineDO.class));
        verify(scopeItemMapper, never()).delete(any());
        verify(requirementMapper, never()).delete(any());
    }

    @Test
    void resetsChangedRequirementsToPendingDuringDraftSave() {
        ProjectNodeDO node = requirementNode();
        ProjectNodeBaselineDO baseline = baseline();
        baseline.setVersion(1);
        ProjectNodeRequirementDO existing = new ProjectNodeRequirementDO();
        existing.setId(21L);
        existing.setCode("REQ-001");
        existing.setName("旧名称");
        existing.setType("BUSINESS");
        existing.setPriority(2);
        existing.setAcceptanceCriteria("旧验收标准");
        existing.setStatus(1);
        when(permissionService.requireManageableNode(1L, 10L, "保存需求范围基线")).thenReturn(node);
        when(baselineMapper.selectOne(any())).thenReturn(baseline);
        when(baselineMapper.updateById(any(ProjectNodeBaselineDO.class))).thenReturn(1);
        when(scopeItemMapper.selectList(any())).thenReturn(List.of());
        when(requirementMapper.selectList(any())).thenReturn(List.of(existing));

        NodeRequirementCmd requirement = new NodeRequirementCmd();
        requirement.setId(21L);
        requirement.setCode("REQ-001");
        requirement.setName("新名称");
        requirement.setType("BUSINESS");
        requirement.setPriority(2);
        requirement.setAcceptanceCriteria("旧验收标准");
        requirement.setStatus(1);
        NodeRequirementScopeUpdateCmd cmd = new NodeRequirementScopeUpdateCmd();
        cmd.setVersion(1);
        cmd.setRequirements(List.of(requirement));

        service.saveDraft(1L, 10L, cmd);

        ArgumentCaptor<ProjectNodeRequirementDO> captor = ArgumentCaptor.forClass(ProjectNodeRequirementDO.class);
        verify(requirementMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isZero();
    }

    private ProjectDO project() {
        ProjectDO project = new ProjectDO();
        project.setId(1L);
        project.setStatus(1);
        return project;
    }

    private ProjectNodeDO requirementNode() {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setNodeKey("requirement");
        node.setStatus(1);
        return node;
    }

    private ProjectNodeBaselineDO baseline() {
        ProjectNodeBaselineDO baseline = new ProjectNodeBaselineDO();
        baseline.setId(100L);
        baseline.setProjectId(1L);
        baseline.setNodeId(10L);
        baseline.setStatus(0);
        return baseline;
    }
}
