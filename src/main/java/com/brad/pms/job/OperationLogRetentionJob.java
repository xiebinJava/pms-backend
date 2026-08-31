package com.brad.pms.job;

import com.brad.pms.mapper.LoginLogMapper;
import com.brad.pms.mapper.OperationLogMapper;
import com.brad.pms.service.OperationLogService;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Daily, auditable cleanup of expired operational logs.
 *
 * The job deliberately has no enclosing transaction: each bounded DELETE is
 * committed independently so row locks and undo data are released between
 * batches. The final audit record is written after all batches complete.
 */
@Component
@ConditionalOnProperty(name = "pms.audit.retention.enabled", havingValue = "true", matchIfMissing = true)
public class OperationLogRetentionJob {
    private final OperationLogMapper operationLogMapper;
    private final LoginLogMapper loginLogMapper;
    private final OperationLogService operationLogService;
    private final Clock clock;
    private final int retentionDays;
    private final boolean configuredDryRun;

    public OperationLogRetentionJob(OperationLogMapper operationLogMapper,
                                    LoginLogMapper loginLogMapper,
                                    OperationLogService operationLogService,
                                    Clock clock,
                                    @Value("${pms.audit.retention-days:180}") int retentionDays,
                                    @Value("${pms.audit.retention.dry-run:false}") boolean configuredDryRun) {
        if (retentionDays < 1) throw new IllegalArgumentException("audit retention days must be positive");
        this.operationLogMapper = operationLogMapper;
        this.loginLogMapper = loginLogMapper;
        this.operationLogService = operationLogService;
        this.clock = clock;
        this.retentionDays = retentionDays;
        this.configuredDryRun = configuredDryRun;
    }

    @Scheduled(cron = "${pms.audit.retention.cron:0 0 2 * * *}", zone = "UTC")
    public void runScheduled() {
        runOnce(configuredDryRun);
    }

    public Result runOnce(boolean dryRun) {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(retentionDays);
        long candidates = operationLogMapper.countExpired(cutoff) + loginLogMapper.countExpiredSuccessful(cutoff);
        int deleted = 0;
        if (!dryRun) {
            int batch;
            do {
                batch = operationLogMapper.deleteExpiredBatch(cutoff, 500);
                deleted += batch;
            } while (batch > 0);
            int loginBatch;
            do {
                loginBatch = loginLogMapper.deleteExpiredSuccessfulBatch(cutoff, 500);
                deleted += loginBatch;
            } while (loginBatch > 0);
        }
        operationLogService.record("AUDIT_RETENTION_RUN", "AUDIT", null, null,
                Map.of("retentionDays", retentionDays, "dryRun", dryRun,
                        "candidates", candidates, "deleted", deleted));
        return new Result(cutoff, dryRun, candidates, deleted);
    }

    @Getter
    public static final class Result {
        private final LocalDateTime cutoff;
        private final boolean dryRun;
        private final long candidates;
        private final int deleted;

        private Result(LocalDateTime cutoff, boolean dryRun, long candidates, int deleted) {
            this.cutoff = cutoff;
            this.dryRun = dryRun;
            this.candidates = candidates;
            this.deleted = deleted;
        }
    }
}
