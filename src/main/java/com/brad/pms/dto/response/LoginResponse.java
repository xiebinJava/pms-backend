package com.brad.pms.dto.response;

import lombok.Data;

@Data
public class LoginResponse extends SessionResponse {
    private String token;

    public LoginResponse(String accessToken, String refreshToken, UserDTO user) {
        super(accessToken, refreshToken, user);
        this.token = accessToken;
    }

    public LoginResponse(String token, UserDTO user) {
        this(token, null, user);
    }
}
