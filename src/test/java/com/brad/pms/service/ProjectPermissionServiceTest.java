package com.brad.pms.service;

import com.brad.pms.common.enums.ProjectStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.response.ProjectPermissionsDTO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectMemberMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.security.AuthorizationService;
import com.brad.pms.security.DataScopeResolver;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.UserContext;
import com.brad.pms.security.LoginUser;
import com.brad.pms.workflow.WorkflowComponentKey;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import com.brad.pms.workflow.WorkflowTemplateDefinition;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectPermissionServiceTest {
    @Mock ProjectMapper projectMapper;
    @Mock ProjectMemberMapper memberMapper;
    @Mock ProjectNodeMapper nodeMapper;
    @Mock DataScopeResolver dataScopeResolver;
    @Mock AuthorizationService authorizationService;
    @Mock WorkflowTemplateService workflowTemplateService;

    @BeforeEach
    void initNodeLambdaMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "permission-test"), ProjectNodeDO.class);
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void ordinaryReaderCannotDiscoverDeletedProject() {
        UserContext.set(new LoginUser(7L, "member", "成员", 0));
        ProjectDO deleted = project(ProjectStatus.DELETED.getCode(), 42L, 10L, 20L);
        when(projectMapper.selectById(10L)).thenReturn(deleted);

        assertThatThrownBy(() -> service().requireProjectReadable(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不存在");
    }

    @Test
    void deletedProjectUsesNotFoundCodeForOrdinaryRead() {
        UserContext.set(new LoginUser(7L, "member", "成员", 0));
        ProjectDO deleted = project(ProjectStatus.DELETED.getCode(), 42L, 10L, 20L);
        when(projectMapper.selectById(10L)).thenReturn(deleted);

        assertThatThrownBy(() -> service().requireProjectReadable(10L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(404));
    }

    @Test
    void creatorAndProjectManagerReceiveTheSameProjectCapabilities() {
        ProjectDO project = project(ProjectStatus.ACTIVE.getCode(), 42L, 7L, 8L);
        when(authorizationService.has(PermissionCode.PROJECT_COMMENT_WRITE)).thenReturn(true);

        UserContext.set(new LoginUser(7L, "creator", "创建人", 0));
        ProjectPermissionsDTO creator = service().projectPermissions(project);
        UserContext.set(new LoginUser(8L, "manager", "项目经理", 0));
        ProjectPermissionsDTO manager = service().projectPermissions(project);

        assertThat(manager).usingRecursiveComparison().isEqualTo(creator);
        assertThat(creator.isCanManageProject()).isTrue();
        assertThat(creator.isCanManageMembers()).isTrue();
        assertThat(creator.isCanTerminateProject()).isTrue();
        assertThat(creator.isCanDeleteProject()).isTrue();
    }

    @Test
    void organizationGovernanceMustStayInsideItsPermissionScope() {
        UserContext.set(new LoginUser(9L, "scoped-governance", "范围治理角色", 0));
        ProjectDO project = project(ProjectStatus.ACTIVE.getCode(), 42L, 7L, 8L);
        when(projectMapper.selectById(10L)).thenReturn(project);
        when(authorizationService.has(PermissionCode.PROJECT_READ)).thenReturn(true);
        when(authorizationService.has(PermissionCode.PROJECT_MANAGE)).thenReturn(true);
        when(dataScopeResolver.hasAllCompanyScope(UserContext.get(), PermissionCode.PROJECT_MANAGE)).thenReturn(false);
        when(dataScopeResolver.resolveOrgUnitIds(UserContext.get(), PermissionCode.PROJECT_MANAGE)).thenReturn(List.of(42L));

        assertThat(service().requireProjectManageable(10L, "编辑项目")).isSameAs(project);
    }

    @Test
    void completedProjectCanEnterTheRollbackPathForItsProjectManager() {
        UserContext.set(new LoginUser(8L, "manager", "项目经理", 0));
        ProjectDO project = project(ProjectStatus.COMPLETED.getCode(), 42L, 7L, 8L);
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(11L);
        node.setProjectId(10L);
        node.setStatus(2);
        when(projectMapper.selectById(10L)).thenReturn(project);
        when(nodeMapper.selectById(11L)).thenReturn(node);
        when(authorizationService.has(PermissionCode.PROJECT_READ)).thenReturn(true);

        assertThat(service().requireRollbackableNode(10L, 11L)).isSameAs(node);
    }

    @Test
    void configuredComponentCanUseACustomNodeKey() {
        ProjectDO project = project(ProjectStatus.ACTIVE.getCode(), 42L, 7L, 8L);
        project.setWorkflowTemplateVersionId(15L);
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(11L);
        node.setProjectId(10L);
        node.setNodeKey("custom-review");
        when(projectMapper.selectById(10L)).thenReturn(project);
        when(workflowTemplateService.getNodeDefinition(15L, "custom-review")).thenReturn(
                new WorkflowNodeDefinition("custom-review", "Custom review", "", "", "", List.of("solution-design"),
                        List.of(), false, List.of()));

        service().requireNodeComponent(node, WorkflowComponentKey.SOLUTION_DESIGN, "missing component");
    }

    @Test
    void componentGuardRejectsNodeWithoutTheConfiguredWorkbench() {
        ProjectDO project = project(ProjectStatus.ACTIVE.getCode(), 42L, 7L, 8L);
        project.setWorkflowTemplateVersionId(15L);
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(11L);
        node.setProjectId(10L);
        node.setNodeKey("custom-review");
        when(projectMapper.selectById(10L)).thenReturn(project);
        when(workflowTemplateService.getNodeDefinition(15L, "custom-review")).thenReturn(
                new WorkflowNodeDefinition("custom-review", "Custom review", "", "", "", List.of(),
                        List.of(), false, List.of()));

        assertThatThrownBy(() -> service().requireNodeComponent(node, WorkflowComponentKey.SOLUTION_DESIGN, "missing component"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("missing component");
    }

    @Test
    void crossWorkbenchLookupFindsTheConfiguredCustomNodeKey() {
        ProjectDO project = project(ProjectStatus.ACTIVE.getCode(), 42L, 7L, 8L);
        project.setWorkflowTemplateVersionId(15L);
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(13L);
        node.setProjectId(10L);
        node.setNodeKey("quality-gate");
        when(projectMapper.selectById(10L)).thenReturn(project);
        when(workflowTemplateService.getDefinition(15L)).thenReturn(new WorkflowTemplateDefinition(1, List.of(
                new WorkflowNodeDefinition("quality-gate", "质量评审", "", "", "",
                        List.of(WorkflowComponentKey.SOLUTION_DESIGN), List.of(), false, List.of()))));
        when(nodeMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(node);

        assertThat(service().findNodeWithComponent(10L, WorkflowComponentKey.SOLUTION_DESIGN)).isSameAs(node);
    }

    private ProjectPermissionService service() {
        return new ProjectPermissionService(projectMapper, memberMapper, nodeMapper, dataScopeResolver,
                authorizationService, workflowTemplateService);
    }

    private ProjectDO project(int status, Long orgUnitId, Long creatorId, Long managerId) {
        ProjectDO project = new ProjectDO();
        project.setId(10L);
        project.setStatus(status);
        project.setOrgUnitId(orgUnitId);
        project.setCreatedBy(creatorId);
        project.setProjectManagerId(managerId);
        return project;
    }
}
