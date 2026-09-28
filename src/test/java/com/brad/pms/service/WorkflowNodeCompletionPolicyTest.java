package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowNodeCompletionPolicyTest {

    private final WorkflowNodeCompletionPolicy policy = new WorkflowNodeCompletionPolicy();

    @Test
    void rejectsMissingOwner() {
        assertThatThrownBy(() -> policy.validateOwnerAndSchedule(
                null,
                LocalDate.of(2026, 9, 28),
                LocalDate.of(2026, 9, 29)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先分配节点负责人");
    }

    @Test
    void rejectsIncompleteSchedule() {
        assertThatThrownBy(() -> policy.validateOwnerAndSchedule(
                7L,
                LocalDate.of(2026, 9, 28),
                null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先设置节点排期");
    }

    @Test
    void rejectsReversedSchedule() {
        assertThatThrownBy(() -> policy.validateOwnerAndSchedule(
                7L,
                LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 9, 29)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("节点排期开始日期不能晚于结束日期");
    }

    @Test
    void acceptsOwnerAndOrderedSchedule() {
        policy.validateOwnerAndSchedule(
                7L,
                LocalDate.of(2026, 9, 28),
                LocalDate.of(2026, 9, 29));
    }

    @Test
    void rejectsBlankRollbackReason() {
        assertThatThrownBy(() -> policy.requireReason("  "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("回滚原因不能为空");
    }

    @Test
    void trimsRollbackReason() {
        assertThat(policy.requireReason("  需求调整  ")).isEqualTo("需求调整");
    }
}
