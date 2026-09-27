package com.brad.pms.ai.command;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.AiOperationDO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared argument, preview and result plumbing for connector commands. */
@Component
@RequiredArgsConstructor
public class PmsCommandSupport {

    private final ObjectMapper objectMapper;

    public Map<String, Object> readArguments(AiOperationDO operation, String command) {
        try {
            return objectMapper.readValue(operation.getArgumentsJson(), new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw BusinessException.error(command + " 参数无效");
        }
    }

    public void rejectUnknown(Map<String, Object> arguments, Set<String> allowed, String command) {
        CommandArgumentReader.rejectUnknown(arguments, allowed, command);
    }

    public CommandPreview preview(CommandPreviewRequest request, CommandName name, String description,
                                  Map<String, Object> change, List<String> warnings,
                                  List<String> refreshScopes) {
        return new CommandPreview(null, name, Instant.now().plusSeconds(600), request.contextVersion(),
                warnings, change == null ? List.of() : List.of(new LinkedHashMap<>(change)), refreshScopes);
    }

    public CommandResult result(AiOperationDO operation, String message, Map<String, Object> data,
                                List<String> refreshScopes) {
        return new CommandResult(operation.getId(), "SUCCEEDED", message, data, refreshScopes);
    }

    public Map<String, JsonNode> fieldValues(Map<String, Object> arguments, String key) {
        Object raw = arguments.get(key);
        if (raw == null) return null;
        if (!(raw instanceof Map<?, ?>)) throw BusinessException.error("参数 " + key + " 必须是对象");
        try {
            return objectMapper.convertValue(raw, new TypeReference<>() { });
        } catch (IllegalArgumentException e) {
            throw BusinessException.error("参数 " + key + " 格式不正确");
        }
    }

    public Map<String, Object> copy(Map<String, Object> values) {
        return values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
    }
}
