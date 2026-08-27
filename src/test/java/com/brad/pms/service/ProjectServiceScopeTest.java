package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.brad.pms.dto.request.ProjectPageQry;
import com.brad.pms.dto.response.ProjectPermissionsDTO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectMemberDO;
import com.brad.pms.entity.ProjectNodeDO;
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
}
