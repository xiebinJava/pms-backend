package com.brad.pms.controller;

import com.brad.pms.ai.command.CommandExecutionService;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandPreviewService;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommandRegistry;
import com.brad.pms.ai.context.PageContextRequest;
import com.brad.pms.ai.context.PageContextService;
import com.brad.pms.ai.context.PageContextSnapshot;
import com.brad.pms.ai.context.PageContextType;
import com.brad.pms.common.response.ResponseResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCommandControllerTest {

    @Mock CommandPreviewService previewService;
    @Mock CommandExecutionService executionService;
    @Mock PmsCommandRegistry registry;
    @Mock PageContextService contextService;

    @Test
    void exposesStableCommandNamesAndPreviewEnvelope() {
        when(registry.list()).thenReturn(Set.of(CommandName.TASK_CREATE, CommandName.TASK_ASSIGN));
        CommandPreview preview = new CommandPreview("op-1", CommandName.TASK_CREATE,
                Instant.now(), "v1", List.of(), List.of(Map.of("action", "create")), List.of("task-board"));
        when(previewService.preview(any())).thenReturn(preview);

        AiCommandController controller = new AiCommandController(previewService, executionService, registry);

        ResponseResult<List<String>> commands = controller.commands();
        ResponseResult<CommandPreview> response = controller.preview(new CommandPreviewRequest(
                CommandName.TASK_CREATE, Map.of("title", "测试"), "ctx", "v1"));

        assertThat(commands.getData()).containsExactly("task.assign", "task.create");
        assertThat(response.getData()).isSameAs(preview);
    }

    @Test
    void contextControllerReturnsBackendSnapshot() {
        PageContextSnapshot snapshot = new PageContextSnapshot("project-detail:7:none",
                PageContextType.PROJECT_DETAIL, "/projects/7", 7L, null, Instant.now(), "v1", Map.of());
        when(contextService.assemble(any())).thenReturn(snapshot);

        ResponseResult<PageContextSnapshot> response = new AiContextController(contextService).inspect(
                new PageContextRequest(PageContextType.PROJECT_DETAIL, "/projects/7", 7L, null, Map.of()));

        assertThat(response.getData()).isSameAs(snapshot);
    }
}
