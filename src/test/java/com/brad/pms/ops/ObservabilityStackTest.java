package com.brad.pms.ops;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityStackTest {

    @Test
    void actuatorExposesPrometheusOnThePrivateManagementPort() throws Exception {
        String application = Files.readString(Path.of("src/main/resources/application.yml"));
        String pom = Files.readString(Path.of("pom.xml"));

        assertThat(application).contains("port: ${PMS_MANAGEMENT_PORT:8081}");
        assertThat(application).contains("address: ${PMS_MANAGEMENT_ADDRESS:127.0.0.1}");
        assertThat(application).contains("include: health,metrics,prometheus");
        assertThat(application).contains("enabled: ${PMS_MAIL_HEALTH_ENABLED:false}");
        assertThat(pom).contains("micrometer-registry-prometheus");
    }

    @Test
    void observabilityOverlayBindsLoopbackAndScrapesBackendManagement() throws Exception {
        String compose = Files.readString(Path.of("docker-compose.observability.yml"));
        String prometheus = Files.readString(Path.of("deploy/observability/prometheus.yml"));
        String datasource = Files.readString(
                Path.of("deploy/observability/grafana/provisioning/datasources/datasource.yml"));
        String dashboard = Files.readString(
                Path.of("deploy/observability/grafana/dashboards/pms-backend.json"));

        assertThat(compose).contains("PMS_MANAGEMENT_ADDRESS: \"0.0.0.0\"");
        assertThat(compose).contains("127.0.0.1:8081:8081");
        assertThat(compose).contains("127.0.0.1:9090:9090");
        assertThat(compose).contains("127.0.0.1:3000:3000");
        assertThat(compose).contains("GRAFANA_ADMIN_PASSWORD:?set GRAFANA_ADMIN_PASSWORD");
        assertThat(compose).contains("networks: [pms-data]");
        assertThat(compose).doesNotContain("5173:3000");
        assertThat(prometheus).contains("metrics_path: /actuator/prometheus");
        assertThat(prometheus).contains("backend:8081");
        assertThat(datasource).contains("url: http://prometheus:9090");
        assertThat(dashboard).contains("pms_http_requests_seconds_count");
        assertThat(dashboard).contains("up{job=\\\"pms-backend\\\"}");
    }

    @Test
    void observabilityComposeConfigIsValidWhenDockerIsAvailable() throws Exception {
        Process version = new ProcessBuilder("docker", "compose", "version")
                .redirectErrorStream(true)
                .start();
        boolean finished = version.waitFor(8, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished && version.exitValue() == 0, "docker compose is not available");

        ProcessBuilder builder = new ProcessBuilder(
                "docker", "compose",
                "-f", "docker-compose.example.yml",
                "-f", "docker-compose.observability.yml",
                "--env-file", ".env.mysql.example",
                "config", "--quiet")
                .directory(Path.of(".").toFile())
                .redirectErrorStream(true);
        for (String line : Files.readAllLines(Path.of(".env.mysql.example"))) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                continue;
            }
            int equals = trimmed.indexOf('=');
            builder.environment().put(trimmed.substring(0, equals), trimmed.substring(equals + 1));
        }
        Process config = builder.start();
        boolean configFinished = config.waitFor(30, TimeUnit.SECONDS);
        String output = new String(config.getInputStream().readAllBytes());
        assertThat(configFinished).isTrue();
        assertThat(config.exitValue())
                .as("docker compose config failed: %s", output)
                .isZero();
    }
}
