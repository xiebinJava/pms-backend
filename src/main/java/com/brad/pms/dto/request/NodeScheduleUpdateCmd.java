package com.brad.pms.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** 节点排期更新命令；允许清空排期，因此两个字段均可为空。 */
@Data
public class NodeScheduleUpdateCmd {

    @NotNull(message = "节点版本不能为空，请刷新后重试")
    private Integer version;

    private LocalDate startDate;

    private LocalDate endDate;
}
