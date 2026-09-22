package com.brad.pms.ai.contract;

import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.PmsCommandRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Runtime registry for versioned PMS Agent execution contracts. */
@Component
public class PmsAgentContractRegistry {

    private static final Logger log = LoggerFactory.getLogger(PmsAgentContractRegistry.class);
    private static final Set<String> PUBLISHED_READ_TOOLS = Set.of(
            "pms_project_list", "pms_project_get", "pms_task_list", "pms_people_list", "pms_query");
    private static final String RESOURCE_PATTERN = "classpath*:agent-contracts/**/*.yaml";

    private final PmsAgentContractLoader loader;
    private final ResourcePatternResolver resolver;
    private volatile Map<String, PmsAgentContract> contracts = Map.of();
    private volatile String loadError;

    @Autowired
    public PmsAgentContractRegistry(PmsCommandRegistry commandRegistry) {
        this(new PmsAgentContractLoader(
                commandRegistry.list().stream().map(CommandName::code).collect(Collectors.toSet()),
                PUBLISHED_READ_TOOLS), new PathMatchingResourcePatternResolver());
    }

    PmsAgentContractRegistry(PmsAgentContractLoader loader, ResourcePatternResolver resolver) {
        this.loader = loader;
        this.resolver = resolver;
    }

    /** Test/support constructor for an already validated contract set. */
    public PmsAgentContractRegistry(Collection<PmsAgentContract> initialContracts) {
        this.loader = null;
        this.resolver = null;
        load(initialContracts);
    }

    @PostConstruct
    public void initialize() {
        if (loader == null) return;
        try {
            Resource[] resources = resolver.getResources(RESOURCE_PATTERN);
            if (resources.length == 0) throw new IllegalArgumentException("未发现 Agent 契约资源");
            Map<String, PmsAgentContract> loaded = new LinkedHashMap<>();
            for (Resource resource : resources) {
                PmsAgentContract contract = loader.load(resource);
                putUnique(loaded, contract);
            }
            contracts = Map.copyOf(loaded);
            loadError = null;
        } catch (IOException | RuntimeException ex) {
            contracts = Map.of();
            loadError = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            log.error("PMS Agent 契约加载失败，已关闭契约驱动的 Agent 能力: {}", loadError, ex);
        }
    }

    public void load(Collection<PmsAgentContract> values) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("契约集合不能为空");
        Map<String, PmsAgentContract> loaded = new LinkedHashMap<>();
        for (PmsAgentContract contract : values) putUnique(loaded, contract);
        contracts = Map.copyOf(loaded);
        loadError = null;
    }

    public Optional<PmsAgentContract> find(String agentId, String contractKey) {
        return Optional.ofNullable(contracts.get(identity(agentId, contractKey)));
    }

    public boolean isCurrentVersionAllowed(String contractId, String contractVersion) {
        if (loadError != null || contractId == null || contractVersion == null) return false;
        return contracts.values().stream().anyMatch(contract ->
                contract.contractId().equals(contractId)
                        && contract.contractVersion().equals(contractVersion));
    }

    public boolean isCommandAllowed(String contractId, String contractVersion, String commandName) {
        if (loadError != null || contractId == null || contractVersion == null || commandName == null) return false;
        return contracts.values().stream().anyMatch(contract ->
                contract.contractId().equals(contractId)
                        && contract.contractVersion().equals(contractVersion)
                        && contract.writeCommands().contains(commandName));
    }

    public PmsAgentContract require(String agentId, String contractKey) {
        if (loadError != null) throw new IllegalStateException("PMS Agent 契约不可用: " + loadError);
        return find(agentId, contractKey)
                .orElseThrow(() -> new IllegalArgumentException("未找到 Agent 契约: " + agentId + "/" + contractKey));
    }

    public List<PmsAgentContract> list() {
        return List.copyOf(contracts.values());
    }

    public boolean healthy() {
        return loadError == null;
    }

    public String loadError() {
        return loadError;
    }

    private static void putUnique(Map<String, PmsAgentContract> target, PmsAgentContract contract) {
        if (contract == null) throw new IllegalArgumentException("契约不能为空");
        String identity = identity(contract.agentId(), contract.contractKey());
        if (target.containsKey(identity)) {
            throw new IllegalArgumentException("契约标识重复: " + identity);
        }
        if (target.values().stream().anyMatch(existing ->
                existing.contractId().equals(contract.contractId())
                        && existing.contractVersion().equals(contract.contractVersion()))) {
            throw new IllegalArgumentException("契约版本重复: " + contract.contractId() + "/" + contract.contractVersion());
        }
        target.put(identity, contract);
    }

    private static String identity(String agentId, String contractKey) {
        if (agentId == null || agentId.isBlank() || contractKey == null || contractKey.isBlank()) {
            throw new IllegalArgumentException("契约身份不能为空");
        }
        return agentId + "/" + contractKey;
    }
}
