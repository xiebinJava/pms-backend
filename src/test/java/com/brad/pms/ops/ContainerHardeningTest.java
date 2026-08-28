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
    }
}
