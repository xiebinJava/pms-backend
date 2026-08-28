package com.brad.pms.controller;

import com.brad.pms.security.IgnoreAuth;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal liveness/readiness probe for self-hosted deployments. */
@RestController
public class HealthController {
    private final DataSource dataSource;
    private final int expectedMigrationVersion;

    @Autowired
    public HealthController(DataSource dataSource) {
        this(dataSource, 6);
    }

    public HealthController(DataSource dataSource, int expectedMigrationVersion) {
        this.dataSource = dataSource;
        this.expectedMigrationVersion = expectedMigrationVersion;
    }

    @GetMapping("/health/live")
    @IgnoreAuth
    public ResponseEntity<Map<String, Object>> liveness() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @GetMapping({"/health", "/healthz", "/health/ready"})
    @IgnoreAuth
    public ResponseEntity<Map<String, Object>> readiness() {
        Readiness readiness = checkReadiness();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", readiness.ready ? "UP" : "DOWN");
        body.put("database", readiness.databaseUp ? "UP" : "DOWN");
        body.put("migration", readiness.migrationVersion == null ? "MISSING" : readiness.migrationVersion);
        return ResponseEntity.status(readiness.ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private Readiness checkReadiness() {
        try (Connection connection = dataSource.getConnection()) {
            boolean databaseUp;
            try (PreparedStatement statement = connection.prepareStatement("SELECT 1");
                 ResultSet resultSet = statement.executeQuery()) {
                databaseUp = resultSet.next() && resultSet.getInt(1) == 1;
            }
            if (!databaseUp) return new Readiness(false, false, null);
            String migrationVersion = latestMigrationVersion(connection);
            boolean migrationReady = String.valueOf(expectedMigrationVersion).equals(migrationVersion);
            return new Readiness(true, migrationReady, migrationVersion);
        } catch (Exception ignored) {
            return new Readiness(false, false, null);
        }
    }

    private String latestMigrationVersion(Connection connection) throws Exception {
        try {
            return queryLatest(connection, "pms_schema_migration_history");
        } catch (Exception ignored) {
            return queryLatest(connection, "flyway_schema_history");
        }
    }

    private String queryLatest(Connection connection, String table) throws Exception {
        String sql = "SELECT version FROM " + table
                + " ORDER BY CAST(version AS UNSIGNED) DESC, applied_at DESC LIMIT 1";
        if ("flyway_schema_history".equals(table)) {
            sql = "SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1";
        }
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    private static final class Readiness {
        private final boolean databaseUp;
        private final boolean ready;
        private final String migrationVersion;

        private Readiness(boolean databaseUp, boolean ready, String migrationVersion) {
            this.databaseUp = databaseUp;
            this.ready = ready;
            this.migrationVersion = migrationVersion;
        }
    }
}
