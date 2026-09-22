package com.brad.pms.ai.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Parses and validates the machine-readable PMS Agent contract. */
public final class PmsAgentContractLoader {

    private static final Pattern AGENT_ID = Pattern.compile("[a-zA-Z0-9_-]{1,64}");
    private static final Pattern VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+");
    /**
     * Accepted confirmation policies. `preview-and-confirm` keeps the human in
     * the loop; `preview-then-execute` is the same preview/execute chain without
     * waiting for a per-write confirmation, for deployments whose caller already
     * holds the account's own write authority.
     */
    private static final Set<String> CONFIRMATION_POLICIES =
            Set.of("preview-and-confirm", "preview-then-execute");

    private final ObjectMapper yamlMapper;
    private final Set<String> supportedWriteCommands;
    private final Set<String> supportedReadTools;

    public PmsAgentContractLoader(Set<String> supportedWriteCommands, Set<String> supportedReadTools) {
        this(new ObjectMapper(new YAMLFactory()), supportedWriteCommands, supportedReadTools);
    }

    PmsAgentContractLoader(ObjectMapper yamlMapper,
                           Set<String> supportedWriteCommands,
                           Set<String> supportedReadTools) {
        this.yamlMapper = yamlMapper;
        this.supportedWriteCommands = Set.copyOf(supportedWriteCommands);
        this.supportedReadTools = Set.copyOf(supportedReadTools);
    }

    public PmsAgentContract load(Resource resource) {
        if (resource == null) throw new IllegalArgumentException("契约资源不能为空");
        try {
            PmsAgentContract contract = yamlMapper.readValue(resource.getInputStream(), PmsAgentContract.class);
            validate(contract);
            return contract;
        } catch (IOException ex) {
            throw new IllegalArgumentException("无法读取 Agent 契约: " + resource.getDescription(), ex);
        }
    }

    private void validate(PmsAgentContract contract) {
        requireText(contract.contractId(), "contractId");
        requireText(contract.agentId(), "agentId");
        requireText(contract.contractKey(), "contractKey");
        requireText(contract.contractVersion(), "contractVersion");
        requireText(contract.locale(), "locale");
        requireText(contract.principalRole(), "principalRole");
        if (!AGENT_ID.matcher(contract.agentId()).matches()) {
            throw new IllegalArgumentException("Agent 标识格式无效: " + contract.agentId());
        }
        if (!VERSION.matcher(contract.contractVersion()).matches()) {
            throw new IllegalArgumentException("契约版本格式无效: " + contract.contractVersion());
        }
        requireNonEmpty(contract.workflowNodeKeys(), "workflowNodeKeys");
        requireNonEmpty(contract.readCapabilities(), "readCapabilities");
        requireNonEmpty(contract.readToolBindings(), "readToolBindings");
        requireNonEmpty(contract.writeCommands(), "writeCommands");
        requireNonEmpty(contract.entryConditions(), "entryConditions");
        requireNonEmpty(contract.inputs(), "inputs");
        requireNonEmpty(contract.missingInputRules(), "missingInputRules");
        requireNonEmpty(contract.executionSteps(), "executionSteps");
        requireNonEmpty(contract.completionCriteria(), "completionCriteria");
        requireNonEmpty(contract.failureStrategies(), "failureStrategies");
        requireNonEmpty(contract.terminationConditions(), "terminationConditions");

        if (!contract.specializedAgents().isEmpty()) {
            throw new IllegalArgumentException("本节点不允许绑定专业 Agent");
        }
        for (String command : contract.writeCommands()) {
            requireText(command, "writeCommands");
            if (!supportedWriteCommands.contains(command)) {
                throw new IllegalArgumentException("未注册的写入命令: " + command);
            }
            if (!contract.confirmationPolicies().containsKey(command)) {
                throw new IllegalArgumentException("写入命令缺少确认策略: " + command);
            }
        }
        for (Map.Entry<String, String> binding : contract.readToolBindings().entrySet()) {
            requireText(binding.getKey(), "readToolBindings");
            requireText(binding.getValue(), "readToolBindings");
            if (!supportedReadTools.contains(binding.getValue())) {
                throw new IllegalArgumentException("未发布的读取工具: " + binding.getValue());
            }
        }
        for (String command : contract.confirmationPolicies().keySet()) {
            if (!contract.writeCommands().contains(command)) {
                throw new IllegalArgumentException("确认策略引用了未声明的命令: " + command);
            }
        }
        for (Map.Entry<String, String> policy : contract.confirmationPolicies().entrySet()) {
            if (!CONFIRMATION_POLICIES.contains(policy.getValue())) {
                throw new IllegalArgumentException("确认策略无效: " + policy.getValue());
            }
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("契约字段不能为空: " + field);
    }

    private static void requireNonEmpty(List<?> values, String field) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("契约字段不能为空: " + field);
    }

    private static void requireNonEmpty(Map<?, ?> values, String field) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("契约字段不能为空: " + field);
    }
}
