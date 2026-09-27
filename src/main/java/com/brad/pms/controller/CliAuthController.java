package com.brad.pms.controller;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.response.LoginResponse;
import com.brad.pms.integration.cli.api.CliAuthDtos;
import com.brad.pms.security.IgnoreAuth;
import com.brad.pms.security.UserContext;
import com.brad.pms.service.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Browser-approved PKCE loopback authorization for the local PMS CLI. */
@RestController
@RequestMapping("/integration/cli/v1")
public class CliAuthController {

    private static final String CLIENT_ID = "pms-cli";
    private static final String CODE_CHALLENGE_METHOD = "S256";
    private static final int CODE_EXPIRE_SECONDS = 120;
    private static final int ACCESS_EXPIRE_SECONDS = 1800;

    private final AuthService authService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, PendingAuthorization> pending = new ConcurrentHashMap<>();

    public CliAuthController(AuthService authService) {
        this.authService = authService;
    }

    public static boolean isAllowedLoopbackRedirect(String redirectUri) {
        try {
            URI uri = new URI(redirectUri);
            boolean hostAllowed = "localhost".equalsIgnoreCase(uri.getHost())
                    || "127.0.0.1".equals(uri.getHost())
                    || "[::1]".equals(uri.getHost());
            return "http".equalsIgnoreCase(uri.getScheme())
                    && hostAllowed
                    && uri.getPort() >= 1024
                    && uri.getPort() <= 65535
                    && "/callback".equals(uri.getPath())
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (URISyntaxException | IllegalArgumentException ignored) {
            return false;
        }
    }

    @PostMapping("/authorize/approve")
    public ResponseResult<CliAuthDtos.ApprovalResponse> approve(@RequestBody CliAuthDtos.ApproveRequest request) {
        requireClientAndPkce(request.clientId(), request.redirectUri(), request.codeChallenge(), request.codeChallengeMethod());
        if (UserContext.get() == null || UserContext.userIdOrNull() == null) {
            throw BusinessException.unauthorized("请先登录 PMS");
        }
        String code = randomToken(32);
        pending.put(code, new PendingAuthorization(
                UserContext.userId(), request.redirectUri(), request.clientId(), request.state(),
                request.codeChallenge(), Instant.now().plusSeconds(CODE_EXPIRE_SECONDS)));
        return ResponseResult.success(new CliAuthDtos.ApprovalResponse(
                code, request.redirectUri(), request.state(), CODE_EXPIRE_SECONDS));
    }

    @IgnoreAuth
    @PostMapping("/token")
    public ResponseResult<CliAuthDtos.TokenResponse> token(@RequestBody CliAuthDtos.TokenRequest request) {
        if (request == null || request.grantType() == null) {
            throw BusinessException.error("grantType 不能为空");
        }
        if ("authorization_code".equals(request.grantType())) {
            return ResponseResult.success(exchangeAuthorizationCode(request));
        }
        if ("refresh_token".equals(request.grantType())) {
            return ResponseResult.success(toTokenResponse(authService.refresh(request.refreshToken())));
        }
        throw BusinessException.error("不支持的 CLI 授权类型");
    }

    @IgnoreAuth
    @PostMapping("/revoke")
    public ResponseResult<CliAuthDtos.RevokeResponse> revoke(@RequestBody CliAuthDtos.RevokeRequest request) {
        authService.revokeRefreshToken(request == null ? null : request.refreshToken());
        return ResponseResult.success(new CliAuthDtos.RevokeResponse(true));
    }

    private CliAuthDtos.TokenResponse exchangeAuthorizationCode(CliAuthDtos.TokenRequest request) {
        if (!CLIENT_ID.equals(request.clientId()) || !isAllowedLoopbackRedirect(request.redirectUri())
                || request.code() == null || request.codeVerifier() == null) {
            throw BusinessException.error("CLI 授权参数无效");
        }
        PendingAuthorization authorization = pending.remove(request.code());
        if (authorization == null || authorization.expiresAt().isBefore(Instant.now())) {
            throw BusinessException.unauthorized("授权码已失效或已使用");
        }
        if (!authorization.clientId().equals(request.clientId())
                || !authorization.redirectUri().equals(request.redirectUri())
                || !verifyPkce(request.codeVerifier(), authorization.codeChallenge())) {
            throw BusinessException.unauthorized("PKCE 校验失败");
        }
        return toTokenResponse(authService.issueCliSession(authorization.userId()));
    }

    private static void requireClientAndPkce(String clientId, String redirectUri,
                                             String codeChallenge, String codeChallengeMethod) {
        if (!CLIENT_ID.equals(clientId) || !isAllowedLoopbackRedirect(redirectUri)
                || codeChallenge == null || codeChallenge.isBlank()
                || !CODE_CHALLENGE_METHOD.equals(codeChallengeMethod)) {
            throw BusinessException.error("CLI 授权参数无效");
        }
    }

    private static boolean verifyPkce(String verifier, String challenge) {
        try {
            String computed = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            return MessageDigest.isEqual(computed.getBytes(StandardCharsets.US_ASCII),
                    challenge.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            return false;
        }
    }

    private CliAuthDtos.TokenResponse toTokenResponse(LoginResponse response) {
        return new CliAuthDtos.TokenResponse(response.getAccessToken(), response.getRefreshToken(),
                ACCESS_EXPIRE_SECONDS, response.getUser());
    }

    private String randomToken(int bytesLength) {
        byte[] bytes = new byte[bytesLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record PendingAuthorization(Long userId, String redirectUri, String clientId, String state,
                                        String codeChallenge, Instant expiresAt) {
    }
}
