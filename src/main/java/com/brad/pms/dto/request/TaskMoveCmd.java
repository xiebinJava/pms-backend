package com.brad.pms.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Data
public class TaskMoveCmd {

    @NotNull(message = "状态不能为空")
    private Integer status;
}
