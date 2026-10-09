package com.brad.pms.ai.connector;

import com.brad.pms.ai.command.AiOperationService;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Neutral AI connector write facade.
 *
 * <p>It normalizes the request boundary and delegates the actual preview,
 * transaction, command allow-list, permission and idempotency work to the
 * PMS-owned {@link AiOperationService}.</p>
 */
@Service
@RequiredArgsConstructor
public class AutomaticCommandExecutionService {

    private final AiOperationService operationService;

    public CommandResult execute(AutomaticOperationRequest request) {
        if (request == null) throw BusinessException.error("自动执行请求不能为空");

        Long userId = UserContext.userId();
        Context context = normalizeContext(request);
        CommandPreviewRequest previewRequest = new CommandPreviewRequest(
                request.name(),
                request.arguments(),
                context.id(),
                context.version(),
                request.contractId(),
                request.contractVersion());
        return operationService.executeAutomatically(
                userId,
                previewRequest,
                request.idempotencyKey(),
                request.clientId(),
                request.requestId());
    }

    private Context normalizeContext(AutomaticOperationRequest request) {
        if (request.contextId() != null) {
            return new Context(request.contextId(), request.contextVersion());
        }
        if (requiresConcreteContext(request.name())) {
            throw BusinessException.conflict("节点操作必须提供具体上下文");
        }
        return new Context("global:pms", "v1");
    }

    private boolean requiresConcreteContext(CommandName command) {
        return switch (command) {
            case NODE_COMPLETE, NODE_FIELD_UPDATE, NODE_ROLLBACK,
                 NODE_OWNER_UPDATE, NODE_SCHEDULE_UPDATE, TASK_CREATE,
                 DEVELOPMENT_ITEM_NODE_OWNER_UPDATE,
                 DEVELOPMENT_ITEM_NODE_SCHEDULE_UPDATE,
                 DEVELOPMENT_ITEM_NODE_FIELD_UPDATE,
                 DEVELOPMENT_ITEM_NODE_COMPLETE,
                 DEVELOPMENT_ITEM_TASK_CREATE,
                 ITERATION_PLAN_CREATE, ITERATION_PLAN_UPDATE,
                 ITERATION_PLAN_STORY_ADD, ITERATION_PLAN_STORY_REMOVE -> true;
            default -> false;
        };
    }

    private record Context(String id, String version) {
    }
}
