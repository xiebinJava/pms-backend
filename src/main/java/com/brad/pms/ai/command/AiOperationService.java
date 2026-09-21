package com.brad.pms.ai.command;

import com.brad.pms.ai.contract.PmsAgentContractRegistry;
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
import java.util.Objects;
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
    private final PmsAgentContractRegistry contractRegistry;

    @Transactional
    public CommandPreview persistPreview(Long userId, CommandPreviewRequest request, CommandPreview proposal) {
        if (userId == null) throw BusinessException.unauthorized("未登录");
        validateContractBinding(request.contractId(), request.contractVersion(), request.name().code());
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(PREVIEW_TTL_MINUTES);
        String operationId = UUID.randomUUID().toString();
        AiOperationDO operation = new AiOperationDO();
        operation.setId(operationId);
        operation.setCommandName(request.name().code());
        operation.setUserId(userId);
        operation.setContextId(request.contextId());
        operation.setContextVersion(request.contextVersion());
        operation.setContractId(request.contractId());
        operation.setContractVersion(request.contractVersion());
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
        validateExecutionBinding(operation, request);
        validateContractBinding(operation.getContractId(), operation.getContractVersion(), operation.getCommandName());

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

    private void validateContractBinding(String contractId, String contractVersion, String commandName) {
        if (contractId == null && contractVersion == null) return;
        if (!contractRegistry.isCommandAllowed(contractId, contractVersion, commandName)) {
            throw BusinessException.conflict("契约版本或操作命令未获授权，请重新生成预览");
        }
    }

    private void validateExecutionBinding(AiOperationDO operation, OperationExecuteRequest request) {
        if (operation.getContractId() != null
                && (!Objects.equals(operation.getContractId(), request.contractId())
                || !Objects.equals(operation.getContractVersion(), request.contractVersion()))) {
            throw BusinessException.conflict("执行请求与原契约不一致，请重新生成预览");
        }
        if (request.contextId() != null
                && (!Objects.equals(request.contextId(), operation.getContextId())
                || !Objects.equals(request.contextVersion(), operation.getContextVersion()))) {
            throw BusinessException.conflict("执行请求与原项目上下文不一致，请重新生成预览");
        }
        if (operation.getContractId() == null && request.contractId() != null) {
            throw BusinessException.conflict("原操作预览未绑定契约，请重新生成预览");
        }
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
