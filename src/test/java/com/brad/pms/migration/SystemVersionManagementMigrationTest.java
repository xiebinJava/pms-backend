package com.brad.pms.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SystemVersionManagementMigrationTest {
    @Test
    void createsIndependentSystemVersionAndHistoryTables() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V63__system_version_management.sql"), StandardCharsets.UTF_8)
                .toLowerCase();

        assertThat(sql).contains(
                "create table pms_system",
                "create table pms_system_version",
                "create table pms_system_version_history",
                "unique key uk_pms_system_code",
                "unique key uk_pms_system_version_no",
                "foreign key (system_id) references pms_system",
                "idx_pms_system_version_history_version",
                "draft", "planned", "developing", "testing", "released", "archived");
        assertThat(sql).doesNotContain("project_id", "topic_id", "story_id", "build_version", "release_version");
    }

    @Test
    void migratesLegacyStatusesAndTightensTheLifecycleConstraint() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V67__simplify_system_version_status.sql"), StandardCharsets.UTF_8)
                .toLowerCase();

        assertThat(sql).contains(
                "set status = 'planned'",
                "where status = 'draft'",
                "set status = 'developing'",
                "where status = 'testing'",
                "from_status = 'planned'",
                "to_status = 'planned'",
                "drop check chk_pms_system_version_status",
                "add constraint chk_pms_system_version_status",
                "status in ('planned', 'developing', 'released', 'archived')");
    }

    @Test
    void removesTheLegacySystemCodeAfterTheAutoIncrementIdIsAvailable() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V68__remove_system_code.sql"), StandardCharsets.UTF_8)
                .toLowerCase();

        assertThat(sql).contains(
                "alter table pms_system",
                "drop index uk_pms_system_code",
                "drop column code");
    }
}
