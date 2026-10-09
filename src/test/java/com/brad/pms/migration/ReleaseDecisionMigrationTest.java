package com.brad.pms.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseDecisionMigrationTest {

    @Test
    void createsReleaseDecisionAndHandoverBaseline() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V30__release_decision_operations_handoff.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).containsIgnoringCase("CREATE TABLE project_node_release_baseline");
        assertThat(sql).containsIgnoringCase("release_version");
        assertThat(sql).containsIgnoringCase("release_window_start");
        assertThat(sql).containsIgnoringCase("release_window_end");
        assertThat(sql).containsIgnoringCase("package_ready");
        assertThat(sql).containsIgnoringCase("config_confirmed");
        assertThat(sql).containsIgnoringCase("rollback_ready");
        assertThat(sql).containsIgnoringCase("monitoring_confirmed");
        assertThat(sql).containsIgnoringCase("on_call_confirmed");
        assertThat(sql).containsIgnoringCase("decision_result");
        assertThat(sql).containsIgnoringCase("handover_notes");
        assertThat(sql).containsIgnoringCase("observation_items");
        assertThat(sql).containsIgnoringCase("emergency_contact");
        assertThat(sql).containsIgnoringCase("UNIQUE KEY");
    }

    @Test
    void simplifiesReleaseBaselineToHandoverOwnerAndNotes() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V69__simplify_release_handover.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).containsIgnoringCase("ADD COLUMN handover_owner_id BIGINT");
        assertThat(sql).containsIgnoringCase("FOREIGN KEY (handover_owner_id) REFERENCES sys_user (id)");
        assertThat(sql).containsIgnoringCase("DROP COLUMN release_version");
        assertThat(sql).containsIgnoringCase("DROP COLUMN release_window_start");
        assertThat(sql).containsIgnoringCase("DROP COLUMN release_window_end");
        assertThat(sql).containsIgnoringCase("DROP COLUMN release_type");
        assertThat(sql).containsIgnoringCase("DROP COLUMN package_ready");
        assertThat(sql).containsIgnoringCase("DROP COLUMN config_confirmed");
        assertThat(sql).containsIgnoringCase("DROP COLUMN rollback_ready");
        assertThat(sql).containsIgnoringCase("DROP COLUMN monitoring_confirmed");
        assertThat(sql).containsIgnoringCase("DROP COLUMN on_call_confirmed");
        assertThat(sql).containsIgnoringCase("DROP COLUMN decision_result");
        assertThat(sql).containsIgnoringCase("DROP COLUMN decision_note");
        assertThat(sql).containsIgnoringCase("DROP COLUMN observation_items");
        assertThat(sql).containsIgnoringCase("DROP COLUMN emergency_contact");
        assertThat(sql).containsIgnoringCase("idx_node_release_baseline_handover_owner");
    }
}
