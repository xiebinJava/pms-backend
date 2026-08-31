package com.brad.pms.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class ActivationRequest {
    @NotBlank(message = "激活令牌不能为空")
    private String token;
    @NotBlank(message = "密码不能为空")
    @Size(min = 12, message = "密码至少需要 12 位")
    private String password;
}
