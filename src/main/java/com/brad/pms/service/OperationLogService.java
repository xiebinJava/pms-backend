package com.brad.pms.service;

import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditSanitizer;
import com.brad.pms.entity.OperationLogDO;
import com.brad.pms.mapper.OperationLogMapper;
import com.brad.pms.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class OperationLogService {
    private static final Logger log = LoggerFactory.getLogger(OperationLogService.class);
    private final OperationLogMapper operationLogMapper;
    private final AuditSanitizer auditSanitizer;

    public void record(String action, String resourceType, Long resourceId, Object before, Object after) {
        record(AuditEvent.success(action, resourceType, resourceId, null, null, before, after));
    }

    public void record(AuditEvent event) {
        long started = System.nanoTime();
        OperationLogDO log = new OperationLogDO();
        log.setOperatorId(UserContext.userIdOrNull());
        log.setAction(event.action());
        log.setResourceType(event.resourceType());
        log.setResourceId(event.resourceId());
        log.setProjectId(event.projectId());
        log.setReason(normalizeReason(event.reason()));
        log.setResult(event.result() == null ? "SUCCESS" : event.result());
        log.setRequestId(MDC.get("requestId"));
        log.setIp(requestValue(request -> request.getRemoteAddr(), 64));
        log.setUserAgent(requestValue(request -> request.getHeader("User-Agent"), 500));
        log.setBeforeJson(auditSanitizer.sanitize(event.before()));
        log.setAfterJson(auditSanitizer.sanitize(event.after()));
        operationLogMapper.insert(log);
        long durationMs = (System.nanoTime() - started) / 1_000_000;
        String previousAction = MDC.get("action");
        String previousResource = MDC.get("resource");
        String previousUser = MDC.get("userId");
        String previousDuration = MDC.get("durationMs");
        try {
            MDC.put("action", event.action() == null ? "" : event.action());
            MDC.put("resource", event.resourceType() == null ? "" : event.resourceType());
            if (log.getOperatorId() == null) MDC.remove("userId"); else MDC.put("userId", String.valueOf(log.getOperatorId()));
            MDC.put("durationMs", String.valueOf(durationMs));
            OperationLogService.log.info("audit_event");
        } finally {
            restoreMdc("action", previousAction);
            restoreMdc("resource", previousResource);
            restoreMdc("userId", previousUser);
            restoreMdc("durationMs", previousDuration);
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null) return null;
        String normalized = reason.replaceAll("\\p{Cntrl}", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private String requestValue(java.util.function.Function<jakarta.servlet.http.HttpServletRequest, String> getter,
                                int maxLength) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) return null;
        String value = getter.apply(servletAttributes.getRequest());
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private void restoreMdc(String key, String value) {
        if (value == null) MDC.remove(key); else MDC.put(key, value);
    }
}
