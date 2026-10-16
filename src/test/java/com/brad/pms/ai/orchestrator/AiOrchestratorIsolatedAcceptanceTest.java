package com.brad.pms.ai.orchestrator;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Java-side safety gate for the real acceptance runner.
 *
 * <p>The authenticated HTTP write is intentionally executed by the Connector
 * E2E runner, which owns BrowserAuth and disposable database cleanup. This
 * test prevents a backend test invocation from silently targeting shared data
 * and verifies that the checked-in contract still names the required stages.</p>
 */
class AiOrchestratorIsolatedAcceptanceTest {

    @Test
    void onlyAllowsAnExplicitDisposableAcceptanceTarget() {
        Assumptions.assumeTrue("true".equals(System.getenv("PMS_E2E_WRITE"))
                        && "true".equals(System.getenv("PMS_E2E_ISOLATED")),
                "SKIPPED: PMS_E2E_WRITE/PMS_E2E_ISOLATED are not both true");

        String baseUrl = required("PMS_E2E_BASE_URL");
        String allowedBaseUrl = required("PMS_E2E_ALLOWED_BASE_URL");
        String database = required("PMS_E2E_DATABASE_NAME");
        String allowedDatabase = required("PMS_E2E_ALLOWED_DATABASE_NAME");
        assertThat(normalizeUrl(baseUrl)).isEqualTo(normalizeUrl(allowedBaseUrl));
        assertThat(database).isEqualTo(allowedDatabase);
        assertThat(database).startsWith("pms_ai_e2e_");
        assertThat(required("PMS_E2E_RUN_ID")).matches("[A-Za-z0-9][A-Za-z0-9_-]{2,63}");
    }

    @Test
    void checkedInContractKeepsThePreviewFirstVerificationStages() throws Exception {
        String spec = Files.readString(Path.of("docs/agent-contracts/orchestrator/ai-orchestrator-v1.md"));
        assertThat(spec).contains(
                "读取真实上下文",
                "生成执行计划",
                "预览变更",
                "等待用户确认",
                "回读并验证结果",
                "CONTEXT_READ",
                "VERIFICATION_FINISHED");
    }

    @Test
    void storyAcceptanceContractRequiresTheRealReadbackAndAuditStages() throws Exception {
        String spec = Files.readString(Path.of("docs/agent-contracts/pms/story-node-orchestrator-e2e.md"));
        assertThat(spec).contains(
                "focused context",
                "NEEDS_INPUT",
                "WAITING_CONFIRMATION",
                "回读验证",
                "PMS_E2E_DB_URL",
                "WRITE_RESULT_UNKNOWN",
                "story.delete");

        if ("true".equals(System.getenv("PMS_E2E_STORY_ORCHESTRATOR"))) {
            String databaseUrl = System.getenv("PMS_E2E_DB_URL");
            assertThat(databaseUrl).as("故事真实验收必须提供只读数据库连接").isNotBlank();
        }
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少隔离验收配置: " + name);
        return value.trim();
    }

    private String normalizeUrl(String raw) {
        URI uri = URI.create(raw.trim());
        if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
            throw new IllegalArgumentException("隔离验收只允许 HTTP(S) 目标");
        }
        String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
        return uri.getScheme() + "://" + uri.getAuthority() + path;
    }
}
