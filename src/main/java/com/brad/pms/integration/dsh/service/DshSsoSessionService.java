package com.brad.pms.integration.dsh.service;

import com.brad.pms.auth.AuthProviderProperties;
import com.brad.pms.auth.AuthenticatedIdentity;
import com.brad.pms.auth.OidcEndpoints;
import com.brad.pms.auth.OidcTokenClient;
import com.brad.pms.auth.OidcValidatedClaims;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.config.EnterpriseDataMigration;
import com.brad.pms.dto.response.LoginResponse;
import com.brad.pms.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Turns a Keycloak ID Token that DSH already obtained through SSO into a normal
 * PMS session, so DSH and PMS act as the same person without the browser bridge.
 *
 * <p>The token is verified by PMS itself (signature via the realm JWKS, issuer,
 * expiry) and its audience must be one of the explicitly allow-listed clients,
 * so DSH relays a signed assertion from the IdP rather than an identity of its
 * own invention.</p>
 */
@Service
public class DshSsoSessionService {

    private final OidcTokenClient tokenClient;
    private final AuthProviderProperties properties;
    private final AuthService authService;
    private final boolean enabled;
    private final Set<String> allowedClients;
    private volatile OidcEndpoints endpoints;

    public DshSsoSessionService(OidcTokenClient tokenClient,
                                AuthProviderProperties properties,
                                AuthService authService,
                                @Value("${pms.dsh.sso-session-enabled:false}") boolean enabled,
                                @Value("${pms.dsh.sso-allowed-clients:dsh-web}") String allowedClients) {
        this.tokenClient = tokenClient;
        this.properties = properties;
        this.authService = authService;
        this.enabled = enabled;
        this.allowedClients = Arrays.stream(allowedClients.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public LoginResponse login(String idToken, String ip, String userAgent) {
        if (!enabled) throw BusinessException.forbidden("PMS 未开启 SSO 会话签发");
        AuthProviderProperties.Oidc oidc = properties.getOidc();
        if (oidc == null || !oidc.isEnabled() || oidc.getIssuer() == null || oidc.getIssuer().isBlank()) {
            throw BusinessException.error("PMS 未配置 SSO，无法校验 DSH 令牌");
        }
        OidcValidatedClaims claims = tokenClient.validateRelayedIdToken(oidc, endpoints(oidc), idToken, allowedClients);
        String email = claims.email() == null ? null : EnterpriseDataMigration.normalizeEmail(claims.email());
        AuthenticatedIdentity identity = new AuthenticatedIdentity(
                email, claims.subject(), "oidc", claims.issuer(), claims.subject());
        return authService.loginFromVerifiedIdentity(identity, ip, userAgent);
    }

    /** The realm metadata is static, so discovery runs once per process. */
    private OidcEndpoints endpoints(AuthProviderProperties.Oidc oidc) {
        OidcEndpoints cached = endpoints;
        if (cached != null) return cached;
        synchronized (this) {
            if (endpoints == null) endpoints = tokenClient.discover(oidc.getIssuer());
            return endpoints;
        }
    }

    Set<String> allowedClients() {
        return allowedClients;
    }
}
