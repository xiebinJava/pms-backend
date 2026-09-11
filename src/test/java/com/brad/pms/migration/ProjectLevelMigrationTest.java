package com.brad.pms.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectLevelMigrationTest {

    @Test
    void addsAStableDefaultedProjectLevelColumn() throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "db/migration/V16__project_level.sql")) {
            assertThat(stream).as("V16 project level migration").isNotNull();
            if (stream == null) return;
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).containsIgnoringCase("ADD COLUMN project_level TINYINT NOT NULL DEFAULT 0");
            assertThat(sql).containsIgnoringCase("0常规(C) 1重要(B) 2关键(A) 3战略(S)");
        }
    }
}
