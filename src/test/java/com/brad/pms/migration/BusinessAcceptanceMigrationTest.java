package com.brad.pms.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessAcceptanceMigrationTest {

    @Test
    void createsAcceptanceBaselineItemsAndDefectReferences() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V24__business_acceptance_defect_closure.sql"),
                StandardCharsets.UTF_8);
        String followUpSql = Files.readString(
                Path.of("src/main/resources/db/migration/V25__acceptance_source_versions.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).containsIgnoringCase("CREATE TABLE project_node_acceptance_baseline");
        assertThat(sql).containsIgnoringCase("CREATE TABLE project_node_acceptance_item");
        assertThat(sql).containsIgnoringCase("CREATE TABLE project_node_acceptance_defect");
        assertThat(sql).containsIgnoringCase("requirement_id");
        assertThat(sql).containsIgnoringCase("uk_node_acceptance_defect_key");
        assertThat(followUpSql).containsIgnoringCase("requirement_baseline_version");
        assertThat(followUpSql).containsIgnoringCase("source_defect_id");
    }
}
