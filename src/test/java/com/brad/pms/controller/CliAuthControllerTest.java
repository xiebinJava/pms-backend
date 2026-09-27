package com.brad.pms.controller;

import com.brad.pms.dto.response.LoginResponse;
import com.brad.pms.integration.cli.api.CliAuthDtos;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import com.brad.pms.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CliAuthControllerTest {

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void acceptsOnlyLoopbackCallbackRedirects() {
        assertThat(CliAuthController.isAllowedLoopbackRedirect("http://127.0.0.1:43127/callback")).isTrue();
        assertThat(CliAuthController.isAllowedLoopbackRedirect("http://localhost:43127/callback")).isTrue();
        assertThat(CliAuthController.isAllowedLoopbackRedirect("https://evil.example/callback")).isFalse();
        assertThat(CliAuthController.isAllowedLoopbackRedirect("http://127.0.0.1:80/callback")).isFalse();
        assertThat(CliAuthController.isAllowedLoopbackRedirect("http://127.0.0.1:43127/other")).isFalse();
    }

    @Test
    void authorizesWithPkceAndConsumesTheCodeOnce() {
        AuthService authService = mock(AuthService.class);
        when(authService.issueCliSession(7L)).thenReturn(new LoginResponse("access", "refresh", null));
        CliAuthController controller = new CliAuthController(authService);
        UserContext.set(new LoginUser(7L, "brad", "Brad"));
        String verifier = "a-strong-verifier-for-pms-cli-1234567890";
        String challenge = base64UrlSha256(verifier);

        var approved = controller.approve(new CliAuthDtos.ApproveRequest(
                "pms-cli", "http://127.0.0.1:43127/callback", "state-1", challenge, "S256"));

        var token = controller.token(new CliAuthDtos.TokenRequest(
                "authorization_code", approved.getData().authorizationCode(),
                "http://127.0.0.1:43127/callback", "pms-cli", verifier, null));

        assertThat(token.getData().accessToken()).isEqualTo("access");
        assertThatThrownBy(() -> controller.token(new CliAuthDtos.TokenRequest(
                "authorization_code", approved.getData().authorizationCode(),
                "http://127.0.0.1:43127/callback", "pms-cli", verifier, null)))
                .hasMessageContaining("授权码");
    }

    @Test
    void rejectsPkceVerifierMismatch() {
        AuthService authService = mock(AuthService.class);
        CliAuthController controller = new CliAuthController(authService);
        UserContext.set(new LoginUser(7L, "brad", "Brad"));
        String challenge = base64UrlSha256("expected-verifier-1234567890");
        var approved = controller.approve(new CliAuthDtos.ApproveRequest(
                "pms-cli", "http://localhost:43127/callback", "state-2", challenge, "S256"));

        assertThatThrownBy(() -> controller.token(new CliAuthDtos.TokenRequest(
                "authorization_code", approved.getData().authorizationCode(),
                "http://localhost:43127/callback", "pms-cli", "wrong-verifier-1234567890", null)))
                .hasMessageContaining("PKCE");
    }

    private static String base64UrlSha256(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
