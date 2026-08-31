package com.brad.pms.dto.request;

import lombok.Data;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class UserInviteCmd {
    private String nameZh;
    private String username;
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
    private String phone;
    @NotNull(message = "主组织不能为空")
    private Long orgUnitId;
    private Long positionId;
    private String roleCode;

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }
}
