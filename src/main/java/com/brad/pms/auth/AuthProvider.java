package com.brad.pms.auth;

import com.brad.pms.dto.response.AuthProviderDTO;

/** Pluggable sign-in method. Local password stays the default; OIDC/LDAP opt in. */
public interface AuthProvider {

    String type();

    boolean enabled();

    String displayName();

    /** Relative API path that starts this provider, or null when it has no start step. */
    String startPath();

    default AuthProviderDTO toDto() {
        return new AuthProviderDTO(type(), enabled(), displayName(), startPath());
    }
}
