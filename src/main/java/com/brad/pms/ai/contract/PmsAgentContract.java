package com.brad.pms.ai.contract;

import java.util.List;
import java.util.Map;

/** Immutable, versioned execution contract for one PMS Agent and workflow node. */
public record PmsAgentContract(
        String contractId,
        String agentId,
        String contractKey,
        List<String> workflowNodeKeys,
        String contractVersion,
        boolean required,
        String locale,
        String principalRole,
        List<String> specializedAgents,
        List<String> readCapabilities,
        Map<String, String> readToolBindings,
        List<String> writeCommands,
        Map<String, String> confirmationPolicies,
        List<String> entryConditions,
        List<String> inputs,
        List<String> missingInputRules,
        List<String> executionSteps,
        List<String> completionCriteria,
        List<String> failureStrategies,
        List<String> terminationConditions) {

    public PmsAgentContract {
        workflowNodeKeys = copy(workflowNodeKeys);
        specializedAgents = copy(specializedAgents);
        readCapabilities = copy(readCapabilities);
        readToolBindings = readToolBindings == null ? Map.of() : Map.copyOf(readToolBindings);
        writeCommands = copy(writeCommands);
        confirmationPolicies = confirmationPolicies == null ? Map.of() : Map.copyOf(confirmationPolicies);
        entryConditions = copy(entryConditions);
        inputs = copy(inputs);
        missingInputRules = copy(missingInputRules);
        executionSteps = copy(executionSteps);
        completionCriteria = copy(completionCriteria);
        failureStrategies = copy(failureStrategies);
        terminationConditions = copy(terminationConditions);
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
