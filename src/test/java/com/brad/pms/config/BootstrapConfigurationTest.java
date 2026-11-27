package com.brad.pms.config;

import com.brad.pms.mapper.UserMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class BootstrapConfigurationTest {
    @ParameterizedTest
    @ValueSource(strings = {"", "short", "PmsAdmin123!", "change-me-placeholder"})
    void rejectsMissingAndSharedCredentialsOnEmptyPersistentDatabase(String password) {
        var environment = new MockEnvironment().withProperty("PMS_BOOTSTRAP_ADMIN_PASSWORD", password);
        environment.setActiveProfiles("mysql", "test");
        var initializer = new DataInitializer(mock(UserMapper.class), null, null, null, null, null, null,
                null, null, environment);
        assertThatThrownBy(initializer::run).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PMS_BOOTSTRAP_ADMIN_PASSWORD");
    }
}
