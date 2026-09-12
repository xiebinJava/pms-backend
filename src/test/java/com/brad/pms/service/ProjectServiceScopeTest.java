package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.brad.pms.dto.request.ProjectCreateCmd;
import com.brad.pms.dto.request.ProjectPageQry;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.dto.response.ProjectPermissionsDTO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectMemberDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.OrgUnitDO;
import com.brad.pms.entity.ProjectTypeDO;
import com.brad.pms.entity.WorkflowTemplateDO;
import com.brad.pms.entity.WorkflowTemplateVersionDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.security.DataScopeResolver;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import com.brad.pms.mapper.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceScopeTest {
    @Mock ProjectMapper projectMapper;
    @Mock ProjectMemberMapper memberMapper;
    @Mock ProjectTaskMapper taskMapper;
    @Mock ProjectMilestoneMapper milestoneMapper;
    @Mock ProjectNodeMapper nodeMapper;
    @Mock MemberService memberService;
    @Mock NodeService nodeService;
    @Mock UserService userService;
    @Mock FollowerService followerService;
    @Mock ProjectPermissionService permissionService;
    @Mock ProjectLifecycleLogMapper lifecycleLogMapper;
    @Mock UserPositionMapper userPositionMapper;
    @Mock DataScopeResolver dataScopeResolver;
    @Mock OrgUnitMapper orgUnitMapper;
    @Mock OperationLogService operationLogService;
    @Mock WorkflowTemplateService workflowTemplateService;

    @InjectMocks ProjectService projectService;

    @AfterEach
    void clearContext() { UserContext.clear(); }

    @org.junit.jupiter.api.BeforeEach
    void initMybatisLambdaCaches() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        TableInfoHelper.initTableInfo(assistant, ProjectDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectMemberDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectNodeDO.class);
        TableInfoHelper.initTableInfo(assistant, OrgUnitDO.class);
    }

    @Test
    void createUsesAnInsertSafeTemporaryCodeBeforeFormattingProjectId() {
        UserContext.set(new LoginUser(1L, "admin", "管理员", 1));
        ProjectCreateCmd command = new ProjectCreateCmd();
        command.setName("CI 验收项目");
        command.setProjectLevel(2);

        ProjectTypeDO projectType = new ProjectTypeDO();
        projectType.setId(5L);
        WorkflowTemplateVersionDO templateVersion = new WorkflowTemplateVersionDO();
        templateVersion.setId(9L);
        WorkflowTemplateDO template = new WorkflowTemplateDO();
        template.setId(3L);
        template.setProjectTypeId(5L);
        when(workflowTemplateService.resolveForProjectCreation(null, null))
                .thenReturn(new WorkflowTemplateService.WorkflowTemplateBinding(projectType, templateVersion, template));
        var workflowDefinition = com.brad.pms.workflow.BuiltInWorkflowTemplate.compatibilityDefinition();
        when(workflowTemplateService.getDefinition(9L)).thenReturn(workflowDefinition);
        projectService.setWorkflowTemplateService(workflowTemplateService);

        when(projectMapper.insert(any(ProjectDO.class))).thenAnswer(invocation -> {
            ProjectDO project = invocation.getArgument(0);
            assertThat(project.getCode()).startsWith("TMP-");
            assertThat(project.getProjectLevel()).isEqualTo(2);
            assertThat(project.getProjectTypeId()).isEqualTo(5L);
            assertThat(project.getWorkflowTemplateVersionId()).isEqualTo(9L);
            project.setId(99L);
            return 1;
        });
        when(permissionService.requireProject(99L)).thenAnswer(invocation -> {
            ProjectDO project = new ProjectDO();
            project.setId(99L);
            project.setCode("PRJ-000099");
            project.setName("CI 验收项目");
            project.setOwnerId(1L);
            project.setCreatedBy(1L);
            project.setStatus(1);
            return project;
        });
        when(memberMapper.selectList(any())).thenReturn(List.of());
        when(taskMapper.countByProjectIds(any())).thenReturn(List.of());
        when(nodeMapper.selectList(any())).thenReturn(List.of());
        when(userService.listByIds(any())).thenReturn(List.of(user(1L)));
        when(permissionService.projectPermissions(any())).thenReturn(new ProjectPermissionsDTO());

        ProjectDTO result = projectService.create(command);

        assertThat(result.getCode()).isEqualTo("PRJ-000099");
        verify(nodeService).initFromDefinition(99L, 1L, workflowDefinition);
        verify(projectMapper).updateById(org.mockito.ArgumentMatchers.<ProjectDO>argThat(
                project -> "PRJ-000099".equals(project.getCode())));
                verify(operationLogService).record(org.mockito.ArgumentMatchers.argThat(event ->
                "PROJECT_CREATED".equals(event.action())
                        && "PROJECT".equals(event.resourceType())
                        && Long.valueOf(99L).equals(event.projectId())));
    }

    @Test
    void deleteMarksTheProjectSoftDeletedAndKeepsTheAuditContext() {
        UserContext.set(new LoginUser(1L, "admin", "管理员", 1));
        ProjectDO project = new ProjectDO();
        project.setId(100L);
        project.setStatus(1);
        project.setDeleted(false);
        when(permissionService.requireProjectManageable(100L, "删除项目")).thenReturn(project);

        projectService.delete(100L);

        verify(projectMapper).softDeleteProject(100L, 4);
        verify(operationLogService).record(org.mockito.ArgumentMatchers.argThat(event ->
                "PROJECT_DELETED".equals(event.action()) && Long.valueOf(100L).equals(event.projectId())));
    }

    @Test
    void statsAppliesTheSameOrganizationAndMemberScopeAsProjectList() {
        LoginUser user = new LoginUser(7L, "member", "成员", 0);
        UserContext.set(user);
        when(dataScopeResolver.resolveOrgUnitIds(user, "project:read")).thenReturn(List.of(42L));
        when(dataScopeResolver.hasAllCompanyScope(user, "project:read")).thenReturn(false);
        when(memberMapper.selectList(any())).thenReturn(List.of());
        when(projectMapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<ProjectDO> wrapper = invocation.getArgument(0);
            assertThat(wrapper).isNotNull();
            ProjectDO project = new ProjectDO();
            project.setStatus(1);
            project.setProgress(50);
            return List.of(project);
        });

        var stats = projectService.stats();

        assertThat(stats).containsEntry("total", 1);
        assertThat(stats).containsEntry("active", 1L);
        assertThat(stats).containsEntry("avgProgress", 50L);
        verify(dataScopeResolver).resolveOrgUnitIds(user, "project:read");
        verify(dataScopeResolver).hasAllCompanyScope(user, "project:read");
    }

    @Test
    void listReadableByIdsAppliesTheSameReadScopeAsProjectList() {
        LoginUser user = new LoginUser(7L, "member", "成员", 0);
        UserContext.set(user);
        when(dataScopeResolver.resolveOrgUnitIds(user, "project:read")).thenReturn(List.of(42L));
        when(dataScopeResolver.hasAllCompanyScope(user, "project:read")).thenReturn(false);
        when(projectMapper.selectList(any())).thenReturn(List.of());
        when(memberMapper.selectList(any())).thenReturn(List.of());

        assertThat(projectService.listReadableByIds(List.of(1L, 2L))).isEmpty();
        assertThat(projectService.listReadableByIds(List.of())).isEmpty();
        verify(dataScopeResolver).resolveOrgUnitIds(user, "project:read");
    }

    @Test
    void listReadableIdsAppliesTheSameReadScopeAsProjectList() {
        LoginUser user = new LoginUser(7L, "member", "成员", 0);
        UserContext.set(user);
        when(dataScopeResolver.resolveOrgUnitIds(user, "project:read")).thenReturn(List.of(42L));
        when(dataScopeResolver.hasAllCompanyScope(user, "project:read")).thenReturn(false);
        when(memberMapper.selectList(any())).thenReturn(List.of());
        ProjectDO project = new ProjectDO();
        project.setId(11L);
        when(projectMapper.selectList(any())).thenReturn(List.of(project));

        assertThat(projectService.listReadableIds()).containsExactly(11L);
        verify(dataScopeResolver).resolveOrgUnitIds(user, "project:read");
    }

    @Test
    void pageUsesCompletedNodeRatioForTheSharedProjectProgress() {
        UserContext.set(new LoginUser(1L, "admin", "管理员", 1));
        ProjectDO project = new ProjectDO();
        project.setId(9L);
        project.setOwnerId(1L);
        project.setCreatedBy(1L);
        project.setProgress(11);
        Page<ProjectDO> page = new Page<>(1, 10);
        page.setTotal(1);
        page.setRecords(List.of(project));
        when(projectMapper.selectPage(any(), any())).thenReturn(page);
        when(memberMapper.selectList(any())).thenReturn(List.of());
        when(taskMapper.countByProjectIds(any())).thenReturn(List.of());
        when(nodeMapper.selectList(any())).thenReturn(List.of(
                node(2), node(2), node(0), node(0)
        ));
        when(userService.listByIds(any())).thenReturn(List.of(user(1L)));
        when(permissionService.projectPermissions(project)).thenReturn(new ProjectPermissionsDTO());

        ProjectPageQry query = new ProjectPageQry();
        query.setCurrPage(1);
        query.setPageSize(10);

        var result = projectService.page(query);

        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().get(0).getProgress()).isEqualTo(50);
    }

    @Test
    void pageAndDetailExposeTheSameOrganizationPathAndLeaderDisplay() {
        UserContext.set(new LoginUser(1L, "admin", "管理员", 1));
        ProjectDO project = new ProjectDO();
        project.setId(12L);
        project.setOwnerId(1L);
        project.setCreatedBy(1L);
        project.setOrgUnitId(11L);
        Page<ProjectDO> page = new Page<>(1, 10);
        page.setTotal(1);
        page.setRecords(List.of(project));
        when(projectMapper.selectPage(any(), any())).thenReturn(page);
        when(memberMapper.selectList(any())).thenReturn(List.of());
        when(taskMapper.countByProjectIds(any())).thenReturn(List.of());
        when(nodeMapper.selectList(any())).thenReturn(List.of());
        when(userService.listByIds(any())).thenReturn(List.of(user(1L), user(8L)));
        when(permissionService.projectPermissions(project)).thenReturn(new ProjectPermissionsDTO());
        when(orgUnitMapper.selectList(any())).thenReturn(List.of(
                org(1L, null, "公司总部", "/1/", null),
                org(11L, 1L, "产品制造 BG", "/1/11/", 8L)
        ));

        ProjectPageQry query = new ProjectPageQry();
        query.setCurrPage(1);
        query.setPageSize(10);

        var dto = projectService.page(query).getList().get(0);

        assertThat(dto.getOrgUnitName()).isEqualTo("产品制造 BG");
        assertThat(dto.getOrgUnitPath()).isEqualTo("公司总部 / 产品制造 BG");
        assertThat(dto.getOrgUnitLeaderName()).isEqualTo("用户（user）");
    }

    private static ProjectNodeDO node(int status) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setProjectId(9L);
        node.setStatus(status);
        return node;
    }

    private static UserDO user(Long id) {
        UserDO user = new UserDO();
        user.setId(id);
        user.setUsername(id == 1L ? "admin" : "user");
        user.setNameZh(id == 1L ? "管理员" : "用户");
        return user;
    }

    private static OrgUnitDO org(Long id, Long parentId, String name, String path, Long leaderId) {
        OrgUnitDO org = new OrgUnitDO();
        org.setId(id);
        org.setParentId(parentId);
        org.setName(name);
        org.setPath(path);
        org.setLeaderUserId(leaderId);
        return org;
    }
}
