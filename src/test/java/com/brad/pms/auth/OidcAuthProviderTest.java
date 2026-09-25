package com.brad.pms.auth;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.response.OidcStartDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OidcAuthProviderTest {

    private AuthProviderProperties properties;
    private OidcTokenClient tokenClient;
    private OidcStateStore stateStore;
    private OidcAuthProvider provider;

    @BeforeEach
    void setUp() {
        properties = new AuthProviderProperties();
        properties.getOidc().setEnabled(true);
        properties.getOidc().setDisplayName("Company SSO");
        properties.getOidc().setClientId("pms");
        properties.getOidc().setClientSecret("secret");
        properties.getOidc().setRedirectUri("http://localhost:5173/login/oidc/callback");
        properties.getOidc().setAuthorizationUri("https://idp.example.com/authorize");
        properties.getOidc().setTokenUri("https://idp.example.com/token");
        properties.getOidc().setUserinfoUri("https://idp.example.com/userinfo");
        tokenClient = mock(OidcTokenClient.class);
        stateStore = new InMemoryOidcStateStore();
        provider = new OidcAuthProvider(properties, tokenClient, stateStore);
    }

    @Test
    void startBuildsAuthorizationUrlWithPkce() {
        OidcStartDTO start = provider.start();

        assertThat(start.getState()).isNotBlank();
        assertThat(start.getAuthorizationUrl())
                .startsWith("https://idp.example.com/authorize?")
                .contains("response_type=code")
                .contains("client_id=pms")
                .contains("code_challenge_method=S256")
                .contains("nonce=")
                .contains("state=" + start.getState());
        assertThat(stateStore.consume(start.getState(), java.time.Instant.now())).isPresent();
    }

    @Test
    void startRequiresEnabledProvider() {
        properties.getOidc().setEnabled(false);
        assertThatThrownBy(() -> provider.start()).hasMessage("SSO 登录未启用");
    }

    @Test
    void exchangeLinksEmailFromUserInfo() {
        OidcStartDTO start = provider.start();
        when(tokenClient.exchange(any(), any(), eq("code-1"), any()))
                .thenReturn(new OidcTokenResponse("access", "signed.id.token"));
        when(tokenClient.validateIdToken(any(), any(), eq("signed.id.token"), any()))
                .thenReturn(new OidcValidatedClaims("https://idp.example.com", "subject-1",
                        "alex.zhang@example.com", true));
        when(tokenClient.userInfo(eq("https://idp.example.com/userinfo"), eq("access")))
                .thenReturn(Map.of("email", "Alex.Zhang@Example.com", "email_verified", "true"));

        AuthenticatedIdentity identity = provider.exchange("code-1", start.getState());

        assertThat(identity.emailNormalized()).isEqualTo("alex.zhang@example.com");
        assertThat(identity.providerType()).isEqualTo("oidc");
        assertThat(identity.externalIssuer()).isEqualTo("https://idp.example.com");
        assertThat(identity.externalSubject()).isEqualTo("subject-1");
        verify(tokenClient).exchange(any(), any(), eq("code-1"), any());
        verify(tokenClient).validateIdToken(any(), any(), eq("signed.id.token"), any());
    }

    @Test
    void exchangeRejectsReusedState() {
        OidcStartDTO start = provider.start();
        stateStore.consume(start.getState(), java.time.Instant.now());

        assertThatThrownBy(() -> provider.exchange("code-1", start.getState()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("SSO 状态已失效，请重新登录");
    }

}
