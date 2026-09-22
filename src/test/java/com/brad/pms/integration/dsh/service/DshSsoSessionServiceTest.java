package com.brad.pms.integration.dsh.service;

import com.brad.pms.auth.AuthProviderProperties;
import com.brad.pms.auth.OidcEndpoints;
import com.brad.pms.auth.OidcTokenClient;
import com.brad.pms.auth.OidcValidatedClaims;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.response.LoginResponse;
import com.brad.pms.dto.response.UserDTO;
import com.brad.pms.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DshSsoSessionServiceTest {

    @Mock OidcTokenClient tokenClient;
    @Mock AuthService authService;

    @Test
    void exchangesAVerifiedIdTokenForANormalPmsSession() {
        when(tokenClient.discover("http://idp/realms/pms"))
                .thenReturn(new OidcEndpoints("http://idp/realms/pms", "http://idp/auth", "http://idp/token",
                        "http://idp/userinfo", "http://idp/certs"));
        when(tokenClient.validateRelayedIdToken(any(), any(), eq("id-token"), any()))
                .thenReturn(new OidcValidatedClaims("http://idp/realms/pms", "subject-1", "admin@pms.com", true));
        UserDTO user = new UserDTO();
        user.setId(2L);
        when(authService.loginFromVerifiedIdentity(any(), any(), any()))
                .thenReturn(new LoginResponse("pms-access", "pms-refresh", user));

        LoginResponse response = service(true, "dsh-web").login("id-token", "127.0.0.1", "test-agent");

        assertThat(response.getToken()).isEqualTo("pms-access");
        assertThat(response.getRefreshToken()).isEqualTo("pms-refresh");
        ArgumentCaptor<com.brad.pms.auth.AuthenticatedIdentity> captor =
                ArgumentCaptor.forClass(com.brad.pms.auth.AuthenticatedIdentity.class);
        verify(authService).loginFromVerifiedIdentity(captor.capture(), eq("127.0.0.1"), eq("test-agent"));
        assertThat(captor.getValue().emailNormalized()).isEqualTo("admin@pms.com");
        assertThat(captor.getValue().externalSubject()).isEqualTo("subject-1");
        assertThat(captor.getValue().externalIssuer()).isEqualTo("http://idp/realms/pms");
    }

    @Test
    void refusesWhenTheDeploymentDidNotEnableSsoSessions() {
        assertThatThrownBy(() -> service(false, "dsh-web").login("id-token", "ip", "ua"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未开启 SSO 会话签发");
    }

    @Test
    void refusesRelayedTokensFromUnauthorizedClients() {
        when(tokenClient.discover(any())).thenReturn(new OidcEndpoints("http://idp/realms/pms", "a", "t", "u", "c"));
        when(tokenClient.validateRelayedIdToken(any(), any(), any(), any()))
                .thenThrow(BusinessException.forbidden("SSO 令牌的客户端不在允许列表内"));

        assertThatThrownBy(() -> service(true, "dsh-web").login("id-token", "ip", "ua"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不在允许列表内");
    }

    @Test
    void allowListsOnlyTheConfiguredClients() {
        assertThat(service(true, "dsh-web,other-client").allowedClients())
                .isEqualTo(Set.of("dsh-web", "other-client"));
    }

    private DshSsoSessionService service(boolean enabled, String allowedClients) {
        AuthProviderProperties properties = new AuthProviderProperties();
        AuthProviderProperties.Oidc oidc = properties.getOidc();
        oidc.setEnabled(true);
        oidc.setIssuer("http://idp/realms/pms");
        oidc.setClientId("pms-web");
        return new DshSsoSessionService(tokenClient, properties, authService, enabled, allowedClients);
    }
}
