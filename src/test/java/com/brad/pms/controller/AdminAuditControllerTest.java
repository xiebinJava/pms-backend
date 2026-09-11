package com.brad.pms.controller;

import com.brad.pms.entity.OperationLogDO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAuditControllerTest {
    @Test
    void auditQuerySupportsResourceOperatorAndTimeFilters() {
        QueryWrapper<OperationLogDO> query = AdminAuditController.buildQuery(
                "USER_DISABLED", "USER", 42L, 7L,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 2, 0, 0), 99L, "SUCCESS", "req-99");

        String sql = query.getSqlSegment();
        assertThat(sql).contains("action", "resource_type", "resource_id", "operator_id", "project_id", "result", "request_id", "created_at");
    }

    @Test
    void auditQueryDoesNotEmbedAFixedLimitSoServerPagingCanApply() {
        QueryWrapper<OperationLogDO> query = AdminAuditController.buildQuery(
                null, null, null, null, null, null);

        assertThat(query.getSqlSegment()).doesNotContain("LIMIT 200");
    }
}
