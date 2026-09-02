package com.brad.pms.service;

import com.brad.pms.entity.ImportJobDO;
import com.brad.pms.mapper.ImportJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Persists a failed import state even when the business transaction rolls back. */
@Service
@RequiredArgsConstructor
public class ImportJobStateService {
    private final ImportJobMapper importJobMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String jobId, String reason) {
        ImportJobDO job = importJobMapper.selectById(jobId);
        if (job == null || "SUCCESS".equals(job.getStatus())) return;
        job.setStatus("FAILED");
        job.setFailureReason(reason == null ? "导入失败，已回滚全部变更" : reason.substring(0, Math.min(reason.length(), 500)));
        job.setFailedAt(LocalDateTime.now());
        importJobMapper.updateById(job);
    }
}
