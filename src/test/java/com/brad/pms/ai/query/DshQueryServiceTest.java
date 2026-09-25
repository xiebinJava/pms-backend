package com.brad.pms.ai.query;

import com.brad.pms.ai.context.PageContextService;
import com.brad.pms.ai.context.PageContextSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DshQueryServiceTest {

    @Test
    void queriesProjectsThroughTheAuthoritativePageContextAssembler() {
        PageContextService contextService = mock(PageContextService.class);
        AiTaskQueryService taskQueryService = mock(AiTaskQueryService.class);
        PageContextSnapshot snapshot = new PageContextSnapshot(
                "project-list", com.brad.pms.ai.context.PageContextType.PROJECT_LIST,
                "/projects", null, null, Instant.now(), "page-1",
                Map.of("projects", List.of(Map.of("code", "PRJ-1")),
                        "pagination", Map.of("page", 1, "total", 1)));
        when(contextService.assemble(any())).thenReturn(snapshot);
        DshQueryService service = new DshQueryService(contextService, taskQueryService, new ObjectMapper());

        DshQueryResult result = service.query(new DshQueryRequest(
                "projects", Map.of("priority", 3), List.of(), 1, 50));

        assertThat(result.resource()).isEqualTo("projects");
        assertThat(result.authoritative()).isTrue();
        assertThat(result.data()).containsEntry("projects", List.of(Map.of("code", "PRJ-1")));
        verify(contextService).assemble(any());
    }

    @Test
    void queriesTasksWithTheScopedReadOnlyTaskService() {
        PageContextService contextService = mock(PageContextService.class);
        AiTaskQueryService taskQueryService = mock(AiTaskQueryService.class);
        AiTaskQueryResult taskResult = new AiTaskQueryResult(
                "task-query", true, "Asia/Shanghai", LocalDate.of(2026, 9, 17),
                1, 1, 50, 1,
                new AiTaskQueryResult.Pagination(1, 50, 1), List.of());
        when(taskQueryService.query(any())).thenReturn(taskResult);
        DshQueryService service = new DshQueryService(contextService, taskQueryService, new ObjectMapper());

        DshQueryResult result = service.query(new DshQueryRequest(
                "tasks", Map.of("projectId", 22, "due", "today"), List.of(), 1, 50));

        assertThat(result.resource()).isEqualTo("tasks");
        assertThat(result.data()).containsKey("tasks");
        verify(taskQueryService).query(new AiTaskQueryRequest("project", "today", "all", 22L, null, 1, 50));
    }

    @Test
    void rejectsUnsupportedResourcesAndFilters() {
        DshQueryService service = new DshQueryService(
                mock(PageContextService.class), mock(AiTaskQueryService.class), new ObjectMapper());

        assertThatThrownBy(() -> service.query(new DshQueryRequest("users", Map.of(), List.of(), 1, 50)))
                .hasMessageContaining("查询资源");
        assertThatThrownBy(() -> service.query(new DshQueryRequest(
                "projects", Map.of("sql", "select 1"), List.of(), 1, 50)))
                .hasMessageContaining("过滤条件");
    }
}
