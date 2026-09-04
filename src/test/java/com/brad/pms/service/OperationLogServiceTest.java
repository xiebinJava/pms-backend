package com.brad.pms.service;

import com.brad.pms.entity.OperationLogDO;
import com.brad.pms.mapper.OperationLogMapper;
import com.brad.pms.audit.AuditEvent;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OperationLogServiceTest {

    @Test
    void redactsCredentialFieldsBeforePersistingAudit() {
        OperationLogMapper mapper = mock(OperationLogMapper.class);
        OperationLogService service = new OperationLogService(mapper, new com.brad.pms.audit.AuditSanitizer());

        service.record("LOGIN_FAILED", "USER", 7L,
                Map.of("password", "supersecret", "token_hash", "sha256-value", "safe", "value"), null);

        var captor = org.mockito.ArgumentCaptor.forClass(OperationLogDO.class);
        verify(mapper).insert(captor.capture());
        String before = captor.getValue().getBeforeJson();
        assertThat(before).doesNotContain("supersecret", "sha256-value");
        assertThat(before).contains("REDACTED", "value");
    }

    @Test
    void persistsProjectContextReasonAndResultFromStructuredEvent() {
        OperationLogMapper mapper = mock(OperationLogMapper.class);
        OperationLogService service = new OperationLogService(mapper, new com.brad.pms.audit.AuditSanitizer());

        service.record(AuditEvent.success("PROJECT_TERMINATED", "PROJECT", 7L, 7L,
                "范围调整", Map.of("status", "ACTIVE"), Map.of("status", "TERMINATED")));

        var captor = org.mockito.ArgumentCaptor.forClass(OperationLogDO.class);
        verify(mapper).insert(captor.capture());
        OperationLogDO persisted = captor.getValue();
        assertThat(persisted.getProjectId()).isEqualTo(7L);
        assertThat(persisted.getReason()).isEqualTo("范围调整");
        assertThat(persisted.getResult()).isEqualTo("SUCCESS");
        assertThat(persisted.getAfterJson()).contains("TERMINATED");
    }

    @Test
    void capturesRequestMetadataAndRedactsNestedSensitiveValues() {
        OperationLogMapper mapper = mock(OperationLogMapper.class);
        OperationLogService service = new OperationLogService(mapper, new com.brad.pms.audit.AuditSanitizer());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("User-Agent", "qa-browser");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        MDC.put("requestId", "req-audit-1");
        try {
            service.record(AuditEvent.success("ROLE_UPDATED", "ROLE", 8L, null, null,
                    Map.of("nested", Map.of("access_token", "secret-value")), null));
        } finally {
            MDC.remove("requestId");
            RequestContextHolder.resetRequestAttributes();
        }

        var captor = org.mockito.ArgumentCaptor.forClass(OperationLogDO.class);
        verify(mapper).insert(captor.capture());
        OperationLogDO persisted = captor.getValue();
        assertThat(persisted.getRequestId()).isEqualTo("req-audit-1");
        assertThat(persisted.getIp()).isEqualTo("192.0.2.10");
        assertThat(persisted.getUserAgent()).isEqualTo("qa-browser");
        assertThat(persisted.getBeforeJson()).doesNotContain("secret-value").contains("REDACTED");
    }
}
