package com.brad.pms.ai.orchestrator.contract;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public record AgentDraft(
        String contractVersion,
        DraftPayload draft) {

    public record DraftPayload(
            List<AgentDraftItem> facts,
            List<AgentDraftItem> inferences,
            List<AgentDraftItem> assumptions,
            List<AgentDraftItem> unresolvedDecisions,
            Map<String, Object> patch) {
    }

    public static AgentDraft parse(ObjectMapper objectMapper, String json) throws IOException {
        return objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                .readerFor(AgentDraft.class)
                .readValue(json);
    }
}
