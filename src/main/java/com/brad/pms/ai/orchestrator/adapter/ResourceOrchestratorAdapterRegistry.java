package com.brad.pms.ai.orchestrator.adapter;

import com.brad.pms.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Selects the resource adapter for a conversation resource type. Requirement
 * and story keep their inline service paths; only project and topic route
 * through this registry.
 */
@Component
public class ResourceOrchestratorAdapterRegistry {

    private final Map<String, ResourceOrchestratorAdapter> adapters;

    public ResourceOrchestratorAdapterRegistry(List<ResourceOrchestratorAdapter> adapterList) {
        Map<String, ResourceOrchestratorAdapter> registered = new LinkedHashMap<>();
        for (ResourceOrchestratorAdapter adapter : adapterList) {
            registered.put(adapter.resourceType(), adapter);
        }
        this.adapters = Map.copyOf(registered);
    }

    public Optional<ResourceOrchestratorAdapter> find(String resourceType) {
        return Optional.ofNullable(adapters.get(resourceType));
    }

    public ResourceOrchestratorAdapter require(String resourceType) {
        return find(resourceType).orElseThrow(() ->
                BusinessException.error("CAPABILITY_UNAVAILABLE: 不支持的资源类型 " + resourceType));
    }
}
