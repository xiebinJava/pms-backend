package com.brad.pms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InvitationResponse {
    private Long userId;
    private String activationUrl;
    private String expiresAt;
}
