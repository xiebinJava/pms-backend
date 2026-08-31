package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.LoginRequest;
import com.brad.pms.dto.request.RefreshTokenRequest;
import com.brad.pms.dto.request.ActivationRequest;
import com.brad.pms.dto.request.PasswordResetRequest;
import com.brad.pms.dto.request.PasswordResetConfirmRequest;
import com.brad.pms.dto.request.PasswordChangeRequest;
import com.brad.pms.dto.response.ResetTokenResponse;
import com.brad.pms.dto.response.LoginResponse;
import com.brad.pms.dto.response.UserDTO;
import com.brad.pms.security.IgnoreAuth;
import com.brad.pms.security.UserContext;
import com.brad.pms.service.AuthService;
import com.brad.pms.service.InvitationService;
import com.brad.pms.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final InvitationService invitationService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    @IgnoreAuth
    public ResponseResult<LoginResponse> login(@Validated @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest,
                                               HttpServletResponse httpResponse) {
        LoginResponse response = authService.login(request, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        setRefreshCookie(httpResponse, response.getRefreshToken(), httpRequest.isSecure());
        return ResponseResult.success(response);
    }

    @PostMapping("/refresh")
    @IgnoreAuth
    public ResponseResult<LoginResponse> refresh(@RequestBody(required = false) RefreshTokenRequest request,
                                                 HttpServletRequest httpRequest,
                                                 HttpServletResponse httpResponse) {
        String token = request == null ? null : request.getRefreshToken();
        if (token == null && httpRequest.getCookies() != null) {
            for (Cookie cookie : httpRequest.getCookies()) {
                if ("pms_refresh_token".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }
        LoginResponse response = authService.refresh(token, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        setRefreshCookie(httpResponse, response.getRefreshToken(), httpRequest.isSecure());
        return ResponseResult.success(response);
    }

    @PostMapping("/logout")
    public ResponseResult<Void> logout() {
        var current = UserContext.get();
        authService.revokeSession(UserContext.userId(), current == null ? null : current.getSessionId(), "USER_LOGOUT");
        return ResponseResult.success();
    }

    @PostMapping("/password/change")
    public ResponseResult<Void> changePassword(@Validated @RequestBody PasswordChangeRequest request) {
        authService.changePassword(request);
        return ResponseResult.success();
    }

    @GetMapping("/me")
    public ResponseResult<UserDTO> me() {
        return ResponseResult.success(authService.me());
    }

    @PostMapping("/activate")
    @IgnoreAuth
    public ResponseResult<Void> activate(@Validated @RequestBody ActivationRequest request) {
        invitationService.activate(request);
        return ResponseResult.success();
    }

    @PostMapping("/password-reset/request")
    @IgnoreAuth
    public ResponseResult<ResetTokenResponse> requestPasswordReset(@Validated @RequestBody PasswordResetRequest request) {
        return ResponseResult.success(passwordResetService.request(request));
    }

    @PostMapping("/password-reset/confirm")
    @IgnoreAuth
    public ResponseResult<Void> confirmPasswordReset(@Validated @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirm(request);
        return ResponseResult.success();
    }

    private void setRefreshCookie(HttpServletResponse response, String token, boolean secure) {
        if (token == null) return;
        ResponseCookie cookie = ResponseCookie.from("pms_refresh_token", token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(Duration.ofDays(30))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
