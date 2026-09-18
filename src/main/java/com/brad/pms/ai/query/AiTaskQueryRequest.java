package com.brad.pms.ai.query;

/**
 * Read-only task query contract exposed to the Work Helper Agent.
 * Values are intentionally narrower than the normal task APIs.
 */
public record AiTaskQueryRequest(
        String scope,
        String due,
        String status,
        Long projectId,
        Long nodeId,
        Integer page,
        Integer pageSize) {
}
