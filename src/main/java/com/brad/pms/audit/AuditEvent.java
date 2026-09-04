package com.brad.pms.audit;

/**
 * Structured audit input supplied by a business service.
 * Operator and request metadata are filled by OperationLogService.
 */
public record AuditEvent(
        String action,
        String resourceType,
        Long resourceId,
        Long projectId,
        String reason,
        Object before,
        Object after,
        String result
) {
    public static AuditEvent success(String action, String resourceType, Long resourceId,
                                     Long projectId, String reason, Object before, Object after) {
        return new AuditEvent(action, resourceType, resourceId, projectId, reason, before, after, "SUCCESS");
    }
}
