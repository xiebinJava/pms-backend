package com.brad.pms.controller;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.response.LoginResponse;
import com.brad.pms.integration.dsh.api.DshSsoSessionRequest;
import com.brad.pms.integration.dsh.service.DshSsoSessionService;
import com.brad.pms.security.IgnoreAuth;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Bootstrap endpoint for DSH: exchange the SSO identity DSH already verified for
 * a normal PMS session. It is intentionally `@IgnoreAuth` because the caller has
 * no PMS token yet; the shared service key is the only gate, together with PMS's
 * own verification of the IdP-signed token.
 */
@RestController
@RequestMapping("/integration/dsh/v1")
public class DshSsoSessionController {

    private final DshSsoSessionService ssoSessionService;
    private final String serviceKey;

    public DshSsoSessionController(DshSsoSessionService ssoSessionService,
                                   @Value("${pms.dsh.service-key:}") String serviceKey) {
        this.ssoSessionService = ssoSessionService;
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
    }

    @PostMapping("/sso-session")
    @IgnoreAuth
    public ResponseResult<LoginResponse> ssoSession(
            @RequestHeader(value = "X-DSH-Service-Key", required = false) String suppliedServiceKey,
            @RequestBody(required = false) DshSsoSessionRequest request,
            HttpServletRequest httpRequest) {
        if (!matchesServiceKey(suppliedServiceKey)) {
            throw BusinessException.forbidden("DSH 服务鉴权失败");
        }
        String idToken = request == null ? null : request.idToken();
        if (idToken == null || idToken.isBlank()) {
            throw BusinessException.unauthorized("缺少 SSO ID Token");
        }
        return ResponseResult.success(ssoSessionService.login(
                idToken, clientIp(httpRequest), httpRequest.getHeader("User-Agent")));
    }

    private boolean matchesServiceKey(String supplied) {
        if (serviceKey.isEmpty() || supplied == null || supplied.isBlank()) return false;
        return MessageDigest.isEqual(
                serviceKey.getBytes(StandardCharsets.UTF_8),
                supplied.trim().getBytes(StandardCharsets.UTF_8));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
