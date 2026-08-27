package com.brad.pms.dto.request;

import lombok.Data;

import java.time.LocalDate;

/** 节点排期更新命令；允许清空排期，因此两个字段均可为空。 */
@Data
public class NodeScheduleUpdateCmd {

    private LocalDate startDate;

    private LocalDate endDate;
}
