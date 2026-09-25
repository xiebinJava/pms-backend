package com.brad.pms.integration.dsh.api;

import java.util.List;

/** Discovery metadata for an internally fetched Agent contract. */
public record DshAgentContractCapabilityDTO(
        String agentId,
        String contractKey,
        List<String> workflowNodeKeys,
        String contractVersion,
        String endpoint,
        String scope,
        boolean required) {

    public DshAgentContractCapabilityDTO {
        workflowNodeKeys = workflowNodeKeys == null ? List.of() : List.copyOf(workflowNodeKeys);
    }
}
