package com.brad.pms.service;

import com.brad.pms.dto.response.ProjectNodeDTO;
import com.brad.pms.dto.response.WorkflowNodeFieldValuesDTO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectLifecycleLogMapper;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectMemberMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import com.brad.pms.workflow.WorkflowTemplateDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Read-only workflow entries used by the AI context: no owner backfill, no node activation, no member writes. */
@ExtendWith(MockitoExtension.class)
class NodeServiceReadOnlyTest {

    @Mock ProjectNodeMapper nodeMapper;
    @Mock ProjectMapper projectMapper;
    @Mock ProjectMemberMapper memberMapper;
    @Mock UserService userService;
    @Mock ProjectPermissionService permissionService;
    @Mock ProjectLifecycleLogMapper lifecycleLogMapper;
    @Mock ProjectTaskMapper taskMapper;
    @Mock OperationLogService operationLogService;
    @Mock NodeRequirementScopeService requirementScopeService;
    @Mock NodeSolutionDesignService solutionDesignService;
    @Mock NodePlanResourceRiskService planResourceRiskService;
    @Mock NodeAcceptanceService acceptanceService;
    @Mock NodeDevelopmentControlService developmentControlService;
    @Mock NodeReleaseService releaseService;
    @Mock NodeValueReviewService valueReviewService;
    @Mock MemberService memberService;
    @Mock NotificationService notificationService;
    @Mock WorkflowTemplateService workflowTemplateService;
    @Mock WorkflowComponentBindingService workflowComponentBindingService;
    @Mock NodeCustomFieldService nodeCustomFieldService;

    NodeService nodeService;

    @org.junit.jupiter.api.BeforeEach
    void initService() {
        nodeService = new NodeService(nodeMapper, projectMapper, memberMapper, userService, permissionService,
                lifecycleLogMapper, taskMapper, operationLogService, requirementScopeService, solutionDesignService,
                planResourceRiskService, acceptanceService, developmentControlService, releaseService,
                valueReviewService, memberService);
        nodeService.setNotificationService(null);
        nodeService.setWorkflowTemplateService(workflowTemplateService);
        nodeService.setWorkflowComponentBindingService(workflowComponentBindingService);
        nodeService.setNodeCustomFieldService(nodeCustomFieldService);
    }

    @Test
    void listReadOnlyReturnsActualNodesWithoutDefaultOwnersActivationOrMembers() {
        ProjectDO project = new ProjectDO();
        project.setId(8L);
        project.setWorkflowTemplateVersionId(42L);
        ProjectNodeDO staged = node(301L, "kickoff", 0);
        staged.setStatus(0);
        ProjectNodeDO active = node(299L, "design", 1);
        active.setStatus(1);
        when(permissionService.requireProject(8L)).thenReturn(project);
        when(nodeMapper.selectList(any())).thenReturn(List.of(staged, active));
        when(workflowTemplateService.getDefinitionForContext(42L)).thenReturn(new WorkflowTemplateDefinition(2,
                List.of(nodeDefinition("kickoff"), nodeDefinition("design"))));
        when(workflowComponentBindingService.applyTopicBinding(any(), any(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(userService.listByIds(anyList())).thenReturn(List.of());
        when(nodeCustomFieldService.getValuesForNode(any(), any(), any()))
                .thenReturn(new WorkflowNodeFieldValuesDTO());

        List<ProjectNodeDTO> nodes = nodeService.listReadOnly(8L);

        assertThat(nodes).extracting(ProjectNodeDTO::getId).containsExactly(301L, 299L);
        assertThat(nodes.get(0).getStatus()).isZero();
        verify(nodeMapper, never()).updateById(any(ProjectNodeDO.class));
        verify(nodeMapper, never()).insert(any(ProjectNodeDO.class));
        verify(memberService, never()).ensureMember(any(), any());
        verify(userService, never()).listByIdsIncludingDeleted(anyList());
    }

    @Test
    void listReadOnlyKeepsHistoricalOverlayReadOnly() {
        ProjectDO project = new ProjectDO();
        project.setId(8L);
        project.setWorkflowTemplateVersionId(null);
        ProjectNodeDO host = node(301L, "develop", 0);
        ProjectNodeDO other = node(302L, "kickoff", 1);
        when(permissionService.requireProject(8L)).thenReturn(project);
        when(nodeMapper.selectList(any())).thenReturn(List.of(host, other));
        when(workflowTemplateService.getDefinitionForContext(null))
                .thenReturn(com.brad.pms.workflow.BuiltInWorkflowTemplate.compatibilityDefinition());
        when(userService.listByIds(anyList())).thenReturn(List.of());
        when(nodeCustomFieldService.getValuesForNode(any(), any(), any()))
                .thenReturn(new WorkflowNodeFieldValuesDTO());
        when(workflowComponentBindingService.applyLegacyTopicBinding(eq(8L), any(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        List<ProjectNodeDTO> nodes = nodeService.listReadOnly(8L);

        assertThat(nodes).hasSize(2);
        verify(workflowComponentBindingService, org.mockito.Mockito.times(1)).applyLegacyTopicBinding(
                eq(8L), any(WorkflowTemplateDefinition.class), anyList());
        verify(workflowComponentBindingService, never()).applyTopicBinding(any(), any(), anyList());
        verify(nodeMapper, never()).updateById(any(ProjectNodeDO.class));
    }

    private static ProjectNodeDO node(Long id, String key, int sort) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(id);
        node.setProjectId(8L);
        node.setNodeKey(key);
        node.setSort(sort);
        node.setStatus(1);
        node.setVersion(0);
        return node;
    }

    private static WorkflowNodeDefinition nodeDefinition(String key) {
        return new WorkflowNodeDefinition(key, key, null, null, null,
                List.of("project-basic-info"), List.of(), false, List.of(), null);
    }
}
