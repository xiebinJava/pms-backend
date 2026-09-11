package com.brad.pms.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
public class OidcCallbackRequest {

    @NotBlank(message = "授权码不能为空")
    private String code;

    @NotBlank(message = "状态不能为空")
    private String state;
}
