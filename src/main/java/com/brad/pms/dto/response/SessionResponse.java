package com.brad.pms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {
    private String accessToken;
    private String refreshToken;
    private UserDTO user;
}
