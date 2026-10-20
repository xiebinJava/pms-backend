package com.brad.pms.ai.orchestrator.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public record AgentDraft(
        String contractVersion,
        DraftPayload draft) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DraftPayload(
            List<AgentDraftItem> facts,
            List<AgentDraftItem> inferences,
            List<AgentDraftItem> assumptions,
            List<AgentDraftItem> unresolvedDecisions,
            Map<String, Object> patch,
            Boolean requestedCompletion,
            Map<String, Object> fieldSources) {

        /**
         * Legacy constructor for requirement/story drafts; the resource-node
         * extensions stay absent on the wire.
         */
        public DraftPayload(
                List<AgentDraftItem> facts,
                List<AgentDraftItem> inferences,
                List<AgentDraftItem> assumptions,
                List<AgentDraftItem> unresolvedDecisions,
                Map<String, Object> patch) {
            this(facts, inferences, assumptions, unresolvedDecisions, patch, null, null);
        }
    }

    public static AgentDraft parse(ObjectMapper objectMapper, String json) throws IOException {
        return objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                .readerFor(AgentDraft.class)
                .readValue(json);
    }
}
