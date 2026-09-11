package com.brad.pms.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RequirementScopeCleanupMigrationTest {

    @Test
    void removesTheDuplicatedNodeObjectiveAndDeliverableColumns() throws IOException {
        String migration = read("db/migration/V20__remove_requirement_scope_objective_deliverable.sql");
        assertThat(migration).containsIgnoringCase("ALTER TABLE project_node_baseline DROP COLUMN objective");
        assertThat(migration).containsIgnoringCase("ALTER TABLE project_node_baseline DROP COLUMN deliverable");
    }

    private String read(String resource) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).as(resource).isNotNull();
            if (stream == null) return "";
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
