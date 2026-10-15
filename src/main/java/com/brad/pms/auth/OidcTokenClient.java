package com.brad.pms.auth;

import java.util.Map;

public interface OidcTokenClient {

    OidcEndpoints discover(String issuer);

    OidcTokenResponse exchange(AuthProviderProperties.Oidc oidc, OidcEndpoints endpoints, String code, String codeVerifier);

    OidcValidatedClaims validateIdToken(AuthProviderProperties.Oidc oidc, OidcEndpoints endpoints,
                                        String idToken, String nonce);

    Map<String, Object> userInfo(String userinfoUri, String accessToken);
}
