package com.brad.pms.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationVersionTest {
    private static final Pattern VERSION = Pattern.compile("^V([^_]+)__.*\\.sql$");

    @Test
    void migrationVersionsAreUnique() throws IOException {
        Set<String> versions = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/db/migration"))) {
            files.filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .map(path -> path.getFileName().toString())
                    .forEach(filename -> {
                        Matcher matcher = VERSION.matcher(filename);
                        if (!matcher.matches()) return;
                        if (!versions.add(matcher.group(1))) duplicates.add(matcher.group(1));
                    });
        }

        assertThat(duplicates).as("duplicate Flyway migration versions").isEmpty();
    }

    @Test
    void readinessVersionMatchesHighestMigration() throws IOException {
        int latestMigration;
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/db/migration"))) {
            latestMigration = files
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .map(path -> VERSION.matcher(path.getFileName().toString()))
                    .filter(Matcher::matches)
                    .mapToInt(matcher -> Integer.parseInt(matcher.group(1)))
                    .max()
                    .orElseThrow();
        }

        String healthController = Files.readString(Path.of(
                "src/main/java/com/brad/pms/controller/HealthController.java"));
        Matcher expectedVersion = Pattern.compile("LATEST_MIGRATION_VERSION\\s*=\\s*(\\d+)")
                .matcher(healthController);

        assertThat(expectedVersion.find()).as("health probe migration version is declared").isTrue();
        assertThat(Integer.parseInt(expectedVersion.group(1)))
                .as("health probe must expect the highest Flyway migration")
                .isEqualTo(latestMigration);
    }

    @Test
    void topicSoftDeleteMigrationAddsRecoverableFlag() throws IOException {
        Path migration = Path.of("src/main/resources/db/migration/V53__development_topic_soft_delete.sql");
        assertThat(Files.exists(migration)).isTrue();
        assertThat(Files.readString(migration)).contains("project_node_development_topic", "deleted BOOLEAN",
                "NOT NULL DEFAULT FALSE");
    }

    @Test
    void optionalDevelopmentContextMigrationContractIsPresent() throws IOException {
        Path migration = Path.of("src/main/resources/db/migration/V54__optional_development_item_project_context.sql");
        assertThat(Files.exists(migration)).isTrue();
        String sql = Files.readString(migration);
        assertThat(sql).contains("MODIFY COLUMN project_id BIGINT NULL",
                "MODIFY COLUMN node_id BIGINT NULL",
                "MODIFY COLUMN source_node_id BIGINT NULL",
                "MODIFY COLUMN topic_id BIGINT NULL",
                "pms_project_member_auto_managed",
                "pms_project_member_assignment_ref",
                "TOPIC_OWNER",
                "STORY_OWNER",
                "WORKFLOW_NODE_OWNER",
                "TASK_ASSIGNEE");
    }
}
