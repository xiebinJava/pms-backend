package com.brad.pms.ai.connector.context;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request-scoped authorization decisions for structured AI context references.
 * Decisions are memoized so repeated projections of one reference cannot issue
 * repeated mapper/permission queries during a single context read.
 */
public final class ReadAuthorizationSnapshot {

    private final Long userId;
    private final AiContextReadPolicy policy;
    private final Map<AiContextReference, Boolean> decisions = new LinkedHashMap<>();

    private ReadAuthorizationSnapshot(Long userId, Collection<AiContextReference> references,
                                      AiContextReadPolicy policy) {
        this.userId = userId;
        this.policy = policy;
        for (AiContextReference reference : references == null ? java.util.List.<AiContextReference>of() : references) {
            if (reference != null) decisions.put(reference, resolve(reference));
        }
    }

    public static ReadAuthorizationSnapshot forUser(Long userId,
                                                     Collection<AiContextReference> references,
                                                     AiContextReadPolicy policy) {
        if (policy == null) throw new IllegalArgumentException("授权策略不能为空");
        return new ReadAuthorizationSnapshot(userId, references, policy);
    }

    public static ReadAuthorizationSnapshot forUser(Long userId, AiContextReadPolicy policy) {
        return forUser(userId, java.util.List.of(), policy);
    }

    public Long userId() {
        return userId;
    }

    public boolean canRead(AiContextReference reference) {
        if (reference == null || reference.resourceId() == null || reference.resourceId() <= 0) return false;
        return decisions.computeIfAbsent(reference, this::resolve);
    }

    public boolean canRead(String resourceType, Long resourceId) {
        return canRead(new AiContextReference(resourceType, resourceId));
    }

    private boolean resolve(AiContextReference reference) {
        if (reference.resourceId() == null || reference.resourceId() <= 0 || reference.resourceType().isBlank()) {
            return false;
        }
        return policy.canReadReference(reference.resourceType(), reference.resourceId());
    }
}
