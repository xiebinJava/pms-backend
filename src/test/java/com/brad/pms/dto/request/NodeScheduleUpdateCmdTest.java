package com.brad.pms.dto.request;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class NodeScheduleUpdateCmdTest {

    @Test
    void carriesAnOptionalInclusiveSchedule() {
        NodeScheduleUpdateCmd cmd = new NodeScheduleUpdateCmd();
        cmd.setStartDate(LocalDate.of(2026, 9, 1));
        cmd.setEndDate(LocalDate.of(2026, 9, 20));

        assertThat(cmd.getStartDate()).isBeforeOrEqualTo(cmd.getEndDate());
    }
}
