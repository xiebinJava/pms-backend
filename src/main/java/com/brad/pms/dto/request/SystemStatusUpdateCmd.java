package com.brad.pms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SystemStatusUpdateCmd {
    @NotBlank(message = "系统状态不能为空")
    private String status;

    @NotNull(message = "系统版本不能为空，请刷新后重试")
    private Integer version;

    @NotBlank(message = "原因不能为空")
    @Size(max = 500, message = "原因不能超过 500 个字符")
    private String reason;
}
