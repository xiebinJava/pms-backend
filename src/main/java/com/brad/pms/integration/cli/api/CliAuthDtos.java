package com.brad.pms.integration.cli.api;

import com.brad.pms.dto.response.UserDTO;

public final class CliAuthDtos {
    private CliAuthDtos() {
    }

    public record ApproveRequest(
            String clientId,
            String redirectUri,
            String state,
            String codeChallenge,
            String codeChallengeMethod) {
    }

    public record ApprovalResponse(
            String authorizationCode,
            String redirectUri,
            String state,
            int expiresInSeconds) {
    }

    public record TokenRequest(
            String grantType,
            String code,
            String redirectUri,
            String clientId,
            String codeVerifier,
            String refreshToken) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            int expiresInSeconds,
            UserDTO user) {
    }

    public record RevokeRequest(String refreshToken) {
    }

    public record RevokeResponse(boolean revoked) {
    }
}
