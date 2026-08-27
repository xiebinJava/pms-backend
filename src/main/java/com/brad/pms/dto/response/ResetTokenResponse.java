package com.brad.pms.dto.response;
import lombok.AllArgsConstructor;
import lombok.Data;
@Data @AllArgsConstructor public class ResetTokenResponse { private String resetUrl; private String expiresAt; }
