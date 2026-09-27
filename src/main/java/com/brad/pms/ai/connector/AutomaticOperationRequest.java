package com.brad.pms.ai.connector;

import com.brad.pms.ai.command.CommandName;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Normalized write request used by the neutral AI connector facade.
 *
 * <p>The connector never supplies the acting user. Authentication is taken
 * from {@code UserContext}; this record only carries the stable command and
 * the request metadata needed for idempotency and audit tracing.</p>
 */
public record AutomaticOperationRequest(
        CommandName name,
        Map<String, Object> arguments,
        String contextId,
        String contextVersion,
        String contractId,
        String contractVersion,
        String idempotencyKey,
        String clientId,
        String requestId) {

    public AutomaticOperationRequest {
        name = Objects.requireNonNull(name, "name");
        arguments = arguments == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
        contextId = optionalText(contextId, "contextId");
        contextVersion = optionalText(contextVersion, "contextVersion");
        contractId = optionalText(contractId, "contractId");
        contractVersion = optionalText(contractVersion, "contractVersion");
        idempotencyKey = requiredText(idempotencyKey, "idempotencyKey");
        clientId = requiredText(clientId, "clientId");
        requestId = requiredText(requestId, "requestId");
        if ((contextId == null) != (contextVersion == null)) {
            throw new IllegalArgumentException("contextId 与 contextVersion 必须同时提供");
        }
        if ((contractId == null) != (contractVersion == null)) {
            throw new IllegalArgumentException("contractId 与 contractVersion 必须同时提供");
        }
        if (!"pms-cli".equals(clientId) && !"mcp".equals(clientId) && !"opencli".equals(clientId)) {
            throw new IllegalArgumentException("clientId 只支持 pms-cli（兼容旧适配器：mcp/opencli）");
        }
    }

    private static String requiredText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String optionalText(String value, String name) {
        if (value == null) return null;
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
