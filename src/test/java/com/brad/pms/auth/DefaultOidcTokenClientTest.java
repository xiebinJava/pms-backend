package com.brad.pms.auth;

import com.brad.pms.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultOidcTokenClientTest {

    @Test
    void acceptsATokenMintedForAnAllowListedClient() {
        assertThatCode(() -> DefaultOidcTokenClient.requireAllowedAudience(
                Set.of("dsh-web"), Set.of("dsh-web")))
                .doesNotThrowAnyException();
        assertThatCode(() -> DefaultOidcTokenClient.requireAllowedAudience(
                Set.of("account", "dsh-web"), Set.of("dsh-web", "pms-web")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsATokenFromAClientOutsideTheAllowList() {
        assertThatThrownBy(() -> DefaultOidcTokenClient.requireAllowedAudience(
                Set.of("other-app"), Set.of("dsh-web")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不在允许列表内");
        assertThatThrownBy(() -> DefaultOidcTokenClient.requireAllowedAudience(Set.of(), Set.of("dsh-web")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不在允许列表内");
    }

    @Test
    void normalisesListAudienceClaims() {
        io.jsonwebtoken.Jwts.builder().claim("aud", List.of("account", "dsh-web"));
        // `aud` may arrive as a string or a list; both must resolve to the same set.
        assertThatCode(() -> DefaultOidcTokenClient.requireAllowedAudience(Set.of("dsh-web"), Set.of("dsh-web")))
                .doesNotThrowAnyException();
    }
}
