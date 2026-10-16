package com.brad.pms.ai.orchestrator.contract;

import java.util.List;

/**
 * Immutable write contract projected from the selected story workflow node.
 * The orchestrator carries this projection; it does not recreate workflow rules.
 */
public record StoryNodeContract(
        Long nodeId,
        String key,
        String label,
        Integer version,
        String variant,
        List<Field> requiredFields,
        List<String> writableFields,
        List<String> protectedFields,
        List<String> allowedActions,
        List<String> allowedTransitions,
        List<String> missingInputRules,
        boolean writeCapable) {

    public StoryNodeContract {
        requiredFields = requiredFields == null ? List.of() : List.copyOf(requiredFields);
        writableFields = writableFields == null ? List.of() : List.copyOf(writableFields);
        protectedFields = protectedFields == null ? List.of() : List.copyOf(protectedFields);
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
        allowedTransitions = allowedTransitions == null ? List.of() : List.copyOf(allowedTransitions);
        missingInputRules = missingInputRules == null ? List.of() : List.copyOf(missingInputRules);
    }

    public record Field(String key, String label, String type,
                        boolean required, boolean nullable, boolean collection) {
    }
}
