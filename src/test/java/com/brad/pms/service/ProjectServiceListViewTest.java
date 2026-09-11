package com.brad.pms.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.brad.pms.dto.request.ProjectPageQry;
import com.brad.pms.dto.response.ProjectListSummaryDTO;
import com.brad.pms.entity.OrgUnitDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectMemberDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.mapper.OrgUnitMapper;
import com.brad.pms.mapper.ProjectLifecycleLogMapper;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectMemberMapper;
import com.brad.pms.mapper.ProjectMilestoneMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.mapper.UserPositionMapper;
import com.brad.pms.security.DataScopeResolver;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceListViewTest {

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

    @InjectMocks ProjectService projectService;

    @BeforeEach
    void initMybatisLambdaCaches() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        TableInfoHelper.initTableInfo(assistant, ProjectDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectMemberDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectNodeDO.class);
        TableInfoHelper.initTableInfo(assistant, OrgUnitDO.class);
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void classifiesOwnedOverdueAndStalePortfolioSignals() {
        LocalDate today = LocalDate.of(2026, 9, 10);
        ProjectDO owned = project(1L, 8L, 8L, 1, today.minusDays(1));
        ProjectDO other = project(2L, 9L, 9L, 1, today.plusDays(3));
        ProjectDO noManager = project(3L, 9L, null, 1, today.plusDays(1));
        ProjectDO completed = project(4L, 8L, 8L, 2, today.minusDays(2));

        assertThat(ProjectService.isOwnedBy(owned, 8L)).isTrue();
        assertThat(ProjectService.isOwnedBy(other, 8L)).isFalse();
        assertThat(ProjectService.isOverdue(owned, today)).isTrue();
        assertThat(ProjectService.isOverdue(completed, today)).isFalse();
        assertThat(ProjectService.hasNoManager(noManager)).isTrue();
        assertThat(ProjectService.hasNoManager(owned)).isFalse();
        assertThat(ProjectService.isStaleNode(node(1L, 1, today.minusDays(1)), today)).isTrue();
        assertThat(ProjectService.isStaleNode(node(1L, 1, today.plusDays(1)), today)).isFalse();
    }

    @Test
    void summaryCountsAttentionSignalsInsideTheCurrentReadScope() {
        UserContext.set(new LoginUser(8L, "manager", "项目经理", 0));
        LocalDate today = LocalDate.now();
        ProjectDO overdue = project(1L, 8L, 8L, 1, today.minusDays(1));
        ProjectDO noManager = project(2L, 8L, null, 1, today.plusDays(5));
        ProjectDO stale = project(3L, 8L, 8L, 1, today.plusDays(5));
        when(projectMapper.selectList(any())).thenReturn(List.of(overdue, noManager, stale));
        when(nodeMapper.selectList(any())).thenReturn(List.of(node(3L, 1, today.minusDays(1))));
        when(userService.listByIds(any())).thenReturn(List.of());

        ProjectPageQry query = new ProjectPageQry();
        query.setView("PORTFOLIO");
        ProjectListSummaryDTO summary = projectService.listSummary(query);

        assertThat(summary.getTotal()).isEqualTo(3);
        assertThat(summary.getActive()).isEqualTo(3);
        assertThat(summary.getOverdue()).isEqualTo(1);
        assertThat(summary.getNoManager()).isEqualTo(1);
        assertThat(summary.getStaleNode()).isEqualTo(1);
        assertThat(summary.getCurrentNodes()).extracting(ProjectListSummaryDTO.NodeOption::getKey)
                .containsExactly("design");
    }

    @Test
    void pageResolvesBusinessLineFilterToTheOrgAndItsDescendants() {
        UserContext.set(new LoginUser(1L, "admin", "管理员", 1));
        OrgUnitDO org = new OrgUnitDO();
        org.setId(11L);
        org.setPath("/1/11/");
        when(orgUnitMapper.selectById(11L)).thenReturn(org);
        when(orgUnitMapper.findDescendantIds("/1/11/", 11L)).thenReturn(List.of(11L, 12L));
        when(projectMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));

        ProjectPageQry query = new ProjectPageQry();
        query.setView("ALL");
        query.setOrgUnitId(11L);
        query.setCurrPage(1);
        query.setPageSize(10);

        projectService.page(query);

        verify(orgUnitMapper).findDescendantIds("/1/11/", 11L);
        verify(projectMapper).selectPage(any(), any());
    }

    @Test
    void currentNodePrefersTheActiveNodeWithTheLowestSort() {
        ProjectNodeDO later = node(9L, 1, LocalDate.now());
        later.setSort(3);
        later.setNodeKey("plan");
        later.setName("计划");
        ProjectNodeDO current = node(9L, 1, LocalDate.now());
        current.setSort(1);
        current.setNodeKey("design");
        current.setName("方案设计");
        ProjectNodeDO pending = node(9L, 0, LocalDate.now());
        pending.setSort(0);
        pending.setNodeKey("kickoff");

        ProjectNodeDO selected = ProjectService.currentNodeOf(List.of(later, pending, current));
        assertThat(selected).isSameAs(current);
    }

    private static ProjectDO project(Long id, Long createdBy, Long managerId, int status, LocalDate endDate) {
        ProjectDO project = new ProjectDO();
        project.setId(id);
        project.setCreatedBy(createdBy);
        project.setProjectManagerId(managerId);
        project.setStatus(status);
        project.setEndDate(endDate);
        return project;
    }

    private static ProjectNodeDO node(Long projectId, int status, LocalDate endDate) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setProjectId(projectId);
        node.setStatus(status);
        node.setEndDate(endDate);
        node.setNodeKey("design");
        node.setName("方案设计");
        node.setSort(1);
        return node;
    }
}
