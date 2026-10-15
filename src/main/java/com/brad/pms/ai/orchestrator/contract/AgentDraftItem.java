package com.brad.pms.ai.orchestrator.contract;

import java.util.List;

public record AgentDraftItem(
        String text,
        List<String> sourceRefs,
        String confidence) {

    public AgentDraftItem {
        sourceRefs = sourceRefs == null ? null : List.copyOf(sourceRefs);
    }
}
