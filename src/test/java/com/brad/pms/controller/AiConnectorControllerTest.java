package com.brad.pms.controller;

import com.brad.pms.ai.connector.AutomaticCommandExecutionService;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewService;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.integration.ai.api.AiAutomaticExecuteRequest;
import com.brad.pms.integration.ai.api.AiOperationPreviewRequest;
import com.brad.pms.integration.ai.api.AiOperationPreviewDTO;
import com.brad.pms.integration.ai.api.AiOperationResultDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiConnectorControllerTest {

    @Test
    void mapsTheNeutralAiRequestToAnAutomaticPmsOperation() {
        AutomaticCommandExecutionService executionService = mock(AutomaticCommandExecutionService.class);
        CommandResult result = new CommandResult("op-1", "SUCCEEDED", "项目已创建", Map.of(), List.of("project-list"));
        when(executionService.execute(any())).thenReturn(result);
        AiConnectorController controller = new AiConnectorController(executionService);

        ResponseResult<AiOperationResultDTO> response = controller.execute(new AiAutomaticExecuteRequest(
                "project.create", Map.of("name", "订单中心"), null, null,
                null, null, "idem-1", "pms-cli", "req-1"));

        assertThat(response.getData().operationId()).isEqualTo(result.operationId());
        assertThat(response.getData().status()).isEqualTo(result.status());
        verify(executionService).execute(any());
    }

    @Test
    void previewsAFirstPartyCliOperationWithoutExecutingIt() {
        AutomaticCommandExecutionService executionService = mock(AutomaticCommandExecutionService.class);
        CommandPreviewService previewService = mock(CommandPreviewService.class);
        CommandPreview preview = new CommandPreview("op-preview", CommandName.PROJECT_CREATE,
                Instant.now(), "global:v1", List.of("请确认项目负责人"),
                List.of(Map.of("action", "create", "resource", "project")), List.of("project-list"));
        when(previewService.preview(any())).thenReturn(preview);
        AiConnectorController controller = new AiConnectorController(executionService, null, null, previewService);

        ResponseResult<AiOperationPreviewDTO> response = controller.preview(new AiOperationPreviewRequest(
                "project.create", Map.of("name", "订单中心"), "global:pms", "v1",
                null, null, "pms-cli", "req-preview"));

        assertThat(response.getData().operationId()).isEqualTo("op-preview");
        assertThat(response.getData().changes()).hasSize(1);
        verify(previewService).preview(any());
        verifyNoInteractions(executionService);
    }
}
