package com.brad.pms.auth;

public interface LdapDirectoryClient {

    /** Bind the user and return the directory email. */
    String authenticate(AuthProviderProperties.Ldap ldap, String identifier, String password);
}
