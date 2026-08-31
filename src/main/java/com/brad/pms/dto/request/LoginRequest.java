package com.brad.pms.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;

@Data
public class LoginRequest {

    /** New clients send email. Kept for old clients during the compatibility window. */
    private String username;

    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "密码不能为空")
    private String password;

    @AssertTrue(message = "邮箱不能为空")
    public boolean hasLoginIdentifier() {
        return (email != null && !email.isBlank()) || (username != null && !username.isBlank());
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }

    public void setUsername(String username) {
        this.username = username == null ? null : username.trim();
    }
}
