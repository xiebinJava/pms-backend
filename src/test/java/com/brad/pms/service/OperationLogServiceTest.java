package com.brad.pms.service;

import com.brad.pms.entity.OperationLogDO;
import com.brad.pms.mapper.OperationLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OperationLogServiceTest {
    @Mock OperationLogMapper operationLogMapper;
    @InjectMocks OperationLogService operationLogService;

    @AfterEach
    void clearRequestContext() {
        org.slf4j.MDC.clear();
    }

    @Test
    void recordsRequestIdAlongsideAuditableChange() {
        org.slf4j.MDC.put("requestId", "trace-123");

        operationLogService.record("USER_DISABLED", "USER", 7L, null, java.util.Map.of("status", "DISABLED"));

        ArgumentCaptor<OperationLogDO> captor = ArgumentCaptor.forClass(OperationLogDO.class);
        verify(operationLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getRequestId()).isEqualTo("trace-123");
        assertThat(captor.getValue().getAfterJson()).contains("DISABLED");
    }
}
