package com.brad.pms.auth;

public record OidcEndpoints(String authorizationUri, String tokenUri, String userinfoUri,
                            String jwksUri, String issuer) {

    public OidcEndpoints(String authorizationUri, String tokenUri, String userinfoUri) {
        this(authorizationUri, tokenUri, userinfoUri, "", "");
    }
}
