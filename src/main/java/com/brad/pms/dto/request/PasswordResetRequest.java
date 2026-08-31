package com.brad.pms.dto.request;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
@Data public class PasswordResetRequest { @NotBlank(message = "英文名不能为空") private String username; }
