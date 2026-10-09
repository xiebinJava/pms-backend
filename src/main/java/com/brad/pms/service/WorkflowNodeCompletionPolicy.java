package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Shared rules for workflow node completion and rollback inputs.
 *
 * <p>The policy intentionally contains no persistence or workflow-template
 * knowledge. Project, topic, and story workflows all use it as the common
 * guard for node-level invariants.</p>
 */
@Service
public class WorkflowNodeCompletionPolicy {

    public void validateOwnerAndSchedule(Long ownerId, LocalDate startDate, LocalDate endDate) {
        if (ownerId == null) {
            throw BusinessException.error("请先分配节点负责人");
        }
        if (startDate == null || endDate == null) {
            throw BusinessException.error("请先设置节点排期");
        }
        if (startDate.isAfter(endDate)) {
            throw BusinessException.error("节点排期开始日期不能晚于结束日期");
        }
    }

    public String requireReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw BusinessException.error("回滚原因不能为空");
        }
        return reason.trim();
    }
}
