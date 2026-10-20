package com.brad.pms.ai.orchestrator.contract;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Resource-neutral node write contract projected from a selected node and its
 * saveContract. Only the fields every PMS resource actually declares are
 * required; resource-specific or partially declared fields stay nullable so
 * project/topic projections never fabricate capabilities. Story responses keep
 * their existing shape through {@link #from(StoryNodeContract)}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResourceNodeContract(
        Long nodeId,
        String key,
        String label,
        Integer version,
        String componentKey,
        String variant,
        List<Map<String, Object>> requiredFields,
        List<Map<String, Object>> fieldContracts,
        List<String> writableFields,
        List<String> protectedFields,
        List<String> allowedActions,
        List<String> allowedTransitions,
        List<String> missingInputRules,
        boolean writeCapable) {

    public ResourceNodeContract {
        requiredFields = requiredFields == null ? null : List.copyOf(requiredFields);
        fieldContracts = fieldContracts == null ? null : List.copyOf(fieldContracts);
        writableFields = writableFields == null ? List.of() : List.copyOf(writableFields);
        protectedFields = protectedFields == null ? List.of() : List.copyOf(protectedFields);
        allowedActions = allowedActions == null ? null : List.copyOf(allowedActions);
        allowedTransitions = allowedTransitions == null ? null : List.copyOf(allowedTransitions);
        missingInputRules = missingInputRules == null ? null : List.copyOf(missingInputRules);
    }

    /**
     * Converts the story-specific contract into the resource-neutral
     * projection without changing the published story JSON shape.
     */
    public static ResourceNodeContract from(StoryNodeContract contract) {
        if (contract == null) return null;
        List<Map<String, Object>> requiredFields = contract.requiredFields().stream()
                .map(ResourceNodeContract::field)
                .toList();
        return new ResourceNodeContract(
                contract.nodeId(),
                contract.key(),
                contract.label(),
                contract.version(),
                null,
                contract.variant(),
                requiredFields,
                null,
                contract.writableFields(),
                contract.protectedFields(),
                contract.allowedActions(),
                contract.allowedTransitions(),
                contract.missingInputRules(),
                contract.writeCapable());
    }

    private static Map<String, Object> field(StoryNodeContract.Field field) {
        return java.util.Map.of(
                "key", field.key(),
                "label", field.label(),
                "type", field.type(),
                "required", field.required(),
                "nullable", field.nullable(),
                "collection", field.collection());
    }
}
