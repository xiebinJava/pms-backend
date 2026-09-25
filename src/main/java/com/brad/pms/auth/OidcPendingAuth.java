package com.brad.pms.auth;

import java.time.Instant;

public record OidcPendingAuth(String codeVerifier, String nonce, Instant expiresAt) {
}
