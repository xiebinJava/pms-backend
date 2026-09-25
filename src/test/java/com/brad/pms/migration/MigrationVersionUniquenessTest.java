package com.brad.pms.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

class MigrationVersionUniquenessTest {

    private static final Pattern VERSIONED_MIGRATION = Pattern.compile("^V([^_]+)__.*\\.sql$");

    @Test
    void migrationVersionsAreUnique() throws IOException {
        Map<String, List<Path>> migrationsByVersion = Files.list(
                        Path.of("src/main/resources/db/migration"))
                .filter(path -> VERSIONED_MIGRATION.matcher(path.getFileName().toString()).matches())
                .collect(groupingBy(path -> versionOf(path), toList()));

        migrationsByVersion.forEach((version, migrations) -> assertThat(migrations)
                .as("Flyway migration version %s", version)
                .hasSize(1));
    }

    private String versionOf(Path path) {
        Matcher matcher = VERSIONED_MIGRATION.matcher(path.getFileName().toString());
        assertThat(matcher.matches()).isTrue();
        return matcher.group(1);
    }
}
