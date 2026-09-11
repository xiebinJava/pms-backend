package com.brad.pms.auth;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.response.OidcStartDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
                .thenReturn(new OidcTokenResponse("access", idToken("ignored@example.com")));
        when(tokenClient.userInfo(eq("https://idp.example.com/userinfo"), eq("access")))
                .thenReturn(Map.of("email", "Alex.Zhang@Example.com", "email_verified", "true"));

        AuthenticatedIdentity identity = provider.exchange("code-1", start.getState());

        assertThat(identity.emailNormalized()).isEqualTo("alex.zhang@example.com");
        assertThat(identity.providerType()).isEqualTo("oidc");
        verify(tokenClient).exchange(any(), any(), eq("code-1"), any());
    }

    @Test
    void exchangeRejectsReusedState() {
        OidcStartDTO start = provider.start();
        stateStore.consume(start.getState(), java.time.Instant.now());

        assertThatThrownBy(() -> provider.exchange("code-1", start.getState()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("SSO 状态已失效，请重新登录");
    }

    @Test
    void emailFromIdTokenReadsPayload() {
        assertThat(provider.emailFromIdToken(idToken("alex.zhang@example.com")))
                .isEqualTo("alex.zhang@example.com");
    }

    private static String idToken(String email) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"email\":\"" + email + "\"}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".sig";
    }
}
