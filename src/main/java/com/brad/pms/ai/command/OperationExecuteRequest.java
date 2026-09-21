package com.brad.pms.ai.command;

/** A confirmed operation execution request. */
public record OperationExecuteRequest(
        String operationId,
        String idempotencyKey,
        String contextId,
        String contextVersion,
        String contractId,
        String contractVersion) {

    public OperationExecuteRequest(String operationId, String idempotencyKey) {
        this(operationId, idempotencyKey, null, null, null, null);
    }

    public OperationExecuteRequest {
        operationId = requireText(operationId, "operationId");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        contextId = optionalText(contextId, "contextId");
        contextVersion = optionalText(contextVersion, "contextVersion");
        contractId = optionalText(contractId, "contractId");
        contractVersion = optionalText(contractVersion, "contractVersion");
        if ((contextId == null) != (contextVersion == null)) {
            throw new IllegalArgumentException("contextId 与 contextVersion 必须同时提供");
        }
        if ((contractId == null) != (contractVersion == null)) {
            throw new IllegalArgumentException("contractId 与 contractVersion 必须同时提供");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String optionalText(String value, String name) {
        if (value == null) return null;
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
