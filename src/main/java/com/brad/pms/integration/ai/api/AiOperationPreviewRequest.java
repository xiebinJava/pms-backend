package com.brad.pms.integration.ai.api;

import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.common.exception.BusinessException;

import java.util.Map;

/** Request envelope for the read-only CLI operation preview facade. */
public record AiOperationPreviewRequest(
        String command,
        Map<String, Object> arguments,
        String contextId,
        String contextVersion,
        String contractId,
        String contractVersion,
        String clientId,
        String requestId,
        Map<String, Object> provenance) {

    public AiOperationPreviewRequest(
            String command,
            Map<String, Object> arguments,
            String contextId,
            String contextVersion,
            String contractId,
            String contractVersion,
            String clientId,
            String requestId) {
        this(command, arguments, contextId, contextVersion, contractId, contractVersion,
                clientId, requestId, null);
    }

    public CommandPreviewRequest toCommandPreviewRequest() {
        if (command == null || command.isBlank()) throw BusinessException.error("command 不能为空");
        final CommandName commandName;
        try {
            commandName = CommandName.fromCode(command.trim());
        } catch (IllegalArgumentException exception) {
            throw BusinessException.error("不支持的操作命令: " + command);
        }
        try {
            return new CommandPreviewRequest(commandName, arguments, contextId, contextVersion,
                    contractId, contractVersion, provenance);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.error(exception.getMessage());
        }
    }
}
