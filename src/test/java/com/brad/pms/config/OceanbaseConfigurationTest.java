package com.brad.pms.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;
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
        assertThat(source.getProperty("spring.datasource.hikari.connection-timeout"))
                .isEqualTo("${PMS_DB_CONNECTION_TIMEOUT_MS:3000}");
        assertThat(source.getProperty("spring.datasource.hikari.validation-timeout"))
                .isEqualTo("${PMS_DB_VALIDATION_TIMEOUT_MS:1000}");
        assertThat(source.getProperty("spring.h2.console.enabled")).isNull();
    }

    @Test
    void defaultRuntimeUsesOceanbaseAndRequiresExplicitJwtSecret() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
                "application", new FileSystemResource("src/main/resources/application.yml"));
        PropertySource<?> source = sources.get(0);

        assertThat(source.getProperty("spring.profiles.default")).isEqualTo("oceanbase");
        assertThat(source.getProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(source.getProperty("pms.jwt.secret")).isEqualTo("${PMS_JWT_SECRET:}");
        assertThat(source.getProperty("pms.security.cors.allowed-origins"))
                .isEqualTo("${PMS_CORS_ALLOWED_ORIGINS:http://localhost:57979,http://127.0.0.1:57979}");
    }

    @Test
    void composeSeparatesRuntimeAndMigrationDatabaseAccounts() throws Exception {
        String compose = Files.readString(Path.of("docker-compose.example.yml"));

        assertThat(compose).contains("OCEANBASE_USER: pms_app");
        assertThat(compose).contains("OCEANBASE_PASSWORD: ${PMS_APP_PASSWORD:?set PMS_APP_PASSWORD}");
        assertThat(compose).contains("OCEANBASE_USER: pms_migrator");
        assertThat(compose).contains("OCEANBASE_PASSWORD: ${PMS_MIGRATOR_PASSWORD:?set PMS_MIGRATOR_PASSWORD}");
        assertThat(compose).doesNotContain("OCEANBASE_USER: ${OCEANBASE_USER:-root@sys}");
    }

    @Test
    void mysqlProfileAlsoRequiresExplicitCredentials() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
                "mysql", new ClassPathResource("application-mysql.yml"));
        PropertySource<?> source = sources.get(0);

        assertThat(source.getProperty("spring.datasource.username"))
                .isEqualTo("${MYSQL_USER}");
        assertThat(source.getProperty("spring.datasource.password"))
                .isEqualTo("${MYSQL_PASSWORD}");
        assertThat(source.getProperty("spring.flyway.enabled")).isEqualTo(true);
    }
}
