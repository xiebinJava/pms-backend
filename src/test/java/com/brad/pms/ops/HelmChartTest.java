package com.brad.pms.ops;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class HelmChartTest {

    private static final Path CHART = Path.of("deploy/helm/pms");

    @Test
    void chartDescribesASingleTenantAppWithoutADatabase() throws Exception {
        String chart = Files.readString(CHART.resolve("Chart.yaml"));
        String values = Files.readString(CHART.resolve("values.yaml"));
        String readme = Files.readString(CHART.resolve("README.md"));

        assertThat(chart).contains("name: pms");
        assertThat(chart).contains("version: 1.0.0");
        assertThat(values).contains("mysqlHost: mysql.example.com");
        assertThat(values).contains("corsAllowedOrigins: https://pms.example.com");
        assertThat(values).contains("bootstrapAdminEmail: alex.zhang@example.com");
        assertThat(values).contains("bootstrapAdminNameZh: 张伟");
        assertThat(values).contains("jwtSecret: replace_with_a_random_secret_at_least_32_bytes");
        assertThat(values).contains("serviceMonitor:\n  enabled: false");
        assertThat(values).contains("ingress:\n  enabled: false");
        assertThat(readme).contains("does not install MySQL");
        assertThat(readme).doesNotContain("tenant_id");
    }

    @Test
    void templatesKeepActuatorOffIngressAndAliasTheFrontendProxy() throws Exception {
        String backend = Files.readString(CHART.resolve("templates/backend-deployment.yaml"));
        String services = Files.readString(CHART.resolve("templates/backend-service.yaml"));
        String ingress = Files.readString(CHART.resolve("templates/ingress.yaml"));
        String monitor = Files.readString(CHART.resolve("templates/servicemonitor.yaml"));
        String notes = Files.readString(CHART.resolve("templates/NOTES.txt"));

        assertThat(backend).contains("PMS_MANAGEMENT_ADDRESS");
        assertThat(backend).contains("0.0.0.0");
        assertThat(backend).contains("secretKeyRef");
        assertThat(backend).contains("runAsUser: 10001");
        assertThat(services).contains("name: {{ .Values.frontend.apiProxyHost }}");
        assertThat(services).contains("backend-management");
        assertThat(ingress).contains("if .Values.ingress.enabled");
        assertThat(ingress).contains("-frontend");
        assertThat(ingress).doesNotContain("management");
        assertThat(monitor).contains("if .Values.serviceMonitor.enabled");
        assertThat(monitor).contains("path: /actuator/prometheus");
        assertThat(notes).contains("Do not put that Service on an Ingress");
    }

    @Test
    void chartTreeStaysOnExampleHostsAndPlaceholderSecrets() throws Exception {
        try (Stream<Path> files = Files.walk(CHART)) {
            files.filter(Files::isRegularFile).forEach(path -> {
                try {
                    String text = Files.readString(path);
                    assertThat(text)
                            .as(path.toString())
                            .doesNotContain("admin123")
                            .doesNotContain("zhangsan")
                            .doesNotContain("localhost:2881");
                    if (path.getFileName().toString().equals("values.yaml")) {
                        assertThat(text).contains("example.com");
                        assertThat(text).doesNotContain("PMS_JWT_SECRET=");
                    }
                } catch (Exception exception) {
                    throw new IllegalStateException(path.toString(), exception);
                }
            });
        }
    }

    @Test
    void helmTemplateRendersWhenHelmIsAvailable() throws Exception {
        Process version;
        try {
            version = new ProcessBuilder("helm", "version", "--short")
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException missing) {
            Assumptions.assumeTrue(false, "helm is not available");
            return;
        }
        boolean finished = version.waitFor(8, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished && version.exitValue() == 0, "helm is not available");

        Process template = new ProcessBuilder(
                "helm", "template", "pms", "deploy/helm/pms",
                "--set", "existingSecret=pms-secrets")
                .directory(Path.of(".").toFile())
                .redirectErrorStream(true)
                .start();
        boolean templateFinished = template.waitFor(20, TimeUnit.SECONDS);
        String output = new String(template.getInputStream().readAllBytes());
        assertThat(templateFinished).isTrue();
        assertThat(template.exitValue())
                .as("helm template failed: %s", output)
                .isZero();
        assertThat(output).contains("kind: Deployment");
        assertThat(output).contains("name: backend");
        assertThat(output).doesNotContain("kind: ServiceMonitor");
        assertThat(output).doesNotContain("kind: Ingress");
    }
}
