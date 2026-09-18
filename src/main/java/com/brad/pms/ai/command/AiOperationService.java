package com.brad.pms.ai.command;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.mapper.AiOperationMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiOperationService {

    private static final String PREVIEW = "PREVIEW";
    private static final String SUCCEEDED = "SUCCEEDED";
    private static final String EXPIRED = "EXPIRED";
    private static final int PREVIEW_TTL_MINUTES = 10;

    private final AiOperationMapper operationMapper;
    private final PmsCommandRegistry registry;
    private final ObjectMapper objectMapper;

    @Transactional
    public CommandPreview persistPreview(Long userId, CommandPreviewRequest request, CommandPreview proposal) {
        if (userId == null) throw BusinessException.unauthorized("未登录");
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(PREVIEW_TTL_MINUTES);
        String operationId = UUID.randomUUID().toString();
        AiOperationDO operation = new AiOperationDO();
        operation.setId(operationId);
        operation.setCommandName(request.name().code());
        operation.setUserId(userId);
        operation.setContextId(request.contextId());
        operation.setContextVersion(request.contextVersion());
        operation.setArgumentsJson(write(request.arguments()));
        operation.setPreviewJson(write(proposal));
        operation.setExpectedVersionsJson(write(proposal.changes()));
        operation.setStatus(PREVIEW);
        operation.setExpiresAt(expiresAt);
        operationMapper.insert(operation);
        return new CommandPreview(operationId, proposal.command(), expiresAt.atZone(ZoneId.systemDefault()).toInstant(), proposal.contextVersion(),
                proposal.warnings(), proposal.changes(), proposal.refreshScopes());
    }

    @Transactional
    public CommandResult execute(Long userId, OperationExecuteRequest request) {
        AiOperationDO operation = operationMapper.selectByIdForUpdate(request.operationId());
        if (operation == null) throw BusinessException.notFound("操作预览不存在");
        if (!operation.getUserId().equals(userId)) throw BusinessException.forbidden("无权执行此操作");
        if (SUCCEEDED.equals(operation.getStatus())) {
            if (!request.idempotencyKey().equals(operation.getIdempotencyKey())) {
                throw BusinessException.conflict("该操作已经执行，请使用原幂等键查询结果");
            }
            return read(operation.getResultJson(), CommandResult.class);
        }
        if (!PREVIEW.equals(operation.getStatus())) {
            throw BusinessException.conflict("操作当前状态不可执行");
        }
        if (operation.getExpiresAt() == null || operation.getExpiresAt().isBefore(LocalDateTime.now())) {
            operation.setStatus(EXPIRED);
            operationMapper.updateById(operation);
            throw BusinessException.conflict("操作预览已过期，请重新生成预览");
        }

        CommandName commandName = parseCommandName(operation.getCommandName());
        CommandResult result = registry.require(commandName).execute(operation);
        operation.setIdempotencyKey(request.idempotencyKey());
        operation.setResultJson(write(result));
        operation.setStatus(SUCCEEDED);
        operation.setExecutedAt(LocalDateTime.now());
        operationMapper.updateById(operation);
        return result;
    }

    public Map<String, Object> arguments(AiOperationDO operation) {
        return read(operation.getArgumentsJson(), new TypeReference<>() { });
    }

    public List<Map<String, Object>> expectedChanges(AiOperationDO operation) {
        return read(operation.getExpectedVersionsJson(), new TypeReference<>() { });
    }

    private CommandName parseCommandName(String value) {
        for (CommandName name : CommandName.values()) {
            if (name.code().equals(value)) return name;
        }
        throw BusinessException.error("不支持的操作命令: " + value);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化 AI 操作", e);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法读取 AI 操作", e);
        }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法读取 AI 操作", e);
        }
    }
}
