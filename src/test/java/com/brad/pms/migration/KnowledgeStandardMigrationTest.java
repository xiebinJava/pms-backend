package com.brad.pms.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeStandardMigrationTest {

    @Test
    void createsKnowledgeAssetsAndImprovementActionsWithOptimisticLockingBaseline() throws Exception {
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V40__add_knowledge_standard_workbench.sql"),
                StandardCharsets.UTF_8);

        assertThat(migration).containsIgnoringCase("CREATE TABLE project_node_knowledge_baseline");
        assertThat(migration).containsIgnoringCase("CREATE TABLE project_node_knowledge_asset");
        assertThat(migration).containsIgnoringCase("CREATE TABLE project_node_knowledge_action");
        assertThat(migration).containsIgnoringCase("version");
        assertThat(migration).containsIgnoringCase("UNIQUE KEY");
        assertThat(migration).contains("node_key = 'knowledge'");
    }
}
