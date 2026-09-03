package com.brad.pms.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EnterpriseSchemaMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void enterpriseSchemaContainsIdentityOrganizationAndRbacTables() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableExists(connection, "sys_company_profile")).isTrue();
            assertThat(tableExists(connection, "sys_org_unit")).isTrue();
            assertThat(tableExists(connection, "sys_user_position")).isTrue();
            assertThat(tableExists(connection, "sys_role")).isTrue();
            assertThat(tableExists(connection, "sys_auth_session")).isTrue();
            assertThat(tableExists(connection, "sys_org_unit_history")).isTrue();
            assertThat(tableExists(connection, "feedback_ticket")).isTrue();
            assertThat(tableExists(connection, "feedback_history")).isTrue();
            assertThat(columnExists(connection, "sys_user", "username_normalized")).isTrue();
            assertThat(columnExists(connection, "sys_user", "email_normalized")).isTrue();
            assertThat(columnExists(connection, "sys_user", "name_zh")).isTrue();
            assertThat(columnExists(connection, "project", "org_unit_id")).isTrue();
            assertThat(columnExists(connection, "project_node", "start_date")).isTrue();
            assertThat(columnExists(connection, "project_node", "end_date")).isTrue();
            assertThat(columnExists(connection, "sys_org_unit_history", "before_json")).isTrue();
            assertThat(columnExists(connection, "sys_org_unit_history", "after_json")).isTrue();
            assertThat(columnExists(connection, "feedback_ticket", "client_request_id")).isTrue();
            assertThat(columnExists(connection, "feedback_ticket", "resolution_note")).isTrue();
            assertThat(columnExists(connection, "feedback_history", "from_status")).isTrue();
            assertThat(columnExists(connection, "feedback_history", "to_status")).isTrue();
            assertThat(indexExists(connection, "feedback_ticket", "feedback_ticket_reporter_created_idx")).isTrue();
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        for (String candidate : new String[]{table, table.toUpperCase()}) {
            try (var result = connection.getMetaData().getTables(
                    connection.getCatalog(), connection.getSchema(), candidate, new String[]{"TABLE"})) {
                if (result.next()) return true;
            }
        }
        return false;
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        for (String tableCandidate : new String[]{table, table.toUpperCase()}) {
            for (String columnCandidate : new String[]{column, column.toUpperCase()}) {
                try (var result = connection.getMetaData().getColumns(
                        connection.getCatalog(), connection.getSchema(), tableCandidate, columnCandidate)) {
                    if (result.next()) return true;
                }
            }
        }
        return false;
    }

    private boolean indexExists(Connection connection, String table, String index) throws Exception {
        for (String tableCandidate : new String[]{table, table.toUpperCase()}) {
            try (var result = connection.getMetaData().getIndexInfo(
                    connection.getCatalog(), connection.getSchema(), tableCandidate, false, false)) {
                while (result.next()) {
                    String candidate = result.getString("INDEX_NAME");
                    if (candidate != null && candidate.equalsIgnoreCase(index)) return true;
                }
            }
        }
        return false;
    }
}
