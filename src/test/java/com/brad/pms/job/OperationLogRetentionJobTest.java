package com.brad.pms.job;

import com.brad.pms.mapper.LoginLogMapper;
import com.brad.pms.mapper.OperationLogMapper;
import com.brad.pms.service.OperationLogService;
import com.brad.pms.audit.AuditEvent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationLogRetentionJobTest {

    @Test
    void dryRunReportsCandidatesWithoutDeleting() {
        OperationLogMapper operations = mock(OperationLogMapper.class);
        LoginLogMapper logins = mock(LoginLogMapper.class);
        OperationLogService audit = mock(OperationLogService.class);
        when(operations.countExpired(any(LocalDateTime.class))).thenReturn(12L);
        when(logins.countExpiredSuccessful(any(LocalDateTime.class))).thenReturn(8L);
        OperationLogRetentionJob job = new OperationLogRetentionJob(operations, logins, audit,
                Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC), 180, false);

        OperationLogRetentionJob.Result result = job.runOnce(true);

        assertThat(result.isDryRun()).isTrue();
        assertThat(result.getCandidates()).isEqualTo(20L);
        assertThat(result.getDeleted()).isZero();
        verify(operations, never()).deleteExpiredBatch(any(LocalDateTime.class), anyInt());
        verify(logins, never()).deleteExpiredSuccessfulBatch(any(LocalDateTime.class), anyInt());
        var event = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(event.capture());
        assertThat(event.getValue().action()).isEqualTo("AUDIT_RETENTION_RUN");
        assertThat(event.getValue().reason()).isEqualTo("audit retention");
        assertThat(event.getValue().before().toString()).contains("retentionDays=180", "dryRun=true");
        assertThat(event.getValue().after().toString()).contains("candidates=20", "deleted=0");
    }

    @Test
    void liveRunDeletesOnlyEligibleRowsAndAuditsResult() {
        OperationLogMapper operations = mock(OperationLogMapper.class);
        LoginLogMapper logins = mock(LoginLogMapper.class);
        OperationLogService audit = mock(OperationLogService.class);
        when(operations.countExpired(any(LocalDateTime.class))).thenReturn(4L);
        when(logins.countExpiredSuccessful(any(LocalDateTime.class))).thenReturn(1L);
        when(operations.deleteExpiredBatch(any(LocalDateTime.class), anyInt())).thenReturn(3, 0);
        when(logins.deleteExpiredSuccessfulBatch(any(LocalDateTime.class), anyInt())).thenReturn(2, 0);
        OperationLogRetentionJob job = new OperationLogRetentionJob(operations, logins, audit,
                Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC), 180, false);

        OperationLogRetentionJob.Result result = job.runOnce(false);

        assertThat(result.isDryRun()).isFalse();
        assertThat(result.getDeleted()).isEqualTo(5);
        verify(audit).record(any(AuditEvent.class));
        verify(operations, times(2)).deleteExpiredBatch(any(LocalDateTime.class), eq(500));
        verify(logins, times(2)).deleteExpiredSuccessfulBatch(any(LocalDateTime.class), eq(500));
    }

}
