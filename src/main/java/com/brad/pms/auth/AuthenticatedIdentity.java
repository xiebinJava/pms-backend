package com.brad.pms.auth;

/** Identity proven by an external directory or IdP. Never creates a PMS user. */
public record AuthenticatedIdentity(String emailNormalized, String loginName, String providerType) {
}
