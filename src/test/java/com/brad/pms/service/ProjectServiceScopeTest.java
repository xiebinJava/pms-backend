package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectMemberDO;
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
}
