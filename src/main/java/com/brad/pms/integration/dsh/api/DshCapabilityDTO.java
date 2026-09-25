package com.brad.pms.integration.dsh.api;

import java.util.List;

/** Stable capability discovery response for the DSH PMS plugin. */
public record DshCapabilityDTO(
        String version,
        List<String> tools,
        List<String> scopes,
        List<String> pageTypes,
        List<DshQueryCapabilityDTO> queries,
        List<DshCommandCapabilityDTO> commands,
        List<DshAgentContractCapabilityDTO> agentContracts,
        DshViewerDTO viewer,
        String today) {

    public DshCapabilityDTO(
            String version,
            List<String> tools,
            List<String> scopes,
            List<String> pageTypes,
            List<DshQueryCapabilityDTO> queries,
            List<DshCommandCapabilityDTO> commands) {
        this(version, tools, scopes, pageTypes, queries, commands, List.of());
    }

    public DshCapabilityDTO(
            String version,
            List<String> tools,
            List<String> scopes,
            List<String> pageTypes,
            List<DshQueryCapabilityDTO> queries,
            List<DshCommandCapabilityDTO> commands,
            List<DshAgentContractCapabilityDTO> agentContracts) {
        this(version, tools, scopes, pageTypes, queries, commands, agentContracts, null, null);
    }

    public DshCapabilityDTO {
        tools = tools == null ? List.of() : List.copyOf(tools);
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        pageTypes = pageTypes == null ? List.of() : List.copyOf(pageTypes);
        queries = queries == null ? List.of() : List.copyOf(queries);
        commands = commands == null ? List.of() : List.copyOf(commands);
        agentContracts = agentContracts == null ? List.of() : List.copyOf(agentContracts);
    }
}
