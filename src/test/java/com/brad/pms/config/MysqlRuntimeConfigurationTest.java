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

class MysqlRuntimeConfigurationTest {

    @Test
    void mysqlProfileUsesExplicitCredentialsAndFlyway() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
                "mysql", new ClassPathResource("application-mysql.yml"));
        PropertySource<?> source = sources.get(0);

        assertThat(source.getProperty("spring.datasource.driver-class-name"))
                .isEqualTo("com.mysql.cj.jdbc.Driver");
        assertThat(source.getProperty("spring.datasource.url"))
                .asString()
                .contains("${MYSQL_HOST:127.0.0.1}")
                .contains("${MYSQL_PORT:3306}")
                .contains("${MYSQL_DB:pms}");
        assertThat(source.getProperty("spring.datasource.username")).isEqualTo("${MYSQL_USER}");
        assertThat(source.getProperty("spring.datasource.password")).isEqualTo("${MYSQL_PASSWORD}");
        assertThat(source.getProperty("spring.flyway.enabled")).isEqualTo(true);
        assertThat(source.getProperty("spring.h2.console.enabled")).isNull();
    }

    @Test
    void defaultRuntimeUsesMysqlAndRequiresExplicitJwtSecret() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
                "application", new FileSystemResource("src/main/resources/application.yml"));
        PropertySource<?> source = sources.get(0);

        assertThat(source.getProperty("spring.profiles.default")).isEqualTo("mysql");
        assertThat(source.getProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(source.getProperty("pms.jwt.secret")).isEqualTo("${PMS_JWT_SECRET:}");
        assertThat(source.getProperty("pms.security.cors.allowed-origins"))
                .isEqualTo("${PMS_CORS_ALLOWED_ORIGINS:http://localhost:57979,http://127.0.0.1:57979}");
    }

    @Test
    void composeUsesMysqlAndTheApplicationAccount() throws Exception {
        String compose = Files.readString(Path.of("docker-compose.example.yml"));

        assertThat(compose).contains("image: mysql:8.4");
        assertThat(compose).contains("MYSQL_USER: ${MYSQL_USER:?set MYSQL_USER}");
        assertThat(compose).contains("MYSQL_PASSWORD: ${MYSQL_PASSWORD:?set MYSQL_PASSWORD}");
        assertThat(compose).contains("SPRING_PROFILES_ACTIVE: mysql");
        assertThat(compose).doesNotContain("oceanbase");
        assertThat(compose).doesNotContain("accounts-init");
        assertThat(compose).doesNotContain("schema-init");
    }

    @Test
    void composePassesTaskReminderSettingsToTheBackendContainer() throws Exception {
        String compose = Files.readString(Path.of("docker-compose.example.yml"));

        assertThat(compose).contains(
                "PMS_NOTIFICATION_TASK_REMINDER_ENABLED: ${PMS_NOTIFICATION_TASK_REMINDER_ENABLED:-false}",
                "PMS_NOTIFICATION_TASK_REMINDER_CRON: \"${PMS_NOTIFICATION_TASK_REMINDER_CRON:-0 0 9 * * *}\"",
                "PMS_NOTIFICATION_TASK_REMINDER_ZONE: ${PMS_NOTIFICATION_TASK_REMINDER_ZONE:-Asia/Shanghai}",
                "PMS_NOTIFICATION_TASK_REMINDER_DUE_SOON_DAYS: ${PMS_NOTIFICATION_TASK_REMINDER_DUE_SOON_DAYS:-7}");
    }

    @Test
    void localMysqlExampleDeclaresSafeTaskReminderDefaults() throws Exception {
        String example = Files.readString(Path.of(".env.mysql.example"));

        assertThat(example).contains(
                "PMS_NOTIFICATION_TASK_REMINDER_ENABLED=false",
                "PMS_NOTIFICATION_TASK_REMINDER_CRON=\"0 0 9 * * *\"",
                "PMS_NOTIFICATION_TASK_REMINDER_ZONE=Asia/Shanghai",
                "PMS_NOTIFICATION_TASK_REMINDER_DUE_SOON_DAYS=7");
    }
}
