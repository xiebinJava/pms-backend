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
        user.setNameZh("谢斌");
        user.setEmail("brad@example.com");

        assertThat(Convertors.userDisplayName(user)).isEqualTo("谢斌");
    }

    @Test
    void displayNameUsesAvailableEnglishNameButHidesGeneratedCompatibilityAlias() {
        UserDO named = new UserDO();
        named.setUsername("Brad.Xie");
        named.setEmail("brad@example.com");
        assertThat(Convertors.userDisplayName(named)).isEqualTo("Brad.Xie");

        UserDO generated = new UserDO();
        generated.setUsername("user-0123456789abcdef");
        generated.setEmail("person@example.com");
        assertThat(Convertors.userDisplayName(generated)).isEqualTo("person@example.com");
    }
}
