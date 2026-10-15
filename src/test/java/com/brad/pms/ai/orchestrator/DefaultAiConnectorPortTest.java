package com.brad.pms.ai.orchestrator;

import com.brad.pms.ai.command.CommandExecutionService;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.CommandPreviewService;
import com.brad.pms.ai.command.OperationExecuteRequest;
import com.brad.pms.ai.connector.AiConnectorQueryService;
import com.brad.pms.ai.connector.context.ContextReadOptions;
import com.brad.pms.ai.connector.context.AiWorkflowContextReadService;
import com.brad.pms.ai.orchestrator.connector.AiConnectorPort;
import com.brad.pms.ai.orchestrator.connector.DefaultAiConnectorPort;
import com.brad.pms.integration.ai.api.AiOperationPreviewDTO;
import com.brad.pms.integration.ai.api.AiOperationResultDTO;
import com.brad.pms.integration.ai.api.AiResourceDetailDTO;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAiConnectorPortTest {

    @Test
    void delegatesReadsAndCommandsThroughExistingServices() {
        AiWorkflowContextReadService contextService = mock(AiWorkflowContextReadService.class);
        CommandPreviewService previewService = mock(CommandPreviewService.class);
        CommandExecutionService executionService = mock(CommandExecutionService.class);
        AiConnectorQueryService queryService = mock(AiConnectorQueryService.class);
        AiConnectorPort port = new DefaultAiConnectorPort(contextService, previewService,
                executionService, queryService);

        AiWorkflowContextDTO context = new AiWorkflowContextDTO("requirement", 33L, 4,
                null, null, List.of());
        AiResourceDetailDTO resource = new AiResourceDetailDTO("requirement", 33L, 4,
                Map.of("title", "需求"));
        CommandPreview preview = new CommandPreview("op-1", CommandName.REQUIREMENT_ANALYSIS_SAVE,
                Instant.now().plusSeconds(600), "4", List.of(), List.of(), List.of());
        CommandResult result = new CommandResult("op-1", "SUCCEEDED", "已保存", Map.of(), List.of());
        when(contextService.context("requirement", 33L, ContextReadOptions.legacy())).thenReturn(context);
        when(queryService.resource("requirement", 33L)).thenReturn(resource);
        when(previewService.preview(any(CommandPreviewRequest.class))).thenReturn(preview);
        when(executionService.execute(any(OperationExecuteRequest.class))).thenReturn(result);

        assertThat(port.readContext("requirement", 33L, ContextReadOptions.legacy())).isSameAs(context);
        assertThat(port.readResource("requirement", 33L)).isSameAs(resource);
        assertThat(port.preview(new CommandPreviewRequest(CommandName.REQUIREMENT_ANALYSIS_SAVE,
                Map.of("analysis", "内容"), "requirement:33", "4")))
                .isEqualTo(AiOperationPreviewDTO.from(preview));
        assertThat(port.execute(new OperationExecuteRequest("op-1", "idem-1")))
                .isEqualTo(AiOperationResultDTO.from(result));

        verify(contextService).context("requirement", 33L, ContextReadOptions.legacy());
        verify(queryService).resource("requirement", 33L);
        verify(previewService).preview(any(CommandPreviewRequest.class));
        verify(executionService).execute(any(OperationExecuteRequest.class));
    }
}
