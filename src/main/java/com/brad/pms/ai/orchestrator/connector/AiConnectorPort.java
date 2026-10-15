package com.brad.pms.ai.orchestrator.connector;

import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.OperationExecuteRequest;
import com.brad.pms.ai.connector.context.ContextReadOptions;
import com.brad.pms.integration.ai.api.AiOperationPreviewDTO;
import com.brad.pms.integration.ai.api.AiOperationResultDTO;
import com.brad.pms.integration.ai.api.AiResourceDetailDTO;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;

public interface AiConnectorPort {

    AiWorkflowContextDTO readContext(String resourceType, Long resourceId, ContextReadOptions options);

    AiOperationPreviewDTO preview(CommandPreviewRequest request);

    AiOperationResultDTO execute(OperationExecuteRequest request);

    AiResourceDetailDTO readResource(String resourceType, Long resourceId);
}
