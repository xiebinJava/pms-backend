package com.brad.pms.integration.dsh.api;

import com.brad.pms.ai.contract.PmsAgentContract;
import com.brad.pms.ai.contract.PmsAgentContractFingerprint;

import java.util.List;
import java.util.Map;

/** Runtime-safe representation of a PMS Agent contract for the DSH plugin. */
public record DshAgentContractDTO(
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
        List<String> terminationConditions,
        String contentSha256) {

    public DshAgentContractDTO {
        workflowNodeKeys = workflowNodeKeys == null ? List.of() : List.copyOf(workflowNodeKeys);
        specializedAgents = specializedAgents == null ? List.of() : List.copyOf(specializedAgents);
        readCapabilities = readCapabilities == null ? List.of() : List.copyOf(readCapabilities);
        readToolBindings = readToolBindings == null ? Map.of() : Map.copyOf(readToolBindings);
        writeCommands = writeCommands == null ? List.of() : List.copyOf(writeCommands);
        confirmationPolicies = confirmationPolicies == null ? Map.of() : Map.copyOf(confirmationPolicies);
        entryConditions = entryConditions == null ? List.of() : List.copyOf(entryConditions);
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        missingInputRules = missingInputRules == null ? List.of() : List.copyOf(missingInputRules);
        executionSteps = executionSteps == null ? List.of() : List.copyOf(executionSteps);
        completionCriteria = completionCriteria == null ? List.of() : List.copyOf(completionCriteria);
        failureStrategies = failureStrategies == null ? List.of() : List.copyOf(failureStrategies);
        terminationConditions = terminationConditions == null ? List.of() : List.copyOf(terminationConditions);
    }

    public static DshAgentContractDTO from(PmsAgentContract contract) {
        return new DshAgentContractDTO(
                contract.contractId(), contract.agentId(), contract.contractKey(), contract.workflowNodeKeys(),
                contract.contractVersion(), contract.required(), contract.locale(), contract.principalRole(),
                contract.specializedAgents(), contract.readCapabilities(), contract.readToolBindings(),
                contract.writeCommands(), contract.confirmationPolicies(), contract.entryConditions(),
                contract.inputs(), contract.missingInputRules(), contract.executionSteps(),
                contract.completionCriteria(), contract.failureStrategies(), contract.terminationConditions(),
                PmsAgentContractFingerprint.sha256(contract));
    }
}
