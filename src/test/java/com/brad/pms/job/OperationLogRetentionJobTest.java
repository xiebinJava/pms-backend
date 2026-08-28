package com.brad.pms.job;

import com.brad.pms.mapper.LoginLogMapper;
import com.brad.pms.mapper.OperationLogMapper;
import com.brad.pms.service.OperationLogService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationLogRetentionJobTest {

    @Test
    void dryRunReportsCandidatesWithoutDeleting() {
        OperationLogMapper operations = mock(OperationLogMapper.class);
        LoginLogMapper logins = mock(LoginLogMapper.class);
        OperationLogService audit = mock(OperationLogService.class);
        when(operations.countExpired(any(LocalDateTime.class))).thenReturn(12L);
        OperationLogRetentionJob job = new OperationLogRetentionJob(operations, logins, audit,
                Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC), 180, false);

        OperationLogRetentionJob.Result result = job.runOnce(true);

        assertThat(result.isDryRun()).isTrue();
        assertThat(result.getCandidates()).isEqualTo(12L);
        assertThat(result.getDeleted()).isZero();
        verify(operations, never()).deleteExpired(any(LocalDateTime.class));
        verify(logins, never()).deleteExpiredSuccessful(any(LocalDateTime.class));
        var metadata = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(audit).record(org.mockito.ArgumentMatchers.eq("AUDIT_RETENTION_RUN"),
                org.mockito.ArgumentMatchers.eq("AUDIT"), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), metadata.capture());
        assertThat(metadata.getValue().toString()).contains("candidates=12", "deleted=0", "dryRun=true");
    }

    @Test
    void liveRunDeletesOnlyEligibleRowsAndAuditsResult() {
        OperationLogMapper operations = mock(OperationLogMapper.class);
        LoginLogMapper logins = mock(LoginLogMapper.class);
        OperationLogService audit = mock(OperationLogService.class);
        when(operations.countExpired(any(LocalDateTime.class))).thenReturn(4L);
        when(operations.deleteExpired(any(LocalDateTime.class))).thenReturn(3);
        OperationLogRetentionJob job = new OperationLogRetentionJob(operations, logins, audit,
                Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC), 180, false);

        OperationLogRetentionJob.Result result = job.runOnce(false);

        assertThat(result.isDryRun()).isFalse();
        assertThat(result.getDeleted()).isEqualTo(3);
        verify(operations).deleteExpired(any(LocalDateTime.class));
        verify(logins).deleteExpiredSuccessful(any(LocalDateTime.class));
    }

}
