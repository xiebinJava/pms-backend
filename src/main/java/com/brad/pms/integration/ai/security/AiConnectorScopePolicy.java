package com.brad.pms.integration.ai.security;

import com.brad.pms.common.exception.BusinessException;

import java.util.Set;

/**
 * Scope vocabulary for the neutral connector facade.
 *
 * <p>These scopes are only the connector boundary. Ordinary PMS permissions
 * and the command-level DSH scope guard remain authoritative for the actual
 * operation.</p>
 */
public final class AiConnectorScopePolicy {

    public static final String QUERY_READ = "pms:query:read";
    public static final String COMMAND_EXECUTE = "pms:command:execute";
    public static final String WORKFLOW_WRITE = "pms:workflow:write";
    public static final String PROJECT_WRITE = "pms:project:write";
    public static final String DEVELOPMENT_WRITE = "pms:development:write";
    public static final String ITERATION_WRITE = "pms:iteration:write";

    private static final Set<String> CLIENTS = Set.of("mcp", "opencli");

    private AiConnectorScopePolicy() {
    }

    public static void requireClient(String clientId) {
        if (clientId == null || !CLIENTS.contains(clientId)) {
            throw BusinessException.forbidden("连接器客户端不受支持");
        }
    }

    public static Set<String> clients() {
        return CLIENTS;
    }
}
