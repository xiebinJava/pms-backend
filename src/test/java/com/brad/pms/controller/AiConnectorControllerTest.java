package com.brad.pms.controller;

import com.brad.pms.ai.connector.AutomaticCommandExecutionService;
import com.brad.pms.ai.connector.AiConnectorQueryService;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewService;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.integration.ai.api.AiAutomaticExecuteRequest;
import com.brad.pms.integration.ai.api.AiOperationPreviewRequest;
import com.brad.pms.integration.ai.api.AiOperationPreviewDTO;
import com.brad.pms.integration.ai.api.AiOperationResultDTO;
import com.brad.pms.integration.ai.api.AiResourceDetailDTO;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.brad.pms.ai.connector.context.ContextReadOptions;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        assertThat(response.getData().auditId()).isEqualTo(result.operationId());
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

    @Test
    void exposesReadOnlyContextAndResourceRoutesWithParsedOptions() throws Exception {
        AutomaticCommandExecutionService executionService = mock(AutomaticCommandExecutionService.class);
        AiConnectorQueryService queryService = mock(AiConnectorQueryService.class);
        ContextReadOptions options = new ContextReadOptions("focused", 301L, "solution-design");
        when(queryService.context("project", 7L, options)).thenReturn(
                new AiWorkflowContextDTO("project", 7L, 3, null, null, List.of()));
        when(queryService.resource("project", 7L)).thenReturn(
                new AiResourceDetailDTO("project", 7L, 3, Map.of("name", "订单中心")));
        AiConnectorController controller = new AiConnectorController(
                executionService, null, queryService, null);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/integration/ai/v1/context/project/7")
                        .param("view", "focused")
                        .param("nodeId", "301")
                        .param("componentKey", "solution-design"))
                .andExpect(status().isOk());
        mvc.perform(get("/integration/ai/v1/resources/project/7"))
                .andExpect(status().isOk());

        verify(queryService).context("project", 7L, options);
        verify(queryService).resource("project", 7L);
    }
}
