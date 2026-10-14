package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectMemberMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.security.DataScopeResolver;
import com.brad.pms.security.AuthorizationService;
import com.brad.pms.workflow.WorkflowComponentKey;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import com.brad.pms.workflow.WorkflowTemplateDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Context-only component checks never resolve the current default template. */
@ExtendWith(MockitoExtension.class)
class ProjectPermissionContextReadTest {

    @Mock ProjectMapper projectMapper;
    @Mock ProjectMemberMapper memberMapper;
    @Mock ProjectNodeMapper nodeMapper;
    @Mock DataScopeResolver dataScopeResolver;
    @Mock AuthorizationService authorizationService;
    @Mock WorkflowTemplateService workflowTemplateService;
    @Mock WorkflowComponentBindingService workflowComponentBindingService;

    @InjectMocks ProjectPermissionService permissionService;

    @Test
    void requireNodeComponentForContextResolvesThroughThePinnedVersion() {
        ProjectDO project = project(8L, 42L);
        ProjectNodeDO node = node(300L, 8L, "design");
        when(projectMapper.selectById(8L)).thenReturn(project);
        when(workflowTemplateService.getDefinitionForContext(42L)).thenReturn(new WorkflowTemplateDefinition(2,
                List.of(nodeDefinition("design", List.of(WorkflowComponentKey.SOLUTION_DESIGN)))));
        when(workflowComponentBindingService.applyTopicBinding(eq(project), any(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        permissionService.requireNodeComponentForContext(node, WorkflowComponentKey.SOLUTION_DESIGN, "缺少组件");

        verify(workflowTemplateService, never()).getNodeDefinition(any(), any());
        verify(workflowComponentBindingService, never()).applyLegacyTopicBinding(any(), any(), anyList());
    }

    @Test
    void requireNodeComponentForContextRejectsAMissingComponent() {
        ProjectDO project = project(8L, 42L);
        ProjectNodeDO node = node(300L, 8L, "design");
        when(projectMapper.selectById(8L)).thenReturn(project);
        when(workflowTemplateService.getDefinitionForContext(42L)).thenReturn(new WorkflowTemplateDefinition(2,
                List.of(nodeDefinition("design", List.of()))));
        when(workflowComponentBindingService.applyTopicBinding(eq(project), any(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        assertThatThrownBy(() -> permissionService.requireNodeComponentForContext(
                node, WorkflowComponentKey.SOLUTION_DESIGN, "节点不包含方案设计工作台"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("方案设计");
    }

    @Test
    void findNodeWithComponentForContextUsesTheStrictOverlayOnly() {
        ProjectDO project = project(8L, null);
        ProjectNodeDO host = node(300L, 8L, "develop");
        ProjectNodeDO other = node(301L, 8L, "kickoff");
        when(projectMapper.selectById(8L)).thenReturn(project);
        when(nodeMapper.selectList(any())).thenReturn(List.of(host, other));
        when(workflowTemplateService.getDefinitionForContext(null))
                .thenReturn(com.brad.pms.workflow.BuiltInWorkflowTemplate.compatibilityDefinition());
        // The binding service is mocked, so express the historical-mount component directly here.
        when(workflowComponentBindingService.applyLegacyTopicBinding(eq(8L), any(), anyList()))
                .thenReturn(new WorkflowTemplateDefinition(2,
                        List.of(nodeDefinition("develop", List.of(WorkflowComponentKey.DEVELOPMENT_CONTROL)),
                                nodeDefinition("kickoff", List.of()))));

        ProjectNodeDO found = permissionService.findNodeWithComponentForContext(
                8L, WorkflowComponentKey.DEVELOPMENT_CONTROL);

        assertThat(found).isSameAs(host);
        verify(workflowComponentBindingService, never()).applyTopicBinding(any(), any(), anyList());
    }

    private static ProjectDO project(Long id, Long versionId) {
        ProjectDO project = new ProjectDO();
        project.setId(id);
        project.setWorkflowTemplateVersionId(versionId);
        return project;
    }

    private static ProjectNodeDO node(Long id, Long projectId, String key) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(id);
        node.setProjectId(projectId);
        node.setNodeKey(key);
        return node;
    }

    private static WorkflowNodeDefinition nodeDefinition(String key, List<String> components) {
        return new WorkflowNodeDefinition(key, key, "", "", "", components, List.of(), false, List.of());
    }
}
