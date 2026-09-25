package com.brad.pms.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OptionalDevelopmentContextMigrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void migrationMakesDevelopmentContextsNullableAndCreatesAssignmentTables() throws Exception {
        Map<String, String> nullable = new HashMap<>();
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            for (String table : new String[]{
                    "project_node_development_topic",
                    "project_node_development_story",
                    "pms_development_item_workflow"}) {
                String projectColumn = table.equals("pms_development_item_workflow")
                        ? "project_id" : "project_id";
                nullable.put(table + "." + projectColumn, columnNullable(metadata, table, projectColumn));
            }
            nullable.put("project_node_development_topic.node_id",
                    columnNullable(metadata, "project_node_development_topic", "node_id"));
            nullable.put("project_node_development_story.node_id",
                    columnNullable(metadata, "project_node_development_story", "node_id"));
            nullable.put("pms_development_item_workflow.source_node_id",
                    columnNullable(metadata, "pms_development_item_workflow", "source_node_id"));
            nullable.put("project_node_development_story.topic_id",
                    columnNullable(metadata, "project_node_development_story", "topic_id"));
        }

        assertThat(nullable).allSatisfy((column, isNullable) ->
                assertThat(isNullable).as(column).isEqualTo("YES"));
        assertThat(tableExists("pms_project_member_auto_managed")).isTrue();
        assertThat(tableExists("pms_project_member_assignment_ref")).isTrue();
    }

    private String columnNullable(DatabaseMetaData metadata, String table, String column) throws Exception {
        try (ResultSet columns = metadata.getColumns(null, null, table, column)) {
            assertThat(columns.next()).as(table + "." + column + " exists").isTrue();
            return columns.getString("IS_NULLABLE");
        }
    }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class, table);
        return count != null && count == 1;
    }
}
