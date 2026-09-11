package com.brad.pms.config;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.entity.UserDO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EnterpriseIntegrityMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void schemaProvidesSoftDeleteAndOptimisticLockColumns() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            for (String table : new String[]{"sys_user", "project", "project_task", "project_milestone", "project_node", "sys_org_unit"}) {
                assertThat(columnExists(connection, table, "deleted")).as(table + ".deleted").isTrue();
                assertThat(columnExists(connection, table, "version")).as(table + ".version").isTrue();
            }
        }
    }

    @Test
    void schemaAddsCriticalForeignKeysAndUniqueIndexes() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(foreignKeyExists(connection, "project", "project_org_unit_fk")).isTrue();
            assertThat(foreignKeyExists(connection, "project_task", "task_project_fk")).isTrue();
            assertThat(foreignKeyExists(connection, "sys_user_position", "user_position_user_fk")).isTrue();
            assertThat(indexExists(connection, "project", "uk_project_code")).isTrue();
            assertThat(indexExists(connection, "project_node", "uk_project_node_key")).isTrue();
        }
    }

    @Test
    void mutableEntitiesExposeMybatisIntegrityAnnotations() throws Exception {
        assertThat(ProjectDO.class.getDeclaredField("version").isAnnotationPresent(Version.class)).isTrue();
        assertThat(ProjectDO.class.getDeclaredField("deleted").isAnnotationPresent(TableLogic.class)).isTrue();
        assertThat(ProjectTaskDO.class.getDeclaredField("version").isAnnotationPresent(Version.class)).isTrue();
        assertThat(ProjectTaskDO.class.getDeclaredField("deleted").isAnnotationPresent(TableLogic.class)).isTrue();
        assertThat(UserDO.class.getDeclaredField("deleted").isAnnotationPresent(TableLogic.class)).isTrue();
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        try (var result = connection.getMetaData().getColumns(connection.getCatalog(), connection.getSchema(), table, column)) {
            return result.next();
        }
    }

    private boolean indexExists(Connection connection, String table, String index) throws Exception {
        try (var result = connection.getMetaData().getIndexInfo(connection.getCatalog(), connection.getSchema(), table, false, false)) {
            while (result.next()) {
                if (index.equalsIgnoreCase(result.getString("INDEX_NAME"))) return true;
            }
            return false;
        }
    }

    private boolean foreignKeyExists(Connection connection, String table, String constraint) throws Exception {
        try (var result = connection.getMetaData().getImportedKeys(connection.getCatalog(), connection.getSchema(), table)) {
            while (result.next()) {
                if (constraint.equalsIgnoreCase(result.getString("FK_NAME"))) return true;
            }
            return false;
        }
    }
}
