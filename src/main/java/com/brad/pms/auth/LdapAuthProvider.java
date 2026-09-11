package com.brad.pms.auth;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.config.EnterpriseDataMigration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LdapAuthProvider implements AuthProvider {

    private final AuthProviderProperties properties;
    private final LdapDirectoryClient directoryClient;

    @Override
    public String type() {
        return "ldap";
    }

    @Override
    public boolean enabled() {
        return properties.getLdap().isEnabled();
    }

    @Override
    public String displayName() {
        return properties.getLdap().getDisplayName();
    }

    @Override
    public String startPath() {
        return null;
    }

    public AuthenticatedIdentity authenticate(String identifier, String password) {
        if (!enabled()) {
            throw BusinessException.error("目录登录未启用");
        }
        String email = directoryClient.authenticate(properties.getLdap(), identifier, password);
        String normalized = EnterpriseDataMigration.normalizeEmail(email);
        String loginName = identifier == null ? normalized : identifier.trim().toLowerCase();
        return new AuthenticatedIdentity(normalized, loginName, type());
    }
}
