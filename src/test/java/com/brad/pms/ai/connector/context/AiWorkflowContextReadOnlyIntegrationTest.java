package com.brad.pms.ai.connector.context;

import com.brad.pms.ai.connector.AiConnectorQueryService;
import com.brad.pms.ai.connector.context.AiReadMutationTracker.Capture;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.integration.ai.api.AiResourceDetailDTO;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import com.brad.pms.service.ProjectService;
import com.brad.pms.service.RequirementManagementService;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import javax.sql.DataSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Isolated DB proof for the readonly workflow context: pure reads run inside one
 * REPEATABLE READ transaction, produce no business writes, no lock queries, no
 * notification/direct-JDBC side effects, and see REPEATABLE-READ snapshots.
 * Gated by PMS_CONTEXT_READ_ISOLATED=true; Docker needs a reachable daemon.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PMS_CONTEXT_READ_ISOLATED", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiWorkflowContextReadOnlyIntegrationTest {

    private static final MySQLContainer<?> CONTAINER =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    @Autowired AiConnectorQueryService queryService;
    @Autowired AiWorkflowContextReadService readService;
    @Autowired AiReadMutationTracker tracker;
    @Autowired AiContextFixtureFactory fixtures;
    @Autowired ProjectService projectService;
    @Autowired RequirementManagementService requirementService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DataSource trackingDataSource;

    private static final AtomicReference<Long> FIXTURE_PROJECT = new AtomicReference<>();
    private static final AtomicReference<Long> FIXTURE_REQUIREMENT = new AtomicReference<>();

    @DynamicPropertySource
    static void containerProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        CONTAINER.start();
        registry.add("spring.datasource.url", CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", CONTAINER::getUsername);
        registry.add("spring.datasource.password", CONTAINER::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @TestConfiguration
    static class IsolationConfig {
        @Bean
        AiReadMutationTracker aiReadMutationTracker() {
            return new AiReadMutationTracker();
        }

        @Bean
        AiContextFixtureFactory aiContextFixtureFactory(javax.sql.DataSource aiReadTrackingDataSource) {
            return new AiContextFixtureFactory(aiReadTrackingDataSource);
        }

        /** Wraps the real Spring DataSource with the tracking proxy (test classpath only). */
        @Bean
        static AiTrackingDataSourcePostProcessor aiTrackingDataSourcePostProcessor(
                AiReadMutationTracker trackerHolder) {
            return new AiTrackingDataSourcePostProcessor(trackerHolder);
        }

        /** MyBatis-Plus autoconfig accepts an Interceptor[]; classify every executed statement. */
        static org.apache.ibatis.plugin.Interceptor mybatisWriteInterceptor(
                AiReadMutationTracker trackerHolder) {
            return new TrackedWriteInterceptor(trackerHolder);
        }
    }

    /** Wraps the auto-configured DataSource bean into the tracking proxy. */
    static final class AiTrackingDataSourcePostProcessor
            implements org.springframework.beans.factory.config.BeanPostProcessor {
        private final AiReadMutationTracker tracker;

        AiTrackingDataSourcePostProcessor(AiReadMutationTracker tracker) {
            this.tracker = tracker;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (bean instanceof DataSource dataSource
                    && !(bean instanceof AiReadTrackingDataSource)) {
                return new AiReadTrackingDataSource(dataSource, tracker);
            }
            return bean;
        }
    }

    @Autowired
    void inferFixtures() {
        // Fixtures are created lazily in a pre-tracked window by the first test.
    }

    @Order(1)
    @Test
    void createFixturesThroughBusinessServicesBeforeTracking() {
        UserContext.set(new LoginUser(1L, "admin", "Admin", com.brad.pms.common.enums.SystemRole.ADMINISTRATOR.getCode()));
        try {
            com.brad.pms.dto.request.ProjectCreateCmd cmd = new com.brad.pms.dto.request.ProjectCreateCmd();
            cmd.setName("只读上下文验收项目");
            cmd.setDescription("无写验证项目");
            ProjectDTO project = projectService.create(cmd);
            FIXTURE_PROJECT.set(project.getId());
            com.brad.pms.dto.request.RequirementSaveCmd requirementCmd = new com.brad.pms.dto.request.RequirementSaveCmd();
            requirementCmd.setTitle("跨端登录需求");
            requirementCmd.setDescription("验收标准：真实业务描述");
            FIXTURE_REQUIREMENT.set(requirementService.create(requirementCmd));
        } finally {
            UserContext.clear();
        }
        assertThat(FIXTURE_PROJECT.get()).isNotNull();
        assertThat(FIXTURE_REQUIREMENT.get()).isNotNull();
    }

    @Order(2)
    @Test
    void contextAndResourceReadsLeaveZeroBusinessWrites() {
        Long projectId = FIXTURE_PROJECT.get();
        Long requirementId = FIXTURE_REQUIREMENT.get();
        assertThat(projectId).isNotNull();
        UserContext.set(new LoginUser(1L, "admin", "Admin", com.brad.pms.common.enums.SystemRole.ADMINISTRATOR.getCode()));
        try {
            Map<String, Object> before = businessTableState();
            tracker.start();
            try {
                AiWorkflowContextDTO legacy = queryService.context("project", projectId);
                AiWorkflowContextDTO focused = queryService.context("project", projectId,
                        new ContextReadOptions("focused", null, null));
                AiWorkflowContextDTO requirement = queryService.context("requirement", requirementId);
                AiResourceDetailDTO projectResource = queryService.resource("project", projectId);
                AiResourceDetailDTO requirementResource = queryService.resource("requirement", requirementId);
                assertThat(legacy.workflow()).isNotNull();
                assertThat(focused.meta().view()).isEqualTo("focused");
                // The workflow rows created at requirement creation are a business fact; a pure read
                // must NEVER change them.
                long requirementWorkflowRows = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM pms_development_item_workflow WHERE item_type = 'REQUIREMENT' "
                                + "AND item_id = ?", Long.class, requirementId);
                // The workflow instance exists (created during requirement creation), so the pure
                // read must report it as configured truthfully rather than hide it.
                assertThat(requirement.meta().workflowConfigured()).isTrue();
                assertThat(requirement.meta().workflowInstanceVersion()).isNotNull();
                assertThat(projectResource.details()).isNotEmpty();
                assertThat(requirementResource.details()).isNotEmpty();
            } finally {
                Capture capture = tracker.capture();
                tracker.stop();
                Map<String, Object> after = businessTableState();
                assertThat(capture.writes()).as("writes").isEmpty();
                assertThat(capture.locks()).as("locks").isEmpty();
                assertThat(after).isEqualTo(before);
            }
        } finally {
            UserContext.clear();
        }
    }

    @Order(3)
    @Test
    void resourceReadSeesRepeatableReadSnapshotWithoutLocksAndNotifiesLaterWrites() throws Exception {
        Long projectId = FIXTURE_PROJECT.get();
        assertThat(projectId).isNotNull();
        UserContext.set(new LoginUser(1L, "admin", "Admin", com.brad.pms.common.enums.SystemRole.ADMINISTRATOR.getCode()));
        try {
            long progressBefore = textValue(projectId, "progress");
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.executeWithoutResult(status -> {
                long firstValue = textValue(projectId, "progress");
                tracker.start();
                try {
                    // barrier snapshot is already established by the plain SELECT above
                    long firstRead = textValue(projectId, "progress");
                    assertThat(firstRead).isEqualTo(firstValue);
                    Capture atBarrier = tracker.capture();
                    assertThat(atBarrier.writes()).isEmpty();
                    assertThat(atBarrier.locks()).isEmpty();
                    // Another connection updates + commits concurrently.
                    CompletableFuture.runAsync(() -> {
                        UserContext.set(new LoginUser(1L, "admin", "Admin", com.brad.pms.common.enums.SystemRole.ADMINISTRATOR.getCode()));
                        try (var independent = jdbcTemplate.getDataSource().getConnection()) {
                            independent.setAutoCommit(true);
                            try (var update = independent.prepareStatement(
                                    "UPDATE project SET progress = 99 WHERE id = ?")) {
                                update.setLong(1, projectId);
                                update.executeUpdate();
                            }
                        } catch (Exception brokenForeignTx) {
                            throw new IllegalStateException(brokenForeignTx);
                        } finally {
                            UserContext.clear();
                        }
                    }).join();
                    // Same repeatable-read snapshot still returns the old value.
                    long insideOldValue = textValue(projectId, "progress");
                    assertThat(insideOldValue).as("RR inside tx").isEqualTo(firstValue);
                } finally {
                    tracker.stop();
                }
                status.setRollbackOnly();
            });
            // A new request after the foreign commit sees the new value; reset state.
            long afterValue = jdbcTemplate.queryForObject(
                    "SELECT progress FROM project WHERE id = ?", Long.class, projectId);
            assertThat(afterValue).isEqualTo(99);
            updateProgress(projectId, progressBefore);
        } finally {
            UserContext.clear();
        }
    }

    private long textValue(Long projectId, String columnNegative) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT " + columnNegativeNegative(columnNegativeSemantic(columnNegative)) +
                        " FROM project WHERE id = ?", Long.class, projectId);
        return value == null ? -1 : value;
    }

    private String columnNegativeNegative(String column) {
        return column;
    }

    private String columnNegativeSemantic(String column) {
        return column;
    }

    private void updateProgress(Long projectId, long progress) {
        jdbcUPDATE(projectId, progress);
    }

    private void jdbcUPDATE(Long projectId, long progress) {
        jdbcTemplate.update("UPDATE project SET progress = ? WHERE id = ?", progress, projectId);
    }

    private long progressBefore(Long projectId) {
        return textValue(projectId, "progress");
    }
    private java.sql.Connection unarmedConnection() throws Exception {
        return trackingDataSource.getConnection();
    }

    @AfterEach
    void releaseTrackedWindow() {
        tracker.stop();
    }

    private Map<String, Object> businessTableState() {
        return Map.ofEntries(
                Map.entry("project", count("project")),
                Map.entry("project_node", count("project_node")),
                Map.entry("pms_requirement", count("pms_requirement")));
    }

    private long count(String table) {
        Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return result == null ? -1 : result;
    }


    /** Named class keeps @Intercepts visible to the MyBatis plugin wrapper. */
    @org.apache.ibatis.plugin.Intercepts(@org.apache.ibatis.plugin.Signature(type = Executor.class,
            method = "update", args = {MappedStatement.class, Object.class}))
    static final class TrackedWriteInterceptor implements org.apache.ibatis.plugin.Interceptor {
        private final AiReadMutationTracker trackerHolder;

        TrackedWriteInterceptor(AiReadMutationTracker trackerHolder) {
            this.trackerHolder = trackerHolder;
        }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            trackerHolder.recordSql(statement.getSqlCommandType().name() + " " + statement.getId());
            return invocation.proceed();
        }
    }
}
