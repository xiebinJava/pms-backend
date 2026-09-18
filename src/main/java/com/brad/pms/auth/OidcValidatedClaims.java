package com.brad.pms.auth;

/** Claims from an ID token after signature and OIDC protocol validation. */
public record OidcValidatedClaims(String issuer, String subject, String email, boolean emailVerified) {
}
