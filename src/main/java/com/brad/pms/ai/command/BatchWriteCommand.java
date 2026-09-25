package com.brad.pms.ai.command;

import com.brad.pms.ai.contract.PmsAgentContractRegistry;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.AiOperationDO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs several registered PMS writes as one preview and one execution.
 *
 * <p>Batch creation is the reason this exists: creating twenty tasks, adding a
 * team, or filling a whole node should cost the caller one decision and one
 * audited operation instead of twenty. Each child command keeps its own
 * permission, scope, contract, version and idempotency behaviour, and because
 * the whole batch runs in the caller's transaction a failure rolls all of it
 * back.</p>
 */
@Component
public class BatchWriteCommand implements PmsCommand {

    private static final Set<String> ALLOWED_ARGUMENTS = Set.of("operations");
    private static final int MAX_OPERATIONS = 20;
    private static final List<String> REFRESH_SCOPES =
            List.of("project-detail", "project-list", "project-dashboard", "task-board");

    private final PmsCommandRegistry registry;
    private final PmsAgentContractRegistry contractRegistry;
    private final ObjectMapper objectMapper;

    /**
     * The registry holds every command, including this one, so the lookup is
     * injected lazily: a batch runs other commands, never itself.
     */
    public BatchWriteCommand(@Lazy PmsCommandRegistry registry,
                             @Lazy PmsAgentContractRegistry contractRegistry,
                             ObjectMapper objectMapper) {
        this.registry = registry;
        this.contractRegistry = contractRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public CommandName name() {
        return CommandName.BATCH_WRITE;
    }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> arguments = request.arguments();
        CommandArgumentReader.rejectUnknown(arguments, ALLOWED_ARGUMENTS, "batch.write");
        List<BatchItem> items = readItems(arguments);
        requireAllowed(request.contractId(), request.contractVersion(), items);

        List<Map<String, Object>> itemChanges = new ArrayList<>();
        LinkedHashSet<String> warnings = new LinkedHashSet<>();
        for (BatchItem item : items) {
            CommandName childName = childName(item.command());
            PmsCommandScopeGuard.requireScope(childName);
            CommandPreview child = registry.require(childName).preview(new CommandPreviewRequest(
                    childName, item.arguments(), request.contextId(), request.contextVersion()));
            warnings.addAll(child.warnings());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("command", childName.code());
            entry.put("arguments", item.arguments());
            entry.put("changes", child.changes());
            itemChanges.add(entry);
        }

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "batch");
        change.put("action", "write");
        change.put("itemCount", items.size());
        change.put("commands", items.stream().map(BatchItem::command).toList());
        change.put("items", itemChanges);
        List<String> previewWarnings = new ArrayList<>();
        previewWarnings.add("批量操作共 " + items.size() + " 条，按顺序执行，任一条失败则整批回滚");
        previewWarnings.addAll(warnings);
        return new CommandPreview(null, name(), Instant.now().plusSeconds(600), request.contextVersion(),
                previewWarnings, List.of(change), REFRESH_SCOPES);
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = readArguments(operation.getArgumentsJson());
        CommandArgumentReader.rejectUnknown(arguments, ALLOWED_ARGUMENTS, "batch.write");
        List<BatchItem> items = readItems(arguments);
        requireAllowed(operation.getContractId(), operation.getContractVersion(), items);
        List<Map<String, Object>> expected = expectedChanges(operation);

        List<Map<String, Object>> results = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            BatchItem item = items.get(index);
            CommandName childName = childName(item.command());
            PmsCommandScopeGuard.requireScope(childName);
            AiOperationDO child = new AiOperationDO();
            child.setId(operation.getId() + "#" + index);
            child.setCommandName(childName.code());
            child.setArgumentsJson(write(item.arguments()));
            child.setExpectedVersionsJson(write(childExpectations(expected, index)));
            child.setContractId(operation.getContractId());
            child.setContractVersion(operation.getContractVersion());
            CommandResult result;
            try {
                result = registry.require(childName).execute(child);
            } catch (BusinessException failure) {
                // Report which item failed and that the whole batch rolled back:
                // a bare child message ("项目已发生变化") is unreadable in a batch.
                throw new BusinessException(failure.getCode(), "批量操作第 " + (index + 1) + " 条（"
                        + childName.code() + "）失败：" + failure.getMessage() + "；整批已回滚，请重新生成预览");
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("command", childName.code());
            entry.put("status", result.status());
            entry.put("message", result.message());
            entry.put("data", result.data());
            results.add(entry);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemCount", items.size());
        data.put("results", results);
        return new CommandResult(operation.getId(), "SUCCEEDED",
                "批量操作已完成（" + items.size() + " 条）", data, REFRESH_SCOPES);
    }

    private void requireAllowed(String contractId, String contractVersion, List<BatchItem> items) {
        if (contractId == null || contractVersion == null) return;
        for (BatchItem item : items) {
            if (!contractRegistry.isCommandAllowed(contractId, contractVersion, item.command())) {
                throw BusinessException.forbidden("当前节点契约不允许批量执行命令: " + item.command());
            }
        }
    }

    private CommandName childName(String command) {
        CommandName name;
        try {
            name = CommandName.fromCode(command);
        } catch (IllegalArgumentException ex) {
            throw BusinessException.error("批量操作包含不支持的命令: " + command);
        }
        if (name == CommandName.BATCH_WRITE) {
            throw BusinessException.error("批量操作不能嵌套批量操作");
        }
        return name;
    }

    @SuppressWarnings("unchecked")
    private List<BatchItem> readItems(Map<String, Object> arguments) {
        Object raw = arguments.get("operations");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw BusinessException.error("operations 必须包含至少一条子操作");
        }
        if (list.size() > MAX_OPERATIONS) {
            throw BusinessException.error("一次批量操作最多 " + MAX_OPERATIONS + " 条，请拆分为多次");
        }
        List<BatchItem> items = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> map)) {
                throw BusinessException.error("operations 的每一项都必须是 {command, arguments} 对象");
            }
            Object command = map.get("command");
            if (!(command instanceof String code) || code.isBlank()) {
                throw BusinessException.error("批量子操作缺少 command");
            }
            Object childArguments = map.get("arguments");
            Map<String, Object> argumentsMap = childArguments instanceof Map<?, ?> childMap
                    ? (Map<String, Object>) childMap
                    : Map.of();
            items.add(new BatchItem(code.trim(), argumentsMap));
        }
        return items;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> childExpectations(List<Map<String, Object>> expected, int index) {
        if (expected.isEmpty()) return List.of();
        Object items = expected.get(0).get("items");
        if (!(items instanceof List<?> list) || index >= list.size()) return List.of();
        Object entry = list.get(index);
        if (!(entry instanceof Map<?, ?> map)) return List.of();
        Object changes = ((Map<String, Object>) map).get("changes");
        return changes instanceof List<?> list2 ? (List<Map<String, Object>>) list2 : List.of();
    }

    private List<Map<String, Object>> expectedChanges(AiOperationDO operation) {
        try {
            return objectMapper.readValue(operation.getExpectedVersionsJson(), new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw BusinessException.error("批量操作预览版本信息无效");
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw BusinessException.error("批量操作参数无效");
        }
    }

    private Map<String, Object> readArguments(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw BusinessException.error("批量操作参数无效");
        }
    }

    private record BatchItem(String command, Map<String, Object> arguments) {
    }
}
