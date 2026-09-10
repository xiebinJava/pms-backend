package com.brad.pms.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OceanbaseAccountPrivilegeTest {

    @Test
    void grantsDropToTheMigrationAccountForDestructiveSchemaMigrations() throws Exception {
        String script = Files.readString(
                Path.of("docker/oceanbase-accounts-init.sh"),
                StandardCharsets.UTF_8);

        assertThat(script)
                .contains("GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES, CREATE VIEW, TRIGGER ON");
    }
}
