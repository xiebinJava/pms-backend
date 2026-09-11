package com.brad.pms.auth;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.config.EnterpriseDataMigration;
import com.brad.pms.dto.response.OidcStartDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OidcAuthProvider implements AuthProvider {

    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final AuthProviderProperties properties;
    private final OidcTokenClient tokenClient;
    private final OidcStateStore stateStore;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String type() {
        return "oidc";
    }

    @Override
    public boolean enabled() {
        return properties.getOidc().isEnabled();
    }

    @Override
    public String displayName() {
        return properties.getOidc().getDisplayName();
    }

    @Override
    public String startPath() {
        return enabled() ? "/auth/oidc/start" : null;
    }

    public OidcStartDTO start() {
        AuthProviderProperties.Oidc oidc = properties.getOidc();
        if (!oidc.isEnabled()) {
            throw BusinessException.error("SSO 登录未启用");
        }
        OidcEndpoints endpoints = resolveEndpoints(oidc);
        String state = randomUrlToken(24);
        String verifier = randomUrlToken(48);
        stateStore.put(state, new OidcPendingAuth(verifier, Instant.now().plus(STATE_TTL)));
        String url = endpoints.authorizationUri()
                + (endpoints.authorizationUri().contains("?") ? "&" : "?")
                + "response_type=code"
                + "&client_id=" + enc(oidc.getClientId())
                + "&redirect_uri=" + enc(oidc.getRedirectUri())
                + "&scope=" + enc(oidc.getScopes())
                + "&state=" + enc(state)
                + "&code_challenge=" + enc(codeChallenge(verifier))
                + "&code_challenge_method=S256";
        return new OidcStartDTO(url, state);
    }

    public AuthenticatedIdentity exchange(String code, String state) {
        AuthProviderProperties.Oidc oidc = properties.getOidc();
        if (!oidc.isEnabled()) {
            throw BusinessException.error("SSO 登录未启用");
        }
        OidcPendingAuth pending = stateStore.consume(state, Instant.now())
                .orElseThrow(() -> BusinessException.unauthorized("SSO 状态已失效，请重新登录"));
        OidcEndpoints endpoints = resolveEndpoints(oidc);
        OidcTokenResponse tokens = tokenClient.exchange(oidc, endpoints, code, pending.codeVerifier());
        String email = emailFromClaims(tokenClient.userInfo(endpoints.userinfoUri(), tokens.accessToken()));
        if (email == null) {
            email = emailFromIdToken(tokens.idToken());
        }
        if (email == null) {
            throw BusinessException.unauthorized("SSO 未返回邮箱，无法登录");
        }
        String normalized = EnterpriseDataMigration.normalizeEmail(email);
        return new AuthenticatedIdentity(normalized, normalized, type());
    }

    OidcEndpoints resolveEndpoints(AuthProviderProperties.Oidc oidc) {
        if (oidc.getClientId() == null || oidc.getClientId().isBlank()
                || oidc.getClientSecret() == null || oidc.getClientSecret().isBlank()
                || oidc.getRedirectUri() == null || oidc.getRedirectUri().isBlank()) {
            throw BusinessException.error("SSO 未配置客户端");
        }
        if (oidc.getAuthorizationUri() != null && !oidc.getAuthorizationUri().isBlank()
                && oidc.getTokenUri() != null && !oidc.getTokenUri().isBlank()) {
            return new OidcEndpoints(oidc.getAuthorizationUri(), oidc.getTokenUri(),
                    oidc.getUserinfoUri() == null ? "" : oidc.getUserinfoUri());
        }
        if (oidc.getIssuer() == null || oidc.getIssuer().isBlank()) {
            throw BusinessException.error("SSO 未配置发行方");
        }
        return tokenClient.discover(oidc.getIssuer());
    }

    static String emailFromClaims(Map<String, Object> claims) {
        if (claims == null || claims.isEmpty()) return null;
        Object verified = claims.get("email_verified");
        if (verified != null && !isTruthy(verified)) {
            return null;
        }
        Object email = claims.get("email");
        return email == null ? null : String.valueOf(email);
    }

    String emailFromIdToken(String idToken) {
        if (idToken == null || idToken.isBlank()) return null;
        String[] parts = idToken.split("\\.");
        if (parts.length < 2) return null;
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode node = objectMapper.readTree(payload);
            if (node.has("email_verified") && !isTruthy(node.get("email_verified").asText())) {
                return null;
            }
            return node.path("email").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean bool) return bool;
        String text = String.valueOf(value);
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    private String randomUrlToken(int bytes) {
        byte[] value = new byte[bytes];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String codeChallenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("无法计算 PKCE 挑战", e);
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
