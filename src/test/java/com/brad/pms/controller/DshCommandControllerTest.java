package com.brad.pms.controller;

import com.brad.pms.ai.command.CommandExecutionService;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandPreviewService;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommandRegistry;
import com.brad.pms.ai.contract.PmsAgentContractRegistry;
import com.brad.pms.common.response.ResponseResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DshCommandControllerTest {

    @Test
    void rejectsAWritePreviewWithoutTheCurrentAgentContract() {
        DshCommandController controller = new DshCommandController(
                mock(CommandPreviewService.class), mock(CommandExecutionService.class), mock(PmsCommandRegistry.class),
                mock(PmsAgentContractRegistry.class));

        assertThatThrownBy(() -> controller.preview(new CommandPreviewRequest(
                CommandName.PROJECT_CREATE, Map.of("name", "测试"), "project-list", "v1")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("契约");
    }

    @Test
    void exposesPreviewAndExecuteUsingTheExistingCommandServices() {
        CommandPreviewService previewService = mock(CommandPreviewService.class);
        CommandExecutionService executionService = mock(CommandExecutionService.class);
        PmsCommandRegistry registry = mock(PmsCommandRegistry.class);
        PmsAgentContractRegistry contractRegistry = mock(PmsAgentContractRegistry.class);
        CommandPreview preview = new CommandPreview(
                "op-1", CommandName.TASK_CREATE, Instant.now(), "v1", List.of(), List.of(), List.of());
        CommandResult result = new CommandResult("op-1", "SUCCEEDED", "任务已创建", Map.of(), List.of());
        when(registry.list()).thenReturn(Set.of(CommandName.NODE_COMPLETE, CommandName.TASK_CREATE));
        when(contractRegistry.isCommandAllowed(
                "pms-project-assistant/project-kickoff", "1.0.0", "task.create")).thenReturn(true);
        when(previewService.preview(any())).thenReturn(preview);
        when(executionService.execute(any())).thenReturn(result);

        DshCommandController controller = new DshCommandController(previewService, executionService, registry, contractRegistry);

        ResponseResult<CommandPreview> previewResponse = controller.preview(
                new CommandPreviewRequest(CommandName.TASK_CREATE, Map.of("title", "测试"), "ctx", "v1",
                        "pms-project-assistant/project-kickoff", "1.0.0"));
        ResponseResult<CommandResult> executeResponse = controller.execute(
                "op-1", new DshCommandController.ExecuteBody("idem-1", "ctx", "v1",
                        "pms-project-assistant/project-kickoff", "1.0.0"));

        assertThat(controller.commands().getData()).containsExactly("node.complete", "task.create");
        assertThat(previewResponse.getData()).isSameAs(preview);
        assertThat(executeResponse.getData()).isSameAs(result);
        verify(previewService).preview(any());
        verify(executionService).execute(any());
    }

    @Test
    void rejectsExecutionWhenDshOmitsTheCurrentContextAndContractBinding() {
        CommandPreviewService previewService = mock(CommandPreviewService.class);
        CommandExecutionService executionService = mock(CommandExecutionService.class);
        PmsCommandRegistry registry = mock(PmsCommandRegistry.class);
        PmsAgentContractRegistry contractRegistry = mock(PmsAgentContractRegistry.class);

        DshCommandController controller = new DshCommandController(previewService, executionService, registry, contractRegistry);

        assertThatThrownBy(() -> controller.execute("op-2", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("绑定");
        verify(executionService, org.mockito.Mockito.never()).execute(any());
    }
}
