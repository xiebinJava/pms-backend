package com.brad.pms.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OceanbaseConfigurationTest {

    @Test
    void oceanbaseProfileUsesTheDedicatedDatabaseAndRuntimeCredentials() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
                "oceanbase", new ClassPathResource("application-oceanbase.yml"));
        PropertySource<?> source = sources.get(0);

        assertThat(source.getProperty("spring.datasource.driver-class-name"))
                .isEqualTo("com.mysql.cj.jdbc.Driver");
        assertThat(source.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:mysql://${OCEANBASE_HOST:127.0.0.1}:${OCEANBASE_PORT:2881}/${OCEANBASE_DATABASE:brad_pms}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true");
        assertThat(source.getProperty("spring.datasource.username"))
                .isEqualTo("${OCEANBASE_USER}");
        assertThat(source.getProperty("spring.datasource.password"))
                .isEqualTo("${OCEANBASE_PASSWORD}");
        assertThat(source.getProperty("spring.h2.console.enabled")).isNull();
    }
}
