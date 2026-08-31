package com.brad.pms.dto.request;
import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;

@Data
public class PasswordResetRequest {
    @Email(message = "邮箱格式不正确")
    private String email;

    /** Deprecated compatibility field for clients that have not migrated yet. */
    private String username;

    @AssertTrue(message = "邮箱不能为空")
    public boolean hasResetIdentifier() {
        return (email != null && !email.isBlank()) || (username != null && !username.isBlank());
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }

    public void setUsername(String username) {
        this.username = username == null ? null : username.trim();
    }
}
