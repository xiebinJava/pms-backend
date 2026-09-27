package com.brad.pms.controller;

import com.brad.pms.ai.connector.AutomaticCommandExecutionService;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.integration.ai.api.AiAutomaticExecuteRequest;
import com.brad.pms.integration.ai.api.AiOperationResultDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
                null, null, "idem-1", "mcp", "req-1"));

        assertThat(response.getData().operationId()).isEqualTo(result.operationId());
        assertThat(response.getData().status()).isEqualTo(result.status());
        verify(executionService).execute(any());
    }
}
