package com.brad.pms.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OceanbaseUpgradeScriptTest {

    private static final List<String> SCRIPTS = List.of(
            "scripts/oceanbase-upgrade.sh",
            "scripts/backup-oceanbase.sh",
            "scripts/restore-oceanbase.sh",
            "scripts/verify-backup.sh"
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
    void upgradeWorkflowUsesChecksumAndNonDestructiveLeaseLock() throws Exception {
        String script = Files.readString(Path.of("scripts/oceanbase-upgrade.sh"));

        assertThat(script).contains("pms_schema_migration_history");
        assertThat(script).contains("pms_schema_migration_lock");
        assertThat(script).contains("sha256sum");
        assertThat(script).contains("locked_until");
        assertThat(script).contains("owner_token");
        assertThat(script).contains("pms-schema-upgrade");
        assertThat(script).contains("pms_schema_migration_step");
        assertThat(script).contains("PMS_ACCEPT_FLYWAY_BASELINE");
        assertThat(script).contains("No pending migrations");
        assertThat(script).doesNotContain("DROP DATABASE");
        assertThat(script).doesNotContain("TRUNCATE");
    }

    @Test
    void upgradeWorkflowAllowsOceanbaseContainerClientOverride() throws Exception {
        String script = Files.readString(Path.of("scripts/oceanbase-upgrade.sh"));

        assertThat(script).contains("PMS_MYSQL_TOOL_CLIENT");
        assertThat(script).contains("--entrypoint");
        assertThat(script).contains("\"$TOOL_CLIENT\"");
    }

    @Test
    void backupAndRestoreRequireIntegrityVerification() throws Exception {
        String backup = Files.readString(Path.of("scripts/backup-oceanbase.sh"));
        String restore = Files.readString(Path.of("scripts/restore-oceanbase.sh"));
        String verify = Files.readString(Path.of("scripts/verify-backup.sh"));

        assertThat(backup).contains("sha256");
        assertThat(backup).contains("single-transaction");
        assertThat(backup).contains("skip-add-drop-table");
        assertThat(backup).contains("skip-lock-tables");
        assertThat(restore).contains("--allow-empty-target");
        assertThat(restore).contains("verify-backup.sh");
        assertThat(restore).contains("unsupported backup extension");
        assertThat(restore).doesNotContain("DROP DATABASE");
        assertThat(restore).doesNotContain("TRUNCATE");
        assertThat(verify).contains("gzip -t");
        assertThat(verify).contains("sha256sum");
    }

    @Test
    void retentionIndexMigrationLeadsWithCreatedAt() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V6__audit_retention_indexes.sql"));
        assertThat(migration).contains("idx_login_log_retention ON sys_login_log (created_at, result)");
    }

    @Test
    void upgradeRefusesToRunWithoutMigrationCredentials() throws Exception {
        ProcessBuilder builder = new ProcessBuilder("bash", "scripts/oceanbase-upgrade.sh");
        removeDatabaseCredentials(builder.environment());

        Process process = builder.redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());

        assertThat(process.waitFor()).isEqualTo(2);
        assertThat(output).contains("OCEANBASE_PASSWORD or PMS_MIGRATOR_PASSWORD is required");
    }

    @Test
    void restoreRequiresExplicitEmptyTargetAndProtectsProductionDatabase() throws Exception {
        Process missingConfirmation = new ProcessBuilder("bash", "scripts/restore-oceanbase.sh")
                .redirectErrorStream(true)
                .start();
        assertThat(missingConfirmation.waitFor()).isEqualTo(2);

        ProcessBuilder production = new ProcessBuilder(
                "bash", "scripts/restore-oceanbase.sh", "--allow-empty-target", "/tmp/not-a-backup.sql.gz");
        production.environment().put("OCEANBASE_DATABASE", "brad_pms");
        production.environment().put("OCEANBASE_PASSWORD", "test-only-placeholder");
        Process productionProcess = production.redirectErrorStream(true).start();
        String output = new String(productionProcess.getInputStream().readAllBytes());

        assertThat(productionProcess.waitFor()).isEqualTo(2);
        assertThat(output).contains("refusing to restore into brad_pms");
    }

    private static void removeDatabaseCredentials(Map<String, String> environment) {
        environment.remove("OCEANBASE_PASSWORD");
        environment.remove("PMS_MIGRATOR_PASSWORD");
    }
}
