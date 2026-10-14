package com.brad.pms.ai.connector.context;

/**
 * One actual workflow node projected to its persisted identity fields. Carrying only real
 * persisted values, it never fabricates versions or statuses.
 */
public record NodeRef(Long id, Integer status, int position) {
}
