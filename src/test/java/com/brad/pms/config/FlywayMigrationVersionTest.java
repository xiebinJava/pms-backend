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
}
