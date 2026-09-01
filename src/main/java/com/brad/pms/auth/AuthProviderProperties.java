package com.brad.pms.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "pms.auth")
public class AuthProviderProperties {

    /** Local email/password form. Leave on unless the deployment is directory-only. */
    private boolean localEnabled = true;

    private final Oidc oidc = new Oidc();
    private final Ldap ldap = new Ldap();

    @Data
    public static class Oidc {
        private boolean enabled = false;
        private String displayName = "SSO";
        private String issuer = "";
        private String authorizationUri = "";
        private String tokenUri = "";
        private String userinfoUri = "";
        private String clientId = "";
        private String clientSecret = "";
        private String redirectUri = "http://localhost:5173/login/oidc/callback";
        private String scopes = "openid email profile";
    }

    @Data
    public static class Ldap {
        private boolean enabled = false;
        private String displayName = "Directory";
        private String url = "";
        private String baseDn = "";
        private String userSearchFilter = "(mail={0})";
        private String userDnPattern = "";
        private String managerDn = "";
        private String managerPassword = "";
        private String emailAttribute = "mail";
    }
}
