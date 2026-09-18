package com.brad.pms.ai.command;

/** A confirmed operation execution request. */
public record OperationExecuteRequest(String operationId, String idempotencyKey) {

    public OperationExecuteRequest {
        operationId = requireText(operationId, "operationId");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
