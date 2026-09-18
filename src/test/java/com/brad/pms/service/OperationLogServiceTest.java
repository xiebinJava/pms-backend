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

    @Test
    void capturesDshAgentMetadataForIntegrationAudit() {
        OperationLogMapper mapper = mock(OperationLogMapper.class);
        OperationLogService service = new OperationLogService(mapper, new com.brad.pms.audit.AuditSanitizer());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-DSH-Session-Id", "dsh-session-1");
        request.addHeader("X-DSH-Agent-Id", "project_assistant");
        request.addHeader("X-DSH-Agent-Version", "sha256:v1");
        request.addHeader("X-DSH-Workspace", "pms");
        request.addHeader("X-DSH-Tool", "pms_command_execute");
        request.addHeader("X-DSH-Operation-Id", "op-1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            service.record(AuditEvent.success("PROJECT_CREATED", "PROJECT", 7L, 7L,
                    null, null, Map.of("name", "DSH 测试项目")));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        var captor = org.mockito.ArgumentCaptor.forClass(OperationLogDO.class);
        verify(mapper).insert(captor.capture());
        OperationLogDO persisted = captor.getValue();
        assertThat(persisted.getDshSessionId()).isEqualTo("dsh-session-1");
        assertThat(persisted.getDshAgentId()).isEqualTo("project_assistant");
        assertThat(persisted.getDshAgentVersion()).isEqualTo("sha256:v1");
        assertThat(persisted.getDshWorkspace()).isEqualTo("pms");
        assertThat(persisted.getDshTool()).isEqualTo("pms_command_execute");
        assertThat(persisted.getDshOperationId()).isEqualTo("op-1");
    }
}
