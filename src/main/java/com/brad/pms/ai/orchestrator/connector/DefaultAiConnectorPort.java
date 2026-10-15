package com.brad.pms.ai.orchestrator.connector;

import com.brad.pms.ai.command.CommandExecutionService;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandPreviewService;
import com.brad.pms.ai.command.OperationExecuteRequest;
import com.brad.pms.ai.connector.AiConnectorQueryService;
import com.brad.pms.ai.connector.context.ContextReadOptions;
import com.brad.pms.ai.connector.context.AiWorkflowContextReadService;
import com.brad.pms.integration.ai.api.AiOperationPreviewDTO;
import com.brad.pms.integration.ai.api.AiOperationResultDTO;
import com.brad.pms.integration.ai.api.AiResourceDetailDTO;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultAiConnectorPort implements AiConnectorPort {

    private final AiWorkflowContextReadService contextReadService;
    private final CommandPreviewService commandPreviewService;
    private final CommandExecutionService commandExecutionService;
    private final AiConnectorQueryService queryService;

    @Override
    public AiWorkflowContextDTO readContext(String resourceType, Long resourceId, ContextReadOptions options) {
        return contextReadService.context(resourceType, resourceId,
                options == null ? ContextReadOptions.legacy() : options);
    }

    @Override
    public AiOperationPreviewDTO preview(CommandPreviewRequest request) {
        return AiOperationPreviewDTO.from(commandPreviewService.preview(request));
    }

    @Override
    public AiOperationResultDTO execute(OperationExecuteRequest request) {
        return AiOperationResultDTO.from(commandExecutionService.execute(request));
    }

    @Override
    public AiResourceDetailDTO readResource(String resourceType, Long resourceId) {
        return queryService.resource(resourceType, resourceId);
    }
}
