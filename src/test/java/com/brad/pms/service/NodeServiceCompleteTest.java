package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.mapper.ProjectLifecycleLogMapper;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectMemberMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.workflow.WorkflowFieldDefinition;
import com.brad.pms.workflow.WorkflowFieldType;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeServiceCompleteTest {

    @Mock ProjectNodeMapper nodeMapper;
    @Mock ProjectMapper projectMapper;
    @Mock ProjectMemberMapper memberMapper;
    @Mock UserService userService;
    @Mock ProjectPermissionService permissionService;
    @Mock ProjectLifecycleLogMapper lifecycleLogMapper;
    @Mock ProjectTaskMapper taskMapper;
    @Mock NotificationService notificationService;
    @Mock OperationLogService operationLogService;
    @Mock NodeSolutionDesignService solutionDesignService;
    @Mock NodePlanResourceRiskService planResourceRiskService;
    @Mock NodeAcceptanceService acceptanceService;
    @Mock NodeDevelopmentControlService developmentControlService;
    @Mock NodeValueReviewService valueReviewService;
    @Mock MemberService memberService;
    @Mock WorkflowTemplateService workflowTemplateService;
    @Mock NodeCustomFieldService nodeCustomFieldService;

    @InjectMocks NodeService nodeService;

    @Test
    void rejectsCompleteWhenTheNodeStillHasUnfinishedTasks() {
        ProjectDO project = new ProjectDO();
        project.setId(1L);
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setNodeKey("develop");
        node.setOwnerId(3L);
        node.setStatus(1);
        when(permissionService.requireProject(1L)).thenReturn(project);
        when(permissionService.requireCompletableNode(1L, 10L)).thenReturn(node);
        when(taskMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> nodeService.complete(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未完成任务");
        verify(nodeMapper, never()).updateById(node);
    }

    @Test
    void requiresAConfirmedSolutionDecisionBeforeCompletingDesignNode() {
        ProjectDO project = new ProjectDO();
        project.setId(1L);
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setNodeKey("design");
        node.setOwnerId(3L);
        node.setStatus(1);
        when(permissionService.requireProject(1L)).thenReturn(project);
        when(permissionService.requireCompletableNode(1L, 10L)).thenReturn(node);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        org.mockito.Mockito.doThrow(BusinessException.error("请先确认方案决策"))
                .when(solutionDesignService).requireConfirmed(1L, 10L);

        assertThatThrownBy(() -> nodeService.complete(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("方案决策");
        verify(nodeMapper, never()).updateById(node);
    }

    @Test
    void requiresAConfirmedPlanBaselineBeforeCompletingPlanNode() {
        ProjectDO project = new ProjectDO();
        project.setId(1L);
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setNodeKey("plan");
        node.setOwnerId(3L);
        node.setStatus(1);
        when(permissionService.requireProject(1L)).thenReturn(project);
        when(permissionService.requireCompletableNode(1L, 10L)).thenReturn(node);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        org.mockito.Mockito.doThrow(BusinessException.error("请先确认计划、资源与风险基线"))
                .when(planResourceRiskService).requireConfirmed(1L, 10L);

        assertThatThrownBy(() -> nodeService.complete(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("计划、资源与风险基线");
        verify(nodeMapper, never()).updateById(node);
    }

    @Test
    void requiresConfirmedAcceptanceBeforeCompletingAcceptanceNode() {
        ProjectDO project = new ProjectDO();
        project.setId(1L);
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setNodeKey("acceptance");
        node.setOwnerId(3L);
        node.setStatus(1);
        when(permissionService.requireProject(1L)).thenReturn(project);
        when(permissionService.requireCompletableNode(1L, 10L)).thenReturn(node);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        org.mockito.Mockito.doThrow(BusinessException.error("请先确认业务验收与缺陷闭环"))
                .when(acceptanceService).requireConfirmed(1L, 10L);

        assertThatThrownBy(() -> nodeService.complete(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("业务验收");
        verify(nodeMapper, never()).updateById(node);
    }

    @Test
    void requiresDevelopmentControlCompletionBeforeCompletingDevelopmentNode() {
        ProjectDO project = new ProjectDO();
        project.setId(1L);
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setNodeKey("develop");
        node.setOwnerId(3L);
        node.setStatus(1);
        when(permissionService.requireProject(1L)).thenReturn(project);
        when(permissionService.requireCompletableNode(1L, 10L)).thenReturn(node);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        org.mockito.Mockito.doThrow(BusinessException.error("请先完成开发控制工作台"))
                .when(developmentControlService).requireCompleted(1L, 10L);

        assertThatThrownBy(() -> nodeService.complete(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("开发控制工作台");
        verify(nodeMapper, never()).updateById(node);
    }

    @Test
    void requiresValueReviewCompletionBeforeCompletingReviewNode() {
        ProjectDO project = new ProjectDO();
        project.setId(1L);
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setNodeKey("review");
        node.setOwnerId(3L);
        node.setStatus(1);
        when(permissionService.requireProject(1L)).thenReturn(project);
        when(permissionService.requireCompletableNode(1L, 10L)).thenReturn(node);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        org.mockito.Mockito.doThrow(BusinessException.error("请先完成价值验证与项目复盘"))
                .when(valueReviewService).requireCompleted(1L, 10L);

        assertThatThrownBy(() -> nodeService.complete(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("价值验证");
        verify(nodeMapper, never()).updateById(node);
    }

    @Test
    void routesSpecializedCompletionByAttachedComponentAfterNodeRename() {
        ProjectDO project = new ProjectDO();
        project.setId(1L);
        project.setWorkflowTemplateVersionId(17L);
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setNodeKey("custom-solution-stage");
        node.setOwnerId(3L);
        node.setStatus(1);
        when(permissionService.requireProject(1L)).thenReturn(project);
        when(permissionService.requireCompletableNode(1L, 10L)).thenReturn(node);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        when(workflowTemplateService.getNodeDefinition(17L, "custom-solution-stage"))
                .thenReturn(new WorkflowNodeDefinition("custom-solution-stage", "自定义方案节点", "说明", "交付物", "角色",
                        java.util.List.of("solution-design"), java.util.List.of(), false, java.util.List.of()));
        nodeService.setWorkflowTemplateService(workflowTemplateService);
        org.mockito.Mockito.doThrow(BusinessException.error("请先确认方案决策"))
                .when(solutionDesignService).requireConfirmed(1L, 10L);

        assertThatThrownBy(() -> nodeService.complete(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("方案决策");
        verify(nodeMapper, never()).updateById(node);
    }

    @Test
    void blocksCompletionWhenARequiredCustomFieldIsMissing() {
        ProjectDO project = new ProjectDO();
        project.setId(1L);
        project.setWorkflowTemplateVersionId(17L);
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setNodeKey("custom-intake");
        node.setOwnerId(3L);
        node.setStatus(1);
        var definition = new WorkflowNodeDefinition("custom-intake", "自定义登记", "说明", "交付物", "角色",
                java.util.List.of(), java.util.List.of(
                new com.brad.pms.workflow.WorkflowFieldDefinition("business-case", "业务价值",
                        com.brad.pms.workflow.WorkflowFieldType.TEXT, true, java.util.List.of())), false, java.util.List.of());
        when(permissionService.requireProject(1L)).thenReturn(project);
        when(permissionService.requireCompletableNode(1L, 10L)).thenReturn(node);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        when(workflowTemplateService.getNodeDefinition(17L, "custom-intake")).thenReturn(definition);
        org.mockito.Mockito.doThrow(BusinessException.error("请先填写业务价值"))
                .when(nodeCustomFieldService).requireRequiredFields(1L, 10L, definition);
        nodeService.setWorkflowTemplateService(workflowTemplateService);
        nodeService.setNodeCustomFieldService(nodeCustomFieldService);

        assertThatThrownBy(() -> nodeService.complete(1L, 10L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("业务价值");
        verify(nodeMapper, never()).updateById(node);
    }

    @Test
    void rejectsCompletionWhenTheBoundWorkflowHasNoDefinitionForTheNode() {
        ProjectDO project = new ProjectDO();
        project.setId(1L);
        project.setWorkflowTemplateVersionId(17L);
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setNodeKey("orphan-stage");
        node.setOwnerId(3L);
        node.setStatus(1);
        when(permissionService.requireProject(1L)).thenReturn(project);
        when(permissionService.requireCompletableNode(1L, 10L)).thenReturn(node);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        when(workflowTemplateService.getNodeDefinition(17L, "orphan-stage")).thenReturn(null);
        nodeService.setWorkflowTemplateService(workflowTemplateService);
        nodeService.setNotificationService(notificationService);

        assertThatThrownBy(() -> nodeService.complete(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("工作流配置缺失");
        verify(nodeMapper, never()).updateById(node);
    }

    @Test
    void blocksCompletionWhenAVisibleRequiredV2ProjectBindingIsMissingFromCanonicalProjectData() {
        ProjectDO project = new ProjectDO();
        project.setId(1L);
        project.setWorkflowTemplateVersionId(17L);
        ProjectNodeDO node = completableNode("custom-intake");
        WorkflowNodeDefinition definition = new WorkflowNodeDefinition("custom-intake", "自定义登记", "说明", "交付物", "角色",
                java.util.List.of(), java.util.List.of(new WorkflowFieldDefinition("project-description", "项目描述",
                WorkflowFieldType.TEXTAREA, true, java.util.List.of(), true, "project.description")),
                false, java.util.List.of(), java.util.List.of("fields"));
        when(permissionService.requireProject(1L)).thenReturn(project);
        when(permissionService.requireCompletableNode(1L, 10L)).thenReturn(node);
        when(workflowTemplateService.getNodeDefinition(17L, "custom-intake")).thenReturn(definition);
        when(projectMapper.selectById(1L)).thenReturn(project);
        nodeService.setWorkflowTemplateService(workflowTemplateService);
        nodeService.setNotificationService(notificationService);

        assertThatThrownBy(() -> nodeService.complete(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目描述");
        verify(nodeMapper, never()).updateById(node);
    }

    @Test
    void allowsCompletionWhenARequiredV2ProjectBindingIsHidden() {
        ProjectDO project = new ProjectDO();
        project.setId(1L);
        project.setWorkflowTemplateVersionId(17L);
        ProjectNodeDO node = completableNode("custom-intake");
        WorkflowNodeDefinition definition = new WorkflowNodeDefinition("custom-intake", "自定义登记", "说明", "交付物", "角色",
                java.util.List.of(), java.util.List.of(new WorkflowFieldDefinition("project-description", "项目描述",
                WorkflowFieldType.TEXTAREA, true, java.util.List.of(), false, "project.description")),
                false, java.util.List.of(), java.util.List.of("fields"));
        when(permissionService.requireProject(1L)).thenReturn(project);
        when(permissionService.requireCompletableNode(1L, 10L)).thenReturn(node);
        when(workflowTemplateService.getNodeDefinition(17L, "custom-intake")).thenReturn(definition);
        org.mockito.Mockito.lenient().when(projectMapper.selectById(1L)).thenReturn(project);
        when(taskMapper.selectCount(any())).thenReturn(1L);
        nodeService.setWorkflowTemplateService(workflowTemplateService);

        assertThatThrownBy(() -> nodeService.complete(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未完成任务");
    }

    @Test
    void doesNotTreatContentOrderAloneAsACanonicalProjectBinding() {
        ProjectDO project = new ProjectDO();
        project.setId(1L);
        project.setWorkflowTemplateVersionId(17L);
        ProjectNodeDO node = completableNode("custom-intake");
        WorkflowNodeDefinition definition = new WorkflowNodeDefinition("custom-intake", "自定义登记", "说明", "交付物", "角色",
                java.util.List.of(), java.util.List.of(new WorkflowFieldDefinition("note", "备注",
                WorkflowFieldType.TEXT, false, java.util.List.of(), true, null)),
                false, java.util.List.of(), java.util.List.of("fields"));
        when(permissionService.requireProject(1L)).thenReturn(project);
        when(permissionService.requireCompletableNode(1L, 10L)).thenReturn(node);
        when(workflowTemplateService.getNodeDefinition(17L, "custom-intake")).thenReturn(definition);
        when(taskMapper.selectCount(any())).thenReturn(1L);
        nodeService.setWorkflowTemplateService(workflowTemplateService);

        assertThatThrownBy(() -> nodeService.complete(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未完成任务");
    }

    @Test
    void exposesV2ContentOrderAndDerivesComponentsFromItsReferences() {
        ProjectDO project = new ProjectDO();
        project.setId(1L);
        project.setWorkflowTemplateVersionId(17L);
        ProjectNodeDO node = completableNode("custom-intake");
        node.setStatus(2);
        WorkflowNodeDefinition definition = new WorkflowNodeDefinition("custom-intake", "自定义登记", "说明", "交付物", "角色",
                null, java.util.List.of(), false, null,
                java.util.List.of("component:solution-design", "fields", "component:value-review"));
        when(permissionService.requireProject(1L)).thenReturn(project);
        when(nodeMapper.selectList(any())).thenReturn(java.util.List.of(node));
        when(userService.listByIds(java.util.List.of(3L))).thenReturn(java.util.List.of());
        when(workflowTemplateService.getNodeDefinition(17L, "custom-intake")).thenReturn(definition);
        nodeService.setWorkflowTemplateService(workflowTemplateService);

        var dto = nodeService.list(1L).get(0);

        assertThat(dto.getContentOrder()).containsExactly("component:solution-design", "fields", "component:value-review");
        assertThat(dto.getComponents()).containsExactly("solution-design", "value-review");
    }

    private static ProjectNodeDO completableNode(String key) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setNodeKey(key);
        node.setOwnerId(3L);
        node.setStatus(1);
        return node;
    }
}
