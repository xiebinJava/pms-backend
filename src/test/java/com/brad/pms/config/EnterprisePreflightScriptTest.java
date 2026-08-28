package com.brad.pms.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EnterprisePreflightScriptTest {

    @Test
    void preflightScriptsAreReadOnlyAndCoverSecuritySchema() throws Exception {
        String preflight = Files.readString(Path.of("scripts/enterprise-preflight.sh"));
        String verify = Files.readString(Path.of("scripts/verify-enterprise-migration.sh"));

        for (String script : new String[]{preflight, verify}) {
            assertThat(script).contains("set -euo pipefail", "MYSQL_PWD");
            assertThat(script).doesNotContain("DROP DATABASE", "TRUNCATE");
            assertThat(script).contains("sys_login_log", "sys_auth_session", "uk_user_username_normalized",
                    "idx_login_log_user_created", "idx_login_log_retention");
        }
        assertThat(preflight).contains("parent_id IS NULL", "status='ACTIVE'");
        assertThat(verify).contains("exactly one active root organization is present");
        assertThat(verify).contains("deleted", "version", "uk_project_code", "uk_project_node_key", "project_org_unit_fk");
    }
}
