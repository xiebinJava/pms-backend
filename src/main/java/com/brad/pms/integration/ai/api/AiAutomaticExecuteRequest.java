package com.brad.pms.integration.ai.api;

import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.connector.AutomaticOperationRequest;
import com.brad.pms.common.exception.BusinessException;

import java.util.Map;

/** JSON request accepted by the neutral AI connector execution endpoint. */
public record AiAutomaticExecuteRequest(
        String command,
        Map<String, Object> arguments,
        String contextId,
        String contextVersion,
        String contractId,
        String contractVersion,
        String idempotencyKey,
        String clientId,
        String requestId) {

    public AutomaticOperationRequest toOperationRequest() {
        if (command == null || command.isBlank()) {
            throw BusinessException.error("command 不能为空");
        }
        final CommandName commandName;
        try {
            commandName = CommandName.fromCode(command.trim());
        } catch (IllegalArgumentException e) {
            throw BusinessException.error("不支持的操作命令: " + command);
        }
        try {
            return new AutomaticOperationRequest(
                    commandName,
                    arguments,
                    contextId,
                    contextVersion,
                    contractId,
                    contractVersion,
                    idempotencyKey,
                    clientId,
                    requestId);
        } catch (IllegalArgumentException e) {
            throw BusinessException.error(e.getMessage());
        }
    }
}
