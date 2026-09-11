package com.brad.pms.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;

/**
 * 为已经存在的环境补齐系统角色字段。新环境由 schema.sql 创建，旧环境在启动时安全升级。
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class SystemRoleSchemaMigration implements org.springframework.boot.CommandLineRunner {

    private final DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (!hasSystemRoleColumn(connection)) {
                try (var statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE sys_user ADD COLUMN system_role TINYINT NOT NULL DEFAULT 0");
                }
                log.info("已为 sys_user 补齐 system_role 字段");
            }
            try (var statement = connection.prepareStatement(
                    "UPDATE sys_user SET system_role = 1 WHERE username = ?")) {
                statement.setString(1, "admin");
                statement.executeUpdate();
            }
        }
    }

    private boolean hasSystemRoleColumn(Connection connection) throws Exception {
        for (String table : new String[]{"sys_user", "SYS_USER"}) {
            for (String column : new String[]{"system_role", "SYSTEM_ROLE"}) {
                try (ResultSet columns = connection.getMetaData().getColumns(
                        connection.getCatalog(), connection.getSchema(), table, column)) {
                    if (columns.next()) return true;
                }
            }
        }
        return false;
    }
}
