package com.brad.pms.ai.orchestrator.adapter;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-resolved node binding for a resource-node orchestration plan. Every
 * field is derived from the focused context, never from the external Agent's
 * request; the five version axes feed the draft-provenance digest snapshot.
 */
public record ResourceNodeBinding(
        String resourceType,
        Long resourceId,
        Long nodeId,
        Long nodeVersion,
        String componentKey,
        String label,
        Map<String, Object> saveContract,
        List<String> writableFields,
        List<String> protectedFields,
        boolean writeCapable,
        Long entityVersion,
        Integer workflowInstanceVersion,
        Integer componentVersion,
        Long contextVersion) {

    /** Fields that position a node and must be carried outside the draft patch. */
    public static final Set<String> NODE_POSITIONING_KEYS = Set.of(
            "nodeId", "nodeVersion", "componentKey", "contextVersion",
            "resourceId", "resourceType", "version", "itemType", "itemId",
            "projectId", "__components");

    public ResourceNodeBinding {
        writableFields = writableFields == null ? List.of() : List.copyOf(writableFields);
        protectedFields = protectedFields == null ? List.of() : List.copyOf(protectedFields);
        saveContract = saveContract == null ? Map.of() : Map.copyOf(saveContract);
    }
}
