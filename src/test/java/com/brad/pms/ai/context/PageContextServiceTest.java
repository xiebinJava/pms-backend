package com.brad.pms.ai.context;

import com.brad.pms.common.page.PageResult;
import com.brad.pms.dto.response.ProjectBoardDTO;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.dto.response.ProjectMemberDTO;
import com.brad.pms.dto.response.ProjectNodeDTO;
import com.brad.pms.dto.response.ProjectTaskDTO;
import com.brad.pms.dto.response.ProjectListSummaryDTO;
import com.brad.pms.dto.response.WorkflowTemplateOptionsDTO;
import com.brad.pms.service.MemberService;
import com.brad.pms.service.NodeService;
import com.brad.pms.service.ProjectBoardService;
import com.brad.pms.service.ProjectService;
import com.brad.pms.service.TaskService;
import com.brad.pms.service.WorkflowTemplateService;
import com.brad.pms.service.FollowerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageContextServiceTest {

    @Mock ProjectService projectService;
    @Mock NodeService nodeService;
    @Mock MemberService memberService;
    @Mock FollowerService followerService;
    @Mock TaskService taskService;
    @Mock ProjectBoardService projectBoardService;
    @Mock WorkflowTemplateService workflowTemplateService;

    @Test
    void projectDetailContextContainsAuthoritativeProjectNodeMembersAndTasks() {
        ProjectDTO project = new ProjectDTO();
        project.setId(7L);
        project.setVersion(3);
        project.setName("AI 项目");
        project.setCurrentNodeName("需求澄清");
        ProjectNodeDTO node = new ProjectNodeDTO();
        node.setId(71L);
        node.setVersion(2);
        node.setName("需求澄清");
        ProjectMemberDTO member = new ProjectMemberDTO();
        member.setUserId(11L);
        member.setDisplayName("张伟");
        ProjectTaskDTO task = new ProjectTaskDTO();
        task.setId(91L);
        task.setTitle("整理需求");

        when(projectService.detail(7L)).thenReturn(project);
        when(nodeService.list(7L)).thenReturn(List.of(node));
        when(memberService.list(7L)).thenReturn(List.of(member));
        when(followerService.list(7L)).thenReturn(List.of());
        when(taskService.listByProject(7L, 71L)).thenReturn(List.of(task));

        PageContextSnapshot snapshot = new ProjectDetailContextAssembler(
                projectService, nodeService, memberService, followerService, taskService).assemble(
                new PageContextRequest(PageContextType.PROJECT_DETAIL, "/projects/7", 7L, 71L, Map.of()));

        assertThat(snapshot.contextId()).isEqualTo("project-detail:7:71");
        assertThat(snapshot.version()).isEqualTo("project-3:node-2");
        assertThat(snapshot.data()).containsKeys("project", "currentNode", "members", "followers", "tasks");
        assertThat(snapshot.data().get("members")).isEqualTo(List.of(member));
    }

    @Test
    void listContextCapsRowsAndPreservesFilterHints() {
        ProjectDTO first = new ProjectDTO();
        first.setId(1L);
        first.setName("项目一");
        first.setCode("P1");
        first.setStatus(2);
        first.setPriority(3);
        ProjectDTO second = new ProjectDTO();
        second.setId(2L);
        second.setName("项目二");
        second.setCode("P2");
        second.setStatus(1);
        second.setPriority(1);
        when(projectService.page(any())).thenReturn(PageResult.of(25, 1, 10, List.of(first, second)));
        when(projectService.listSummary(any())).thenReturn(new ProjectListSummaryDTO());

        PageContextSnapshot snapshot = new ProjectListContextAssembler(projectService).assemble(
                new PageContextRequest(PageContextType.PROJECT_LIST, "/projects", null, null,
                        Map.of("keyword", "项目", "status", "active")));

        assertThat(snapshot.data()).containsEntry("total", 25L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projects = (List<Map<String, Object>>) snapshot.data().get("projects");
        assertThat(projects).hasSize(2);
        assertThat(projects.get(0))
                .containsEntry("name", "项目一")
                .containsEntry("statusLabel", "已完成")
                .containsEntry("priorityLabel", "紧急");
        assertThat(projects.get(1))
                .containsEntry("name", "项目二")
                .containsEntry("statusLabel", "进行中")
                .containsEntry("priorityLabel", "中");
        assertThat(snapshot.data().get("filters")).isEqualTo(Map.of("keyword", "项目", "status", "active"));
        assertThat(snapshot.data().get("visibleCount")).isEqualTo(2);
        assertThat(snapshot.data()).containsKeys("summary", "pagination");
        assertThat(snapshot.data().get("statusLegend")).isEqualTo(Map.of(
                "1", "进行中",
                "2", "已完成",
                "3", "已终止",
                "4", "已删除"));
        assertThat(snapshot.data().get("currentNodeRule"))
                .isEqualTo("已完成或已终止项目可能没有当前节点，当前节点为空时应明确说明。");
        assertThat(snapshot.data().get("priorityLegend")).isEqualTo(Map.of(
                "0", "低",
                "1", "中",
                "2", "高",
                "3", "紧急"));
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> priorityGroups =
                (Map<String, List<Map<String, Object>>>) snapshot.data().get("priorityGroups");
        assertThat(priorityGroups.get("紧急")).containsExactly(
                Map.of("name", "项目一", "code", "P1"));
    }

    @Test
    void listContextUsesPageAndPageSizeHintsAndMarksCurrentPageScope() {
        when(projectService.page(any())).thenReturn(PageResult.of(25, 3, 5, List.of()));
        when(projectService.listSummary(any())).thenReturn(new ProjectListSummaryDTO());

        PageContextSnapshot snapshot = new ProjectListContextAssembler(projectService).assemble(
                new PageContextRequest(PageContextType.PROJECT_LIST, "/projects?page=3", null, null,
                        Map.of("page", 3, "pageSize", 5)));

        ArgumentCaptor<com.brad.pms.dto.request.ProjectPageQry> query =
                ArgumentCaptor.forClass(com.brad.pms.dto.request.ProjectPageQry.class);
        org.mockito.Mockito.verify(projectService).page(query.capture());
        assertThat(query.getValue().getCurrPage()).isEqualTo(3);
        assertThat(query.getValue().getPageSize()).isEqualTo(5);
        assertThat(snapshot.data()).containsEntry("dataScope", "current-page");
        assertThat(snapshot.data()).containsEntry("authoritative", true);
        assertThat(snapshot.data().get("pagination")).isEqualTo(Map.of(
                "page", 3L, "pageSize", 5L, "total", 25L, "totalPage", 5L));
    }

    @Test
    void serviceRejectsUnsupportedContextType() {
        PageContextAssembler assembler = new PageContextAssembler() {
            @Override public PageContextType supports() { return PageContextType.PROJECT_LIST; }
            @Override public PageContextSnapshot assemble(PageContextRequest request) { return null; }
        };
        PageContextService service = new PageContextService(List.of(assembler));

        assertThatThrownBy(() -> service.assemble(new PageContextRequest(
                PageContextType.PROJECT_DETAIL, "/projects/1", 1L, null, Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PROJECT_DETAIL");
    }

    @Test
    void dashboardAndWorkflowAssemblersExposeStructuredSnapshots() {
        ProjectBoardDTO board = new ProjectBoardDTO("2026-09-15", "2026-09-15T10:00:00+08:00", true, List.of());
        when(projectBoardService.getBoard(null)).thenReturn(board);
        WorkflowTemplateOptionsDTO options = new WorkflowTemplateOptionsDTO();
        options.setTemplates(List.of());
        options.setProjectTypes(List.of());
        when(workflowTemplateService.options()).thenReturn(options);

        PageContextSnapshot dashboard = new ProjectDashboardContextAssembler(projectBoardService).assemble(
                new PageContextRequest(PageContextType.PROJECT_DASHBOARD, "/projects/dashboard", null, null, Map.of()));
        PageContextSnapshot workflow = new WorkflowTemplateContextAssembler(workflowTemplateService).assemble(
                new PageContextRequest(PageContextType.WORKFLOW_TEMPLATE, "/admin/workflows", null, null, Map.of()));

        assertThat(dashboard.data()).containsEntry("board", board);
        assertThat(workflow.data()).containsEntry("options", options);
    }
}
