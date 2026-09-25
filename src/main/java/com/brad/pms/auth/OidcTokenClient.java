package com.brad.pms.auth;

import java.util.Map;
import java.util.Set;

public interface OidcTokenClient {

    OidcEndpoints discover(String issuer);

    OidcTokenResponse exchange(AuthProviderProperties.Oidc oidc, OidcEndpoints endpoints, String code, String codeVerifier);

    OidcValidatedClaims validateIdToken(AuthProviderProperties.Oidc oidc, OidcEndpoints endpoints,
                                        String idToken, String nonce);

    /**
     * Validates an ID token that another trusted first-party client (the DSH host)
     * obtained for the same issuer. There is no nonce to compare, so the caller
     * must be an authenticated service and the token must be issued for one of
     * the explicitly allowed client audiences.
     */
    OidcValidatedClaims validateRelayedIdToken(AuthProviderProperties.Oidc oidc, OidcEndpoints endpoints,
                                               String idToken, Set<String> allowedAudiences);

    Map<String, Object> userInfo(String userinfoUri, String accessToken);
}
