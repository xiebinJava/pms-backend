package com.brad.pms.ai.contract;

import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.PmsCommandRegistry;
import com.brad.pms.workflow.BuiltInWorkflowTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guardrails for the data-driven Agent surface: every built-in workflow node
 * must have a contract, and every contract may only name commands that exist.
 */
class PmsAgentContractSetTest {

    @Test
    void everyBuiltInWorkflowNodeHasExactlyOneContract() throws IOException {
        Map<String, PmsAgentContract> byNodeKey = loadContracts().stream()
                .flatMap(contract -> contract.workflowNodeKeys().stream()
                        .map(nodeKey -> Map.entry(nodeKey, contract)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Set<String> builtInNodeKeys = BuiltInWorkflowTemplate.compatibilityDefinition().nodes().stream()
                .map(node -> node.key())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(builtInNodeKeys).isNotEmpty();
        assertThat(byNodeKey.keySet()).containsExactlyInAnyOrderElementsOf(builtInNodeKeys);
    }

    @Test
    void everyContractOnlyDeclaresRegisteredCommandsAndPolicies() throws IOException {
        PmsCommandRegistry registry = mock(PmsCommandRegistry.class);
        when(registry.list()).thenReturn(Set.of(CommandName.values()));
        for (PmsAgentContract contract : loadContracts()) {
            assertThat(contract.writeCommands())
                    .as(contract.contractKey() + " 写入命令")
                    .isNotEmpty();
            for (String command : contract.writeCommands()) {
                assertThat(CommandName.values())
                        .extracting(CommandName::code)
                        .as(contract.contractKey() + " 命令 " + command)
                        .contains(command);
                assertThat(contract.confirmationPolicies()).containsKey(command);
            }
            assertThat(contract.readToolBindings().values())
                    .as(contract.contractKey() + " 读取工具")
                    .allMatch(tool -> tool.startsWith("pms_"));
        }
    }

    private static List<PmsAgentContract> loadContracts() throws IOException {
        PmsCommandRegistry registry = mock(PmsCommandRegistry.class);
        when(registry.list()).thenReturn(Set.of(CommandName.values()));
        Set<String> commands = java.util.Arrays.stream(CommandName.values())
                .map(CommandName::code).collect(Collectors.toSet());
        Set<String> readTools = Set.of(
                "pms_project_list", "pms_project_get", "pms_task_list", "pms_people_list", "pms_query");
        PmsAgentContractLoader loader = new PmsAgentContractLoader(commands, readTools);
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:agent-contracts/**/*.yaml");
        assertThat(resources).isNotEmpty();
        List<PmsAgentContract> contracts = new ArrayList<>();
        Function<Resource, PmsAgentContract> load = loader::load;
        for (Resource resource : resources) {
            contracts.add(load.apply(resource));
        }
        return contracts;
    }
}
