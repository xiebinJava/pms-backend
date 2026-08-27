package com.brad.pms.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class UserDisableCmd {
    @NotBlank(message = "停用原因不能为空")
    private String reason;

    public UserDisableCmd() { }

    public UserDisableCmd(String reason) { this.reason = reason; }
}
