package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.brad.pms.dto.request.ProjectPageQry;
import com.brad.pms.entity.*;
import com.brad.pms.mapper.*;
import com.brad.pms.security.*;
import com.brad.pms.workflow.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import com.brad.pms.controller.ProjectController;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static com.brad.pms.service.ProjectBoardCalculatorTest.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ProjectBoardServiceTest {
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
    @Mock WorkflowTemplateVersionMapper versionMapper;
    @Mock ProjectNodeRiskMapper riskMapper;
    @Mock ProjectNodeDevelopmentStoryMapper storyMapper;
    @Mock ProjectNodeAcceptanceDefectMapper defectMapper;
    @InjectMocks ProjectService projectService;

    ProjectBoardService board;
    WorkflowTemplateService workflowService;

    @BeforeEach
    void setUp() {
        var configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        var assistant = new MapperBuilderAssistant(configuration, "board-test");
        for (Class<?> entity : List.of(ProjectDO.class, ProjectMemberDO.class, ProjectNodeDO.class, OrgUnitDO.class,
                WorkflowTemplateVersionDO.class, ProjectNodeRiskDO.class, ProjectNodeDevelopmentStoryDO.class,
                ProjectNodeAcceptanceDefectDO.class)) TableInfoHelper.initTableInfo(assistant, entity);
        workflowService = new WorkflowTemplateService(mock(ProjectTypeMapper.class), mock(WorkflowTemplateMapper.class),
                versionMapper, operationLogService, new ObjectMapper());
        board = new ProjectBoardService(projectService, workflowService, riskMapper, storyMapper, defectMapper,
                Clock.fixed(Instant.parse("2026-09-12T16:30:00Z"), ZoneId.of("Asia/Shanghai")));
        UserContext.set(new LoginUser(1L, "admin", "Admin", 1));
    }

    @AfterEach
    void clearUser() { UserContext.clear(); }

    @Test
    void emptyScopeSkipsAllAggregateAndEnrichmentQueries() {
        var result = board.getBoard(null);
        assertThat(result.projects()).isEmpty();
        assertThat(result.asOfDate()).isEqualTo("2026-09-13");
        assertThat(OffsetDateTime.parse(result.generatedAt()).toLocalDate().toString()).isEqualTo(result.asOfDate());
        assertThat(result.allCompanyScope()).isTrue();
        verifyNoInteractions(nodeMapper, versionMapper, riskMapper, storyMapper, defectMapper, taskMapper,
                memberMapper, permissionService, milestoneMapper);
    }

    @Test
    void httpRouteReturnsTheBoardEnvelopeAndConfiguredEmptySources() throws Exception {
        var project = storedProject(1L, null);
        project.setOrgUnitId(999L);
        var org = new OrgUnitDO();
        org.setId(999L);
        when(orgUnitMapper.selectById(999L)).thenReturn(org);
        when(projectMapper.selectList(any())).thenReturn(List.of(project));
        var mvc = MockMvcBuilders.standaloneSetup(new ProjectController(projectService, board)).build();
        var response = mvc.perform(get("/projects/board").param("orgUnitId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.asOfDate").value("2026-09-13"))
                .andExpect(jsonPath("$.data.allCompanyScope").value(true))
                .andExpect(jsonPath("$.data.projects[0].project.id").value(1))
                .andExpect(jsonPath("$.data.projects[0].phase").value("UNKNOWN"))
                .andReturn();
        var json = new ObjectMapper().readTree(response.getResponse().getContentAsString());
        var row = json.path("data").path("projects").get(0);
        assertThat(row.has("openRiskCount")).isTrue();
        assertThat(row.get("openRiskCount").asInt()).isZero();
        assertThat(row.has("storySummary")).isTrue();
        assertThat(row.path("storySummary").path("total").asInt()).isZero();
        assertThat(row.path("acceptanceSummary").path("total").asInt()).isZero();
        verify(orgUnitMapper).selectById(999L);
    }

    @Test
    void boardAndListUseIdenticalReadScopeAndDescendantOrganizationIntersection() {
        var user = new LoginUser(7L, "reader", "Reader", 0);
        UserContext.set(user);
        when(dataScopeResolver.resolveOrgUnitIds(user, PermissionCode.PROJECT_READ)).thenReturn(List.of(42L));
        var member = new ProjectMemberDO();
        member.setUserId(7L);
        member.setProjectId(80L);
        when(memberMapper.selectList(any())).thenReturn(List.of(member));
        var org = new OrgUnitDO();
        org.setId(11L);
        org.setPath("/1/11/");
        when(orgUnitMapper.selectById(11L)).thenReturn(org);
        when(orgUnitMapper.findDescendantIds("/1/11/", 11L)).thenReturn(List.of(11L, 12L));
        when(projectMapper.selectPage(any(), any())).thenReturn(new Page<>());
        var listQuery = new ProjectPageQry();
        listQuery.setOrgUnitId(11L);
        projectService.page(listQuery);
        var result = board.getBoard(11L);
        assertThat(result.allCompanyScope()).isFalse();
        ArgumentCaptor<LambdaQueryWrapper<ProjectDO>> list = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        ArgumentCaptor<LambdaQueryWrapper<ProjectDO>> boardQuery = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(projectMapper).selectPage(any(), list.capture());
        verify(projectMapper).selectList(boardQuery.capture());
        assertThat(boardQuery.getValue().getSqlSegment()).isEqualTo(list.getValue().getSqlSegment())
                .contains("org_unit_id", "OR", "AND", "status <>");
        assertThat(boardQuery.getValue().getParamNameValuePairs()).isEqualTo(list.getValue().getParamNameValuePairs());
        assertThat(boardQuery.getValue().getParamNameValuePairs().values()).contains(42L, 80L, 11L, 12L);
        verify(orgUnitMapper, times(2)).findDescendantIds("/1/11/", 11L);
    }

    @Test
    void unknownOrganizationAndNoReadableOrganizationsOrMembershipFailClosed() {
        UserContext.set(new LoginUser(7L, "reader", "Reader", 0));
        board.getBoard(999L);
        ArgumentCaptor<LambdaQueryWrapper<ProjectDO>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(projectMapper).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("id =");
        assertThat(query.getValue().getParamNameValuePairs().values()).contains(-1L);
        verifyNoInteractions(nodeMapper, versionMapper, riskMapper, storyMapper, defectMapper);
    }

    @Test
    void batchesBoundVersionsAndAllRecordsAndExcludesRemovedComponentsAndMismatchedProjects() throws Exception {
        var first = storedProject(1L, 100L);
        var second = storedProject(2L, 100L);
        when(projectMapper.selectList(any())).thenReturn(List.of(first, second));
        var current = node(1, TODAY);
        current.setNodeKey("current");
        var removed = node(2, TODAY.minusDays(3));
        removed.setId(11L);
        removed.setNodeKey("removed");
        var absent = node(0, TODAY);
        absent.setId(12L);
        absent.setNodeKey("absent-from-template");
        var other = node(1, TODAY);
        other.setId(20L);
        other.setProjectId(2L);
        other.setNodeKey("removed");
        when(nodeMapper.selectList(any())).thenReturn(List.of(current, removed, absent, other));
        var definition = new WorkflowTemplateDefinition(2, List.of(
                definitionNode("current", List.of("component:plan-resource-risk", "component:development-control", "component:business-acceptance")),
                definitionNode("removed", List.of())));
        var version = new WorkflowTemplateVersionDO();
        version.setId(100L);
        version.setDefinitionJson(new ObjectMapper().writeValueAsString(definition));
        when(versionMapper.selectList(any())).thenReturn(List.of(version));
        var staleRisk = risk("OPEN", "HIGH");
        staleRisk.setNodeId(11L);
        var mismatch = risk("OPEN", "HIGH");
        mismatch.setProjectId(2L);
        when(riskMapper.selectList(any())).thenReturn(List.of(risk("OPEN", "LOW"), staleRisk, mismatch));
        var staleStory = story("BLOCKED", 100, TODAY.minusDays(1));
        staleStory.setNodeId(12L);
        when(storyMapper.selectList(any())).thenReturn(List.of(story("DONE", 3, TODAY.minusDays(1)), staleStory));
        var staleDefect = defect("OPEN");
        staleDefect.setNodeId(11L);
        when(defectMapper.selectList(any())).thenReturn(List.of(defect("RESOLVED"), staleDefect));

        var result = board.getBoard(null);
        assertThat(result.projects()).hasSize(2);
        var row = result.projects().get(0);
        assertThat(row.project().getProgress()).isEqualTo(33);
        assertThat(row.highRiskCount()).isZero();
        assertThat(row.openRiskCount()).isEqualTo(1);
        assertThat(row.storySummary().total()).isEqualTo(1);
        assertThat(row.storySummary().donePoints()).isEqualTo(3);
        assertThat(row.acceptanceSummary().open()).isZero();
        assertThat(result.projects().get(1).openRiskCount()).isZero();
        assertThat(result.projects().get(1).storySummary().total()).isZero();
        assertThat(result.projects().get(1).acceptanceSummary().total()).isZero();
        verify(nodeMapper).selectList(any());
        verify(versionMapper).selectList(any());
        verify(riskMapper).selectList(any());
        verify(storyMapper).selectList(any());
        verify(defectMapper).selectList(any());
        verify(permissionService).projectPermissionsBatch(any());
        verify(permissionService, never()).projectPermissions(any(ProjectDO.class));
        verify(projectMapper, never()).selectById(any());
        verify(versionMapper, never()).selectById(any());
        verifyNoInteractions(milestoneMapper, nodeService);
        ArgumentCaptor<LambdaQueryWrapper<ProjectNodeRiskDO>> riskQuery = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(riskMapper).selectList(riskQuery.capture());
        assertThat(riskQuery.getValue().getSqlSegment()).contains("project_id IN", "node_id IN");
        assertThat(riskQuery.getValue().getParamNameValuePairs().values()).containsExactlyInAnyOrder(1L, 10L);
    }

    @Test
    void legacyBindingUsesCompatibilityComponentsAndConfiguredEmptyCounts() {
        when(projectMapper.selectList(any())).thenReturn(List.of(storedProject(1L, null)));
        when(nodeMapper.selectList(any())).thenReturn(List.of(node(0, TODAY)));
        var row = board.getBoard(null).projects().get(0);
        assertThat(row.riskDataState()).isEqualTo("AVAILABLE");
        assertThat(row.openRiskCount()).isZero();
        assertThat(row.storySummary().total()).isZero();
        assertThat(row.acceptanceSummary().total()).isZero();
        verifyNoInteractions(versionMapper, storyMapper, defectMapper);
    }

    @Test
    void enabledTemplateComponentsRemainConfiguredWhenProjectNodesAreNotMaterialized() throws Exception {
        when(projectMapper.selectList(any())).thenReturn(List.of(storedProject(1L, 100L), storedProject(2L, 101L)));
        when(nodeMapper.selectList(any())).thenReturn(List.of());
        var configured = new WorkflowTemplateDefinition(2, List.of(definitionNode("kickoff", List.of(
                "component:plan-resource-risk", "component:development-control", "component:business-acceptance"))));
        var withoutMetrics = new WorkflowTemplateDefinition(2, List.of(definitionNode("kickoff", List.of("component:requirement-scope"))));
        var configuredVersion = new WorkflowTemplateVersionDO();
        configuredVersion.setId(100L);
        configuredVersion.setDefinitionJson(new ObjectMapper().writeValueAsString(configured));
        var plainVersion = new WorkflowTemplateVersionDO();
        plainVersion.setId(101L);
        plainVersion.setDefinitionJson(new ObjectMapper().writeValueAsString(withoutMetrics));
        when(versionMapper.selectList(any())).thenReturn(List.of(configuredVersion, plainVersion));

        var rows = board.getBoard(null).projects();

        assertThat(rows.get(0).riskDataState()).isEqualTo("AVAILABLE");
        assertThat(rows.get(0).openRiskCount()).isZero();
        assertThat(rows.get(0).storySummary().total()).isZero();
        assertThat(rows.get(0).acceptanceSummary().total()).isZero();
        assertThat(rows.get(1).openRiskCount()).isNull();
        assertThat(rows.get(1).storySummary()).isNull();
        assertThat(rows.get(1).acceptanceSummary()).isNull();
        verifyNoInteractions(riskMapper, storyMapper, defectMapper);
    }

    @Test
    void missingBoundVersionIsNotSilentlyReplacedByCompatibilityTemplate() {
        when(projectMapper.selectList(any())).thenReturn(List.of(storedProject(1L, 100L)));
        when(nodeMapper.selectList(any())).thenReturn(List.of(node(1, TODAY)));
        assertThatThrownBy(() -> board.getBoard(null)).hasMessageContaining("流程模板版本不存在");
        verifyNoInteractions(riskMapper, storyMapper, defectMapper);
    }

    static ProjectDO storedProject(Long id, Long versionId) {
        var project = new ProjectDO();
        project.setId(id);
        project.setWorkflowTemplateVersionId(versionId);
        project.setStatus(0);
        project.setProgress(99);
        project.setStartDate(TODAY.minusDays(10));
        project.setEndDate(TODAY.plusDays(10));
        return project;
    }

    static WorkflowNodeDefinition definitionNode(String key, List<String> order) {
        return new WorkflowNodeDefinition(key, key, null, null, null, List.of(), List.of(), false, List.of(), order);
    }
}
