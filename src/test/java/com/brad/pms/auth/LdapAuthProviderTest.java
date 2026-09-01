package com.brad.pms.auth;

import com.brad.pms.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LdapAuthProviderTest {

    private AuthProviderProperties properties;
    private LdapDirectoryClient directoryClient;
    private LdapAuthProvider provider;

    @BeforeEach
    void setUp() {
        properties = new AuthProviderProperties();
        properties.getLdap().setEnabled(true);
        properties.getLdap().setDisplayName("Directory");
        directoryClient = mock(LdapDirectoryClient.class);
        provider = new LdapAuthProvider(properties, directoryClient);
    }

    @Test
    void authenticateNormalizesDirectoryEmail() {
        when(directoryClient.authenticate(any(), eq("Alex.Zhang@Example.com"), eq("Secret12!@")))
                .thenReturn("Alex.Zhang@Example.com");

        AuthenticatedIdentity identity = provider.authenticate("Alex.Zhang@Example.com", "Secret12!@");

        assertThat(identity.emailNormalized()).isEqualTo("alex.zhang@example.com");
        assertThat(identity.providerType()).isEqualTo("ldap");
    }

    @Test
    void authenticateRequiresEnabledProvider() {
        properties.getLdap().setEnabled(false);
        assertThatThrownBy(() -> provider.authenticate("alex.zhang@example.com", "Secret12!@"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("目录登录未启用");
    }

    @Test
    void filterEscapesSpecialCharacters() {
        assertThat(LdapFilters.apply("(mail={0})", "a*(b)"))
                .isEqualTo("(mail=a\\2a\\28b\\29)");
    }
}
