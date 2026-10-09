package com.brad.pms.ai.command;

import com.brad.pms.ai.contract.PmsAgentContractRegistry;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.mapper.AiOperationMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
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
    private static final String AUTOMATIC_RUNNING = "AUTOMATIC_RUNNING";
    private static final String SUCCEEDED = "SUCCEEDED";
    private static final String EXPIRED = "EXPIRED";
    private static final String AUTOMATIC = "AUTOMATIC";
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
        PmsCommandScopeGuard.requireScope(commandName);
        CommandResult result = registry.require(commandName).execute(operation);
        operation.setIdempotencyKey(request.idempotencyKey());
        operation.setResultJson(write(result));
        operation.setStatus(SUCCEEDED);
        operation.setExecutedAt(LocalDateTime.now());
        operationMapper.updateById(operation);
        return result;
    }

    /**
     * Executes a connector request atomically without exposing the legacy
     * preview/confirm lifecycle. The command registry and command services
     * remain the only source of business behavior.
     *
     * The first idempotency lookup uses a user/key unique index. Under MySQL's
     * default REPEATABLE_READ isolation, two different new keys can acquire
     * overlapping gap locks and deadlock before either command is persisted.
     * READ_COMMITTED keeps the lookup's FOR UPDATE lock on existing rows while
     * allowing independent automatic operations to reserve their keys safely.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CommandResult executeAutomatically(Long userId,
                                              CommandPreviewRequest request,
                                              String idempotencyKey,
                                              String sourceClient,
                                              String requestId) {
        if (userId == null) throw BusinessException.unauthorized("未登录");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw BusinessException.error("idempotencyKey 不能为空");
        }
        if (sourceClient == null || sourceClient.isBlank()) {
            throw BusinessException.error("clientId 不能为空");
        }
        if (requestId == null || requestId.isBlank()) {
            throw BusinessException.error("requestId 不能为空");
        }

        validateContractBinding(request.contractId(), request.contractVersion(), request.name().code());
        PmsCommandScopeGuard.requireScope(request.name());

        AiOperationDO existing = operationMapper.selectByUserIdAndIdempotencyKeyForUpdate(userId, idempotencyKey);
        if (existing != null) {
            if (!matchesAutomaticRequest(existing, request, sourceClient)) {
                throw BusinessException.conflict("幂等键已经用于其他操作，请更换幂等键");
            }
            if (SUCCEEDED.equals(existing.getStatus())) {
                return read(existing.getResultJson(), CommandResult.class);
            }
            throw BusinessException.conflict("相同幂等键的操作正在处理或不可重试");
        }

        PmsCommand command = registry.require(request.name());
        CommandPreview proposal = command.preview(request);
        LocalDateTime now = LocalDateTime.now();
        AiOperationDO operation = new AiOperationDO();
        operation.setId(UUID.randomUUID().toString());
        operation.setCommandName(request.name().code());
        operation.setUserId(userId);
        operation.setSourceClient(sourceClient);
        operation.setRequestId(requestId);
        operation.setExecutionMode(AUTOMATIC);
        operation.setContextId(request.contextId());
        operation.setContextVersion(request.contextVersion());
        operation.setContractId(request.contractId());
        operation.setContractVersion(request.contractVersion());
        operation.setArgumentsJson(write(request.arguments()));
        operation.setPreviewJson(write(proposal));
        operation.setExpectedVersionsJson(write(proposal.changes()));
        operation.setStatus(AUTOMATIC_RUNNING);
        operation.setIdempotencyKey(idempotencyKey);
        operation.setExpiresAt(now.plusMinutes(PREVIEW_TTL_MINUTES));
        try {
            operationMapper.insert(operation);
        } catch (DuplicateKeyException duplicateKey) {
            // A concurrent request with the same key may have won the unique
            // reservation between the initial lookup and this insert. Re-read
            // the winner and preserve normal idempotency semantics instead of
            // leaking a database 500 to the connector.
            AiOperationDO existingAfterRace = operationMapper
                    .selectByUserIdAndIdempotencyKeyForUpdate(userId, idempotencyKey);
            if (existingAfterRace != null
                    && matchesAutomaticRequest(existingAfterRace, request, sourceClient)) {
                if (SUCCEEDED.equals(existingAfterRace.getStatus())) {
                    return read(existingAfterRace.getResultJson(), CommandResult.class);
                }
                throw BusinessException.conflict("相同幂等键的操作正在处理或不可重试");
            }
            throw BusinessException.conflict("幂等键已经用于其他操作，请更换幂等键");
        }

        CommandResult result = command.execute(operation);
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

    private boolean matchesAutomaticRequest(AiOperationDO operation,
                                             CommandPreviewRequest request,
                                             String sourceClient) {
        return Objects.equals(operation.getCommandName(), request.name().code())
                && Objects.equals(operation.getSourceClient(), sourceClient)
                && Objects.equals(operation.getContextId(), request.contextId())
                && Objects.equals(operation.getContextVersion(), request.contextVersion())
                && Objects.equals(operation.getContractId(), request.contractId())
                && Objects.equals(operation.getContractVersion(), request.contractVersion())
                && jsonEquals(operation.getArgumentsJson(), request.arguments());
    }

    private boolean jsonEquals(String storedJson, Object currentValue) {
        if (storedJson == null) return currentValue == null;
        try {
            return objectMapper.readTree(storedJson).equals(objectMapper.valueToTree(currentValue));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return false;
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
