package com.brad.pms.ops;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MysqlOpsScriptTest {

    private static final List<String> SCRIPTS = List.of(
            "scripts/backup-mysql.sh",
            "scripts/restore-mysql.sh",
            "scripts/verify-backup.sh",
            "scripts/enterprise-preflight.sh",
            "scripts/verify-enterprise-migration.sh"
    );

    @Test
    void operationalScriptsExposeHelpWithoutDatabaseCredentials() throws Exception {
        for (String script : SCRIPTS) {
            Process process = new ProcessBuilder("bash", script, "--help")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            assertThat(process.waitFor()).as(script).isZero();
            assertThat(output).as(script).contains("Usage:");
        }
    }

    @Test
    void backupAndRestoreUseMysqlCoordinatesAndRefuseTheAppDatabase() throws Exception {
        String backup = Files.readString(Path.of("scripts/backup-mysql.sh"));
        String restore = Files.readString(Path.of("scripts/restore-mysql.sh"));

        assertThat(backup).contains("MYSQL_HOST");
        assertThat(backup).contains("MYSQL_PORT:-3306");
        assertThat(backup).contains("MYSQL_DB:-pms");
        assertThat(backup).contains("MYSQL_PASSWORD is required");
        assertThat(backup).doesNotContain("OCEANBASE_");
        assertThat(backup).doesNotContain("obclient");

        assertThat(restore).contains("refusing to restore into pms");
        assertThat(restore).contains("PMS_ALLOW_PRODUCTION_RESTORE");
        assertThat(restore).doesNotContain("brad_pms");
        assertThat(restore).doesNotContain("OCEANBASE_");
        assertThat(restore).doesNotContain("obclient");
    }

    @Test
    void restoreRefusesTheApplicationDatabaseWithoutAnExplicitOverride() throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                "bash", "scripts/restore-mysql.sh", "--allow-empty-target", "/tmp/missing.sql.gz")
                .redirectErrorStream(true);
        builder.environment().put("MYSQL_PASSWORD", "synthetic-only-password");
        builder.environment().put("MYSQL_DB", "pms");
        Process process = builder.start();
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor()).isEqualTo(2);
        assertThat(output).contains("refusing to restore into pms");
    }
}
