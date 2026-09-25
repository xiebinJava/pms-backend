package com.brad.pms.integration.dsh.security;

import com.brad.pms.common.exception.BusinessException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Server-owned Agent and scope policy for DSH delegated PMS access. */
@Component
public final class DshAgentScopePolicy {

    public static final String PROJECT_ASSISTANT = "project_assistant";
    public static final List<String> PROJECT_ASSISTANT_SCOPES = List.of(
            "pms:project:read", "pms:task:read", "pms:query:read",
            "pms:task:write", "pms:command:preview", "pms:command:execute",
            "pms:workflow:write",
            "pms:project:write",
            "pms:workspace:embed");

    private final Map<String, Set<String>> allowedScopes = new LinkedHashMap<>();

    public DshAgentScopePolicy() {
        allowedScopes.put(PROJECT_ASSISTANT, Set.copyOf(PROJECT_ASSISTANT_SCOPES));
    }

    /**
     * Resolves the final scopes from server policy. Requested scopes may narrow
     * the first-party policy, but can never expand it.
     */
    public List<String> resolve(String agentId, Collection<String> requestedScopes) {
        if (agentId == null || agentId.isBlank() || !agentId.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw BusinessException.forbidden("PMS_DSH_AGENT_INVALID: Agent 标识无效");
        }
        Set<String> allowed = allowedScopes.get(agentId);
        if (allowed == null) {
            throw BusinessException.forbidden("PMS_DSH_AGENT_INVALID: Agent 未注册或未启用");
        }

        LinkedHashSet<String> requested = new LinkedHashSet<>();
        if (requestedScopes != null) {
            for (String scope : requestedScopes) {
                if (scope == null || scope.isBlank()) {
                    throw BusinessException.forbidden("PMS_DSH_SCOPE_FORBIDDEN: 请求的 DSH 权限范围无效");
                }
                requested.add(scope.trim());
            }
        }
        if (!requested.isEmpty() && !allowed.containsAll(requested)) {
            throw BusinessException.forbidden("PMS_DSH_SCOPE_FORBIDDEN: 请求的 DSH 权限范围未获允许");
        }
        return PROJECT_ASSISTANT_SCOPES.stream()
                .filter(scope -> requested.isEmpty() || requested.contains(scope))
                .toList();
    }

    public boolean isAllowed(String agentId, Collection<String> scopes) {
        try {
            resolve(agentId, scopes);
            return true;
        } catch (BusinessException ex) {
            return false;
        }
    }
}
