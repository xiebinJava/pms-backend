package com.brad.pms.migration;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OceanbaseSqlImporterTest {

    @Test
    void splitsSemicolonsInsideStringLiterals() {
        String script = "INSERT INTO project (name) VALUES ('范围;基线');\n"
                + "INSERT INTO project (name) VALUES ('it''s ready');";

        assertThat(OceanbaseSqlImporter.splitStatements(script)).containsExactly(
                "INSERT INTO project (name) VALUES ('范围;基线')",
                "INSERT INTO project (name) VALUES ('it''s ready')");
    }

    @Test
    void normalizesH2InsertIdentifiersForMySqlMode() {
        String statement = "INSERT INTO \"PUBLIC\".\"PROJECT\" VALUES (1, 'brad; pms')";

        assertThat(OceanbaseSqlImporter.normalizeH2Statement(statement))
                .isEqualTo("INSERT INTO project VALUES (1, 'brad; pms')");
    }

    @Test
    void normalizesH2UnicodeAndTypedDateLiterals() {
        String statement = "INSERT INTO \"PUBLIC\".\"PROJECT\" VALUES "
                + "(1, U&'\\5f00\\6e90', DATE '2026-08-26', TIMESTAMP '2026-08-26 12:30:00')";

        assertThat(OceanbaseSqlImporter.normalizeH2Statement(statement))
                .isEqualTo("INSERT INTO project VALUES (1, '开源', '2026-08-26', '2026-08-26 12:30:00')");
    }

    @Test
    void countsRowsAcrossMultiRowInsertStatements() throws Exception {
        Path snapshot = Files.createTempFile("pms-snapshot", ".sql");
        Files.writeString(snapshot, "INSERT INTO project VALUES (1, 'one'), (2, 'two');");

        assertThat(OceanbaseSqlImporter.expectedRowCounts(snapshot).get("project"))
                .isEqualTo(2L);
    }

    @Test
    void skipsH2SessionAndDdlStatementsWhenImportingData() {
        List<String> skipped = List.of(
                "SET DB_CLOSE_DELAY -1",
                "CREATE USER IF NOT EXISTS SA PASSWORD HASH 'secret'",
                "ALTER USER SA SET LOCAL TRUE",
                "GRANT ALL ON SCHEMA PUBLIC TO SA",
                "CREATE MEMORY TABLE \"PUBLIC\".\"PROJECT\"(\"ID\" BIGINT)"
        );

        assertThat(skipped.stream().map(OceanbaseSqlImporter::normalizeH2Statement))
                .containsOnlyNulls();
    }

    @Test
    void rejectsDestructiveStatementsFromASnapshot() {
        assertThatThrownBy(() -> OceanbaseSqlImporter.normalizeH2Statement("DROP TABLE project"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("destructive");
    }
}
