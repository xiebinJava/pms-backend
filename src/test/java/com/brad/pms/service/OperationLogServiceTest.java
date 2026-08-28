package com.brad.pms.service;

import com.brad.pms.entity.OperationLogDO;
import com.brad.pms.mapper.OperationLogMapper;
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
        OperationLogService service = new OperationLogService(mapper);

        service.record("LOGIN_FAILED", "USER", 7L,
                Map.of("password", "supersecret", "token_hash", "sha256-value", "safe", "value"), null);

        var captor = org.mockito.ArgumentCaptor.forClass(OperationLogDO.class);
        verify(mapper).insert(captor.capture());
        String before = captor.getValue().getBeforeJson();
        assertThat(before).doesNotContain("supersecret", "sha256-value");
        assertThat(before).contains("REDACTED", "value");
    }
}
