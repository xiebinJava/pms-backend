package com.brad.pms.controller;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    @Test
    void defaultReadinessExpectsLatestEnterpriseMigration() throws Exception {
        HealthController controller = new HealthController(mock(DataSource.class));
        Field field = HealthController.class.getDeclaredField("expectedMigrationVersion");
        field.setAccessible(true);

        assertThat(field.getInt(controller)).isEqualTo(12);
    }

    @Test
    void livenessDoesNotDependOnDatabase() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new IllegalStateException("database down"));

        HealthController controller = new HealthController(dataSource, 5);

        assertThat(controller.liveness().getStatusCodeValue()).isEqualTo(200);
        assertThat(controller.liveness().getBody()).containsEntry("status", "UP");
    }

    @Test
    void readinessFailsWhenDatabaseIsUnavailable() {
        DataSource dataSource = mock(DataSource.class);
        try {
            when(dataSource.getConnection()).thenThrow(new IllegalStateException("database down"));
        } catch (Exception ignored) {
            throw new AssertionError(ignored);
        }

        HealthController controller = new HealthController(dataSource, 5);

        assertThat(controller.readiness().getStatusCodeValue()).isEqualTo(503);
        assertThat(controller.readiness().getBody()).containsEntry("database", "DOWN");
    }

    @Test
    void readinessReportsMissingMigrationVersion() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement databaseProbe = mock(PreparedStatement.class);
        PreparedStatement migrationProbe = mock(PreparedStatement.class);
        ResultSet databaseResult = mock(ResultSet.class);
        ResultSet migrationResult = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT 1")).thenReturn(databaseProbe);
        when(databaseProbe.executeQuery()).thenReturn(databaseResult);
        when(databaseResult.next()).thenReturn(true);
        when(databaseResult.getInt(1)).thenReturn(1);
        when(connection.prepareStatement("SELECT version FROM pms_schema_migration_history ORDER BY CAST(version AS UNSIGNED) DESC, applied_at DESC LIMIT 1"))
                .thenReturn(migrationProbe);
        when(migrationProbe.executeQuery()).thenReturn(migrationResult);
        when(migrationResult.next()).thenReturn(false);

        HealthController controller = new HealthController(dataSource, 5);

        assertThat(controller.readiness().getStatusCodeValue()).isEqualTo(503);
        assertThat(controller.readiness().getBody()).containsEntry("database", "UP");
        assertThat(controller.readiness().getBody()).containsEntry("migration", "MISSING");
    }
}
