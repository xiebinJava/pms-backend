package com.brad.pms.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Data
public class MemberAddCmd {

    @NotNull(message = "用户不能为空")
    private Long userId;

    private Integer role = 2;
}
