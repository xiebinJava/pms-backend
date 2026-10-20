package com.brad.pms.ai.orchestrator.adapter;

import com.brad.pms.ai.orchestrator.contract.AgentDraft;
import com.brad.pms.ai.orchestrator.contract.DraftValidationResult;
import com.brad.pms.ai.orchestrator.contract.ResourceNodeContract;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maps a project/topic resource-node intent into the existing focused context,
 * saveContract and draft validation flow. Adapters never read business Mapper
 * classes and never select components implicitly; PMS stays the authority for
 * field rules, versions and permissions.
 */
public interface ResourceOrchestratorAdapter {

    String resourceType();

    /**
     * Resolves the server-owned binding from the focused context. Throws
     * in-band BusinessException codes: CONFLICT for current-node/version
     * mismatches and CAPABILITY_UNAVAILABLE for ambiguous or non-writable
     * component selections.
     */
    ResourceNodeBinding resolveBinding(AiWorkflowContextDTO context, Long nodeId,
                                       String componentKey, Long nodeVersion);

    DraftValidationResult validateDraft(AgentDraft draft, AiWorkflowContextDTO context,
                                        ResourceNodeBinding binding);

    /**
     * Projects the resource-neutral node contract from the binding. Fields PMS
     * does not declare stay null and are omitted from the response; no empty
     * rules are fabricated to imply capability.
     */
    default ResourceNodeContract nodeContract(ResourceNodeBinding binding) {
        if (binding == null) return null;
        Map<String, Object> contract = binding.saveContract();
        return new ResourceNodeContract(
                binding.nodeId(),
                binding.componentKey(),
                binding.label(),
                binding.nodeVersion() == null ? null : binding.nodeVersion().intValue(),
                binding.componentKey(),
                string(contract.get("variant")),
                requiredFieldContracts(contract),
                fieldContracts(contract),
                binding.writableFields(),
                binding.protectedFields(),
                strings(contract.get("allowedActions")),
                strings(contract.get("allowedTransitions")),
                strings(contract.get("missingInputRules")),
                binding.writeCapable());
    }

    private String string(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return null;
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String text && !text.isBlank()) result.add(text);
        }
        return result.isEmpty() ? null : result;
    }

    private List<Map<String, Object>> fieldContracts(Map<String, Object> contract) {
        if (!(contract.get("fieldContracts") instanceof List<?> list)) return null;
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) result.add(toMap(map));
        }
        return result.isEmpty() ? null : result;
    }

    private List<Map<String, Object>> requiredFieldContracts(Map<String, Object> contract) {
        List<Map<String, Object>> declared = fieldContracts(contract);
        if (declared == null) return null;
        List<Map<String, Object>> required = new ArrayList<>();
        for (Map<String, Object> field : declared) {
            if (Boolean.TRUE.equals(field.get("required"))) required.add(field);
        }
        return required;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Map<?, ?> map) {
        return (Map<String, Object>) Map.copyOf(map);
    }
}
