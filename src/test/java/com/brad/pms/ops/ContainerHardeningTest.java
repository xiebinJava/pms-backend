package com.brad.pms.ops;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerHardeningTest {

    @Test
    void backendImageRunsAsNonRootAndContainsHealthProbe() throws Exception {
        String dockerfile = Files.readString(Path.of("Dockerfile"));
        assertThat(dockerfile).contains("USER 10001");
        assertThat(dockerfile).contains("healthcheck-backend.sh");
        assertThat(Files.isExecutable(Path.of("docker/healthcheck-backend.sh"))).isTrue();
    }

    @Test
    void frontendImageUsesUnprivilegedNginxAndHealthProbe() throws Exception {
        String dockerfile = Files.readString(Path.of("../pms-front/Dockerfile"));
        assertThat(dockerfile).contains("nginx-unprivileged");
        assertThat(dockerfile).contains("USER nginx");
        assertThat(Files.isExecutable(Path.of("../pms-front/healthcheck-frontend.sh"))).isTrue();
    }

    @Test
    void composeDeclaresHealthChecksRestartAndRuntimeIsolation() throws Exception {
        String compose = Files.readString(Path.of("docker-compose.example.yml"));
        assertThat(compose).contains("condition: service_healthy");
        assertThat(compose).contains("restart: unless-stopped");
        assertThat(compose).contains("cap_drop:");
        assertThat(compose).contains("no-new-privileges:true");
        assertThat(compose).contains("read_only: true");
        assertThat(compose).contains("mem_limit:");
        assertThat(compose).contains("cpus:");
        assertThat(compose).contains("healthcheck:");
        assertThat(compose).contains("127.0.0.1:2881:2881");
        assertThat(compose).contains("127.0.0.1:8080:8080");
        assertThat(compose).contains("uploads-init:");
        assertThat(compose).contains("chown -R 10001:10001");
        assertThat(compose).contains("PMS_NOTIFICATION_STARTUP_CHECK:-false");
        assertThat(compose).contains("pms-data:", "pms-edge:", "networks: [pms-data, pms-edge]");
        assertThat(compose).contains("networks: [pms-edge]");
    }

    @Test
    void nginxAddsSecurityHeadersAndProxyLimits() throws Exception {
        String nginx = Files.readString(Path.of("../pms-front/nginx.conf"));
        assertThat(nginx).contains("X-Content-Type-Options");
        assertThat(nginx).contains("Referrer-Policy");
        assertThat(nginx).contains("Content-Security-Policy");
        assertThat(nginx).contains("Strict-Transport-Security");
        assertThat(nginx).contains("client_max_body_size");
        assertThat(nginx).contains("proxy_read_timeout");
        assertThat(nginx).contains("proxy_set_header X-Forwarded-For $remote_addr");
        assertThat(nginx).contains("proxy_set_header X-Forwarded-Proto $forwarded_proto");
        assertThat(nginx).contains("real_ip_header X-Forwarded-For");
        assertThat(nginx).contains("geo $realip_remote_addr $trusted_proxy");
        assertThat(nginx).contains("set_real_ip_from 127.0.0.1;");
        assertThat(nginx).doesNotContain("set_real_ip_from 172.30.0.0/24;");
        assertThat(nginx).contains("proxy_set_header Forwarded \"\"");
        assertThat(nginx).contains("proxy_set_header X-Forwarded-Port \"\"");
    }

    @Test
    void actuatorUsesPrivateManagementPort() throws Exception {
        String application = Files.readString(Path.of("src/main/resources/application.yml"));
        assertThat(application).contains("port: ${PMS_MANAGEMENT_PORT:8081}");
        assertThat(application).contains("address: ${PMS_MANAGEMENT_ADDRESS:127.0.0.1}");
    }
}
