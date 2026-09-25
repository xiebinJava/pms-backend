package com.brad.pms.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.brad.pms.entity.UserNotificationDO;
import com.brad.pms.mapper.UserNotificationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class NotificationQueryIntegrationTest {

    private static final long RECIPIENT_ID = 990001L;
    private static final long OTHER_USER_ID = 990002L;
    private static final long ACTIVE_PROJECT_ID = 990101L;
    private static final long TERMINATED_PROJECT_ID = 990102L;
    private static final long DELETED_PROJECT_ID = 990103L;

    @Autowired UserNotificationMapper notificationMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM user_notification WHERE user_id IN (?, ?)", RECIPIENT_ID, OTHER_USER_ID);
        jdbcTemplate.update("DELETE FROM project WHERE id IN (?, ?, ?)",
                ACTIVE_PROJECT_ID, TERMINATED_PROJECT_ID, DELETED_PROJECT_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?)", RECIPIENT_ID, OTHER_USER_ID);
    }

    @Test
    void filtersDeletedProjectsBeforePageAndUnreadCount() {
        insertUser(RECIPIENT_ID, "notification-recipient");
        insertUser(OTHER_USER_ID, "notification-other");
        insertProject(ACTIVE_PROJECT_ID, "active", 1, false);
        insertProject(TERMINATED_PROJECT_ID, "terminated", 3, false);
        insertProject(DELETED_PROJECT_ID, "deleted", 4, true);
        insertNotification(RECIPIENT_ID, NotificationService.TASK_OVERDUE, ACTIVE_PROJECT_ID, null, 10);
        insertNotification(RECIPIENT_ID, NotificationService.TASK_DUE_SOON, TERMINATED_PROJECT_ID, null, 20);
        insertNotification(RECIPIENT_ID, NotificationService.TASK_OVERDUE, DELETED_PROJECT_ID, null, 30);
        insertNotification(RECIPIENT_ID, NotificationService.TASK_ASSIGNED, null, null, 40);
        insertNotification(OTHER_USER_ID, NotificationService.TASK_OVERDUE, ACTIVE_PROJECT_ID, null, 50);

        IPage<UserNotificationDO> page = notificationMapper.selectVisiblePage(
                new Page<>(1, 2), RECIPIENT_ID, null, false);

        assertThat(page.getTotal()).isEqualTo(3);
        assertThat(page.getRecords()).extracting(UserNotificationDO::getProjectId)
                .containsExactly(null, TERMINATED_PROJECT_ID);
        assertThat(notificationMapper.countVisibleUnread(RECIPIENT_ID)).isEqualTo(3);
    }

    private void insertUser(long id, String username) {
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password, status, deleted, version) VALUES (?, ?, 'test', 'ACTIVE', FALSE, 0)",
                id, username);
    }

    private void insertProject(long id, String name, int status, boolean deleted) {
        jdbcTemplate.update("INSERT INTO project (id, code, name, status, owner_id, created_by, deleted, version) VALUES (?, ?, ?, ?, ?, ?, ?, 0)",
                id, "N-" + id, name, status, RECIPIENT_ID, RECIPIENT_ID, deleted);
    }

    private void insertNotification(long userId, String type, Long projectId, LocalDateTimeHolder readAt,
                                    int sequence) {
        jdbcTemplate.update("INSERT INTO user_notification (user_id, type, title, content, project_id, read_at, created_at, deleted, version) VALUES (?, ?, ?, ?, ?, ?, TIMESTAMPADD(MINUTE, ?, CURRENT_TIMESTAMP), FALSE, 0)",
                userId, type, type, type, projectId, readAt == null ? null : readAt.value(), sequence);
    }

    private record LocalDateTimeHolder(java.time.LocalDateTime value) {
    }
}
