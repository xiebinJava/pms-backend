package com.brad.pms.controller;

import com.brad.pms.ai.context.PageContextRequest;
import com.brad.pms.ai.context.PageContextService;
import com.brad.pms.ai.context.PageContextSnapshot;
import com.brad.pms.ai.context.PageContextType;
import com.brad.pms.ai.query.AiTaskQueryRequest;
import com.brad.pms.ai.query.AiTaskQueryResult;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.ProjectPageQry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DshIntegrationControllerTest {

    @Test
    void projectsUsesAuthoritativeProjectListContextAndKeepsEnvelope() {
        PageContextService pageContextService = mock(PageContextService.class);
        com.brad.pms.ai.query.AiTaskQueryService taskQueryService = mock(com.brad.pms.ai.query.AiTaskQueryService.class);
        DshIntegrationController controller = new DshIntegrationController(pageContextService, taskQueryService);
        PageContextSnapshot snapshot = snapshot(PageContextType.PROJECT_LIST, "/projects", null, null);
        when(pageContextService.assemble(any(PageContextRequest.class))).thenReturn(snapshot);

        ProjectPageQry query = new ProjectPageQry();
        query.setCurrPage(2);
        query.setPageSize(25);
        query.setKeyword("智能");
        query.setStatus(2);
        query.setView("ALL");
        query.setPriority(3);

        ResponseResult<PageContextSnapshot> response = controller.projects(query);

        ArgumentCaptor<PageContextRequest> requestCaptor = ArgumentCaptor.forClass(PageContextRequest.class);
        verify(pageContextService).assemble(requestCaptor.capture());
        PageContextRequest request = requestCaptor.getValue();
        assertThat(request.pageType()).isEqualTo(PageContextType.PROJECT_LIST);
        assertThat(request.route()).isEqualTo("/projects");
        assertThat(request.pageState())
                .containsEntry("page", 2)
                .containsEntry("pageSize", 25)
                .containsEntry("keyword", "智能")
                .containsEntry("status", 2)
                .containsEntry("view", "ALL")
                .containsEntry("priority", 3);
        assertThat(response.getData()).isSameAs(snapshot);
    }

    @Test
    void projectUsesProjectDetailContextWithProjectAndNodeIdentity() {
        PageContextService pageContextService = mock(PageContextService.class);
        com.brad.pms.ai.query.AiTaskQueryService taskQueryService = mock(com.brad.pms.ai.query.AiTaskQueryService.class);
        DshIntegrationController controller = new DshIntegrationController(pageContextService, taskQueryService);
        PageContextSnapshot snapshot = snapshot(PageContextType.PROJECT_DETAIL, "/projects/22", 22L, 7L);
        when(pageContextService.assemble(any(PageContextRequest.class))).thenReturn(snapshot);

        ResponseResult<PageContextSnapshot> response = controller.project(22L, 7L);

        ArgumentCaptor<PageContextRequest> requestCaptor = ArgumentCaptor.forClass(PageContextRequest.class);
        verify(pageContextService).assemble(requestCaptor.capture());
        PageContextRequest request = requestCaptor.getValue();
        assertThat(request.pageType()).isEqualTo(PageContextType.PROJECT_DETAIL);
        assertThat(request.route()).isEqualTo("/projects/22");
        assertThat(request.projectId()).isEqualTo(22L);
        assertThat(request.nodeId()).isEqualTo(7L);
        assertThat(response.getData()).isSameAs(snapshot);
    }

    @Test
    void tasksDelegatesToPermissionAwareTaskQueryWithProjectScope() {
        PageContextService pageContextService = mock(PageContextService.class);
        com.brad.pms.ai.query.AiTaskQueryService taskQueryService = mock(com.brad.pms.ai.query.AiTaskQueryService.class);
        DshIntegrationController controller = new DshIntegrationController(pageContextService, taskQueryService);
        AiTaskQueryResult result = new AiTaskQueryResult(
                "project", true, "Asia/Shanghai", LocalDate.of(2026, 9, 16),
                0, 1, 20, 0, new AiTaskQueryResult.Pagination(1, 20, 0), List.of());
        when(taskQueryService.query(any(AiTaskQueryRequest.class))).thenReturn(result);

        ResponseResult<AiTaskQueryResult> response = controller.tasks(22L, 7L, "today", null, 1, 20);

        ArgumentCaptor<AiTaskQueryRequest> requestCaptor = ArgumentCaptor.forClass(AiTaskQueryRequest.class);
        verify(taskQueryService).query(requestCaptor.capture());
        AiTaskQueryRequest request = requestCaptor.getValue();
        assertThat(request.scope()).isEqualTo("project");
        assertThat(request.projectId()).isEqualTo(22L);
        assertThat(request.nodeId()).isEqualTo(7L);
        assertThat(request.due()).isEqualTo("today");
        assertThat(request.status()).isEqualTo("all");
        assertThat(response.getData()).isSameAs(result);
    }

    @Test
    void tasksTreatsAllAsThePublicAliasForAnyDueDate() {
        PageContextService pageContextService = mock(PageContextService.class);
        com.brad.pms.ai.query.AiTaskQueryService taskQueryService = mock(com.brad.pms.ai.query.AiTaskQueryService.class);
        DshIntegrationController controller = new DshIntegrationController(pageContextService, taskQueryService);

        controller.tasks(22L, null, "all", null, null, null);

        ArgumentCaptor<AiTaskQueryRequest> requestCaptor = ArgumentCaptor.forClass(AiTaskQueryRequest.class);
        verify(taskQueryService).query(requestCaptor.capture());
        assertThat(requestCaptor.getValue().due()).isEqualTo("any");
    }

    private static PageContextSnapshot snapshot(PageContextType pageType, String route, Long projectId, Long nodeId) {
        return new PageContextSnapshot(
                "ctx-test",
                pageType,
                route,
                projectId,
                nodeId,
                Instant.parse("2026-09-16T02:00:00Z"),
                "v1",
                Map.of("authoritative", true));
    }
}
