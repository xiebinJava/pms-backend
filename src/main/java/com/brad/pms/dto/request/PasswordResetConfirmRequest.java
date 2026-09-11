package com.brad.pms.dto.request;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
@Data public class PasswordResetConfirmRequest { @NotBlank private String token; @NotBlank @Size(min = 12, message = "密码至少需要 12 位") private String password; }
