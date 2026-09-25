package com.brad.pms.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProjectMemberAssignmentMigrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void assignmentTablesHaveStableKeysAndLookupIndexes() {
        assertThat(indexColumns("pms_project_member_auto_managed", "uk_project_member_auto_managed"))
                .containsExactly("project_id", "user_id");
        assertThat(indexColumns("pms_project_member_assignment_ref", "uk_project_member_assignment_ref"))
                .containsExactly("project_id", "item_type", "item_id", "assignment_type", "assignment_id");
        assertThat(indexColumns("pms_project_member_assignment_ref", "idx_assignment_ref_project_user"))
                .containsExactly("project_id", "user_id");
        assertThat(indexColumns("pms_project_member_assignment_ref", "idx_assignment_ref_item"))
                .containsExactly("project_id", "item_type", "item_id");
    }

    private java.util.List<String> indexColumns(String table, String index) {
        return jdbcTemplate.query(
                "SELECT column_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ? "
                        + "ORDER BY seq_in_index",
                (rs, rowNum) -> rs.getString("column_name"), table, index);
    }
}
