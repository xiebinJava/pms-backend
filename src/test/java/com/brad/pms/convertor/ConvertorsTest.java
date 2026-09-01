package com.brad.pms.convertor;

import com.brad.pms.entity.UserDO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConvertorsTest {
    @Test
    void displayNameFallsBackToEmailWhenNamesAreMissing() {
        UserDO user = new UserDO();
        user.setEmail("person@example.com");

        assertThat(Convertors.userDisplayName(user)).isEqualTo("person@example.com");
    }

    @Test
    void displayNameUsesAvailableChineseNameWithoutAddingEmptyEnglishSuffix() {
        UserDO user = new UserDO();
        user.setNameZh("张伟");
        user.setEmail("alex.zhang@example.com");

        assertThat(Convertors.userDisplayName(user)).isEqualTo("张伟");
    }

    @Test
    void displayNameUsesAvailableEnglishNameButHidesGeneratedCompatibilityAlias() {
        UserDO named = new UserDO();
        named.setUsername("Alex.Zhang");
        named.setEmail("alex.zhang@example.com");
        assertThat(Convertors.userDisplayName(named)).isEqualTo("Alex.Zhang");

        UserDO generated = new UserDO();
        generated.setUsername("user-0123456789abcdef");
        generated.setEmail("person@example.com");
        assertThat(Convertors.userDisplayName(generated)).isEqualTo("person@example.com");
    }
}
