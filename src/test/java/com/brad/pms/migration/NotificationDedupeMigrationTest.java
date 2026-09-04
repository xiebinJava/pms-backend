package com.brad.pms.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDedupeMigrationTest {

    @Test
    void definesNullableDedupeColumnAndUniqueIndexWithoutDeleted() throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "db/migration/V15__task_notification_dedupe.sql")) {
            assertThat(stream).as("V15 notification migration").isNotNull();
            if (stream == null) return;
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).containsIgnoringCase("ADD COLUMN dedupe_key VARCHAR(128) NULL");
            assertThat(sql).containsIgnoringCase("uk_user_notification_dedupe");
            assertThat(sql).containsIgnoringCase("user_id, type, dedupe_key");
            assertThat(sql).doesNotContainIgnoringCase("user_id, type, dedupe_key, deleted");
        }
    }
}
