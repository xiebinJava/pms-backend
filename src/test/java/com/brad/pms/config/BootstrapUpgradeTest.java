package com.brad.pms.config;

import com.brad.pms.dto.request.LoginRequest;
import com.brad.pms.service.AuthService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test", "spring.datasource.password=test"})
@ActiveProfiles("mysql")
class BootstrapUpgradeTest {
    private static final String URL = "jdbc:tc:mysql:8.4:///upgrade74?TC_DAEMON=true&allowPublicKeyRetrieval=true&useSSL=false";
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);

    @DynamicPropertySource static void existingInstallation(DynamicPropertyRegistry registry) throws SQLException {
        Flyway.configure().dataSource(URL,"test","test").target("73").load().migrate();
        try (var connection = DriverManager.getConnection(URL,"test","test");
             var insert = connection.prepareStatement("INSERT INTO sys_user(username,username_normalized,email,email_normalized,"
                     + "system_role,password) VALUES ('admin','admin','upgrade@example.com','upgrade@example.com',1,?)")) {
            insert.setString(1, HASH);
            insert.executeUpdate();
            try (var grant = connection.createStatement()) {
                grant.executeUpdate("INSERT INTO sys_role(code,name) VALUES ('UPGRADE_READER','升级前的只读角色')");
                grant.executeUpdate("INSERT INTO sys_user_role(id,user_id,role_id,start_at,status) "
                        + "SELECT 810123,u.id,r.id,'2025-01-02 00:00:00','ACTIVE' FROM sys_user u JOIN sys_role r "
                        + "ON r.code='UPGRADE_READER' WHERE u.username='admin'");
            }
        }
        registry.add("spring.datasource.url", () -> URL);
        registry.add("PMS_BOOTSTRAP_ADMIN_PASSWORD", () -> "");
    }

    @Autowired JdbcTemplate db;
    @Autowired AuthService auth;
    @Autowired DataInitializer initializer;

    @Test
    @org.springframework.transaction.annotation.Transactional
    void historicalDatabaseContainingOnlyDeletedAccountsSkipsBootstrap() {
        db.update("UPDATE sys_user SET deleted=true");
        initializer.run();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM sys_user WHERE deleted=false",Long.class)).isZero();
    }

    @Test void originalPasswordAndUnrestrictedLoginSurviveUpgradeWithoutBootstrapSecret() {
        assertThat(db.queryForObject("SELECT password FROM sys_user WHERE username='admin'",String.class)).isEqualTo(HASH);
        LoginRequest request = new LoginRequest();
        request.setEmail("upgrade@example.com");
        request.setPassword(PASSWORD);
        var login = auth.login(request);
        assertThat(login.getUser().isMustChangePassword()).isFalse();
        assertThat(login.getUser().getSystemRole()).isOne();
        initializer.run();
        assertThat(db.queryForObject("SELECT r.code FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id "
                + "WHERE ur.id=810123 AND ur.status='ACTIVE' AND ur.start_at='2025-01-02 00:00:00'",
                String.class)).isEqualTo("UPGRADE_READER");
        assertThat(auth.login(request).getUser().isMustChangePassword()).isFalse();
    }
}
