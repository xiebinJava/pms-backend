package com.brad.pms.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
public class ProjectLifecycleCmd {

    @NotBlank(message = "原因不能为空")
    private String reason;
}
