package com.brad.pms.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    @Test
    void rejectsJwtSecretShorterThan32Bytes() {
        assertThatThrownBy(() -> new JwtTokenProvider("too-short", 30))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    void accepts32ByteSecretAndRoundTripsAccessToken() {
        JwtTokenProvider provider = new JwtTokenProvider("12345678901234567890123456789012", 30);

        LoginUser parsed = provider.parseToken(provider.createAccessToken(7L, 9L));

        assertThat(parsed.getId()).isEqualTo(7L);
        assertThat(parsed.getSessionId()).isEqualTo(9L);
    }

    @Test
    void productionDefaultsRequireExplicitJwtSecretAndDoNotExposeResetTokens() throws Exception {
        String source = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(source).contains("secret: ${PMS_JWT_SECRET:}");
        assertThat(source).contains("password-reset-expose-token: ${PMS_PASSWORD_RESET_EXPOSE_TOKEN:false}");
        assertThat(source).contains("allowed-origins: ${PMS_CORS_ALLOWED_ORIGINS:");
    }
}
