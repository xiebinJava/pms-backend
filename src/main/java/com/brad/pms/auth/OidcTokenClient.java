package com.brad.pms.auth;

import java.util.Map;

public interface OidcTokenClient {

    OidcEndpoints discover(String issuer);

    OidcTokenResponse exchange(AuthProviderProperties.Oidc oidc, OidcEndpoints endpoints, String code, String codeVerifier);

    Map<String, Object> userInfo(String userinfoUri, String accessToken);
}
