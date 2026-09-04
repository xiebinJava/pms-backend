package com.brad.pms.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.brad.pms.common.page.PageResult;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.dto.response.UserNotificationDTO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.entity.UserNotificationDO;
import com.brad.pms.mapper.UserNotificationMapper;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import com.brad.pms.webhook.WebhookEvent;
import com.brad.pms.webhook.WebhookPublisher;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock UserNotificationMapper notificationMapper;
    @Mock UserService userService;
    @Mock FollowerService followerService;
    @Mock WebhookPublisher webhookPublisher;

    @InjectMocks NotificationService notificationService;

    @BeforeEach
    void init() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        TableInfoHelper.initTableInfo(assistant, UserNotificationDO.class);
        UserContext.set(new LoginUser(7L, "Alex.Zhang", "张伟", 0, "张伟", "张伟（Alex.Zhang）", 1L));
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void emitSkipsTheActorAndNullRecipients() {
        notificationService.emit(7L, NotificationService.TASK_ASSIGNED, "任务已指派给你", "接口评审", 9L, 1L, 7L);
        notificationService.emit(null, NotificationService.TASK_ASSIGNED, "任务已指派给你", "接口评审", 9L, 1L, 7L);
        verify(notificationMapper, never()).insert(any(UserNotificationDO.class));
    }

    @Test
    void notifyTaskAssignedWritesInboxForTheNewAssignee() {
        notificationService.notifyTaskAssigned(9L, 3L, "补齐接口文档", 8L);

        ArgumentCaptor<UserNotificationDO> captor = ArgumentCaptor.forClass(UserNotificationDO.class);
        verify(notificationMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(8L);
        assertThat(captor.getValue().getType()).isEqualTo(NotificationService.TASK_ASSIGNED);
        assertThat(captor.getValue().getTitle()).isEqualTo("任务已指派给你");
        assertThat(captor.getValue().getTaskId()).isEqualTo(3L);

        ArgumentCaptor<WebhookEvent> webhook = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookPublisher).publish(webhook.capture());
        assertThat(webhook.getValue().type()).isEqualTo(NotificationService.TASK_ASSIGNED);
        assertThat(webhook.getValue().recipientIds()).containsExactly(8L);
        assertThat(webhook.getValue().projectId()).isEqualTo(9L);
    }

    @Test
    void listUsesSqlFilteredRowsAndKeepsUnreadFirstOrderingInTheMapper() {
        UserNotificationDO unread = row(1L, 9L, null);
        UserNotificationDO read = row(2L, 9L, LocalDateTime.of(2026, 9, 1, 8, 0));
        unread.setCreatedAt(LocalDateTime.of(2026, 9, 1, 9, 0));
        read.setCreatedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        when(notificationMapper.selectVisibleList(7L, true, 20)).thenReturn(List.of(unread, read));
        when(userService.listByIds(any())).thenReturn(List.of());

        List<UserNotificationDTO> result = notificationService.list(true, 20);

        assertThat(result).extracting(UserNotificationDTO::getId).containsExactly(1L, 2L);
        verify(notificationMapper).selectVisibleList(7L, true, 20);
    }

    @Test
    void pageUsesSqlFilteredRowsAndPreservesPaginationMetadata() {
        UserNotificationDO row = row(4L, 9L, null);
        row.setType(NotificationService.TASK_OVERDUE);
        row.setCreatedAt(LocalDateTime.of(2026, 9, 4, 9, 0));
        Page<UserNotificationDO> page = new Page<>(2, 1);
        page.setTotal(2);
        page.setRecords(List.of(row));
        when(notificationMapper.selectVisiblePage(any(), any(), eq(NotificationService.TASK_OVERDUE), eq(true)))
                .thenReturn(page);
        when(userService.listByIds(any())).thenReturn(List.of());

        PageResult<UserNotificationDTO> result = notificationService.page(
                NotificationService.TASK_OVERDUE, true, 2, 1);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getCurrPage()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(1);
        assertThat(result.getList()).extracting(UserNotificationDTO::getType)
                .containsExactly(NotificationService.TASK_OVERDUE);
        verify(notificationMapper).selectVisiblePage(any(), eq(7L),
                eq(NotificationService.TASK_OVERDUE), eq(true));
    }

    @Test
    void pageRejectsUnknownNotificationType() {
        assertThatThrownBy(() -> notificationService.page("UNKNOWN", false, 1, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("通知类型");
        verify(notificationMapper, never()).selectVisiblePage(any(), any(), any(), any(Boolean.class));
    }

    @Test
    void reminderInsertIsInAppOnlyAndDuplicateIsNotAnError() {
        when(notificationMapper.insert(any(UserNotificationDO.class)))
                .thenThrow(new DuplicateKeyException("uk_user_notification_dedupe"));

        assertThat(notificationService.emitInApp(8L, NotificationService.TASK_OVERDUE,
                "任务已逾期", "研发平台 · 补齐文档", 9L, 3L, null, null,
                "TASK_OVERDUE:3:2026-09-04")).isFalse();

        verify(notificationMapper).insert(any(UserNotificationDO.class));
        verifyNoInteractions(webhookPublisher);
    }

    @Test
    void markReadRejectsAnotherUsersRow() {
        UserNotificationDO row = row(1L, 9L, null);
        row.setUserId(8L);
        when(notificationMapper.selectById(1L)).thenReturn(row);

        assertThatThrownBy(() -> notificationService.markRead(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("通知不存在");
    }

    @Test
    void notifyCommentDoesNotNotifyTheAuthorTwice() {
        UserDO actor = new UserDO();
        actor.setId(7L);
        actor.setUsername("Alex.Zhang");
        actor.setNameZh("张伟");
        when(userService.listByIds(any())).thenReturn(List.of(actor));

        notificationService.notifyComment(9L, 3L, "先对齐接口", 7L, 8L);

        ArgumentCaptor<UserNotificationDO> captor = ArgumentCaptor.forClass(UserNotificationDO.class);
        verify(notificationMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(8L);
        assertThat(captor.getValue().getType()).isEqualTo(NotificationService.TASK_COMMENTED);
        assertThat(captor.getValue().getTitle()).contains("张伟");
        ArgumentCaptor<WebhookEvent> webhook = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookPublisher).publish(webhook.capture());
        assertThat(webhook.getValue().type()).isEqualTo(NotificationService.TASK_COMMENTED);
        assertThat(webhook.getValue().recipientIds()).containsExactly(7L, 8L);
    }

    @Test
    void notifyCommentAlsoWritesInboxForProjectFollowers() {
        UserDO actor = new UserDO();
        actor.setId(7L);
        actor.setUsername("Alex.Zhang");
        actor.setNameZh("张伟");
        when(userService.listByIds(any())).thenReturn(List.of(actor));
        when(followerService.listUserIds(9L)).thenReturn(List.of(8L, 11L, 7L));

        notificationService.notifyComment(9L, null, "先对齐接口", null, 8L);

        ArgumentCaptor<UserNotificationDO> captor = ArgumentCaptor.forClass(UserNotificationDO.class);
        verify(notificationMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(UserNotificationDO::getUserId).containsExactly(8L, 11L);
        assertThat(captor.getAllValues()).extracting(UserNotificationDO::getType)
                .containsOnly(NotificationService.PROJECT_COMMENTED);
        ArgumentCaptor<WebhookEvent> webhook = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookPublisher).publish(webhook.capture());
        assertThat(webhook.getValue().recipientIds()).containsExactly(8L, 11L, 7L);
    }

    @Test
    void notifyNodeCompletedWritesInboxForManagerOwnerAndFollowers() {
        UserDO actor = new UserDO();
        actor.setId(7L);
        actor.setUsername("Alex.Zhang");
        actor.setNameZh("张伟");
        when(userService.listByIds(any())).thenReturn(List.of(actor));
        when(followerService.listUserIds(9L)).thenReturn(List.of(11L));

        notificationService.notifyNodeCompleted(9L, 4L, "需求澄清与范围基线", 8L, 8L, 12L);

        ArgumentCaptor<UserNotificationDO> captor = ArgumentCaptor.forClass(UserNotificationDO.class);
        verify(notificationMapper, times(3)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(UserNotificationDO::getUserId)
                .containsExactly(8L, 12L, 11L);
        assertThat(captor.getAllValues()).extracting(UserNotificationDO::getType)
                .containsOnly(NotificationService.NODE_COMPLETED);
        assertThat(captor.getAllValues()).extracting(UserNotificationDO::getNodeId).containsOnly(4L);
        ArgumentCaptor<WebhookEvent> webhook = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookPublisher).publish(webhook.capture());
        assertThat(webhook.getValue().type()).isEqualTo(NotificationService.NODE_COMPLETED);
        assertThat(webhook.getValue().recipientIds()).containsExactly(8L, 12L, 11L);
        assertThat(webhook.getValue().nodeId()).isEqualTo(4L);
    }

    @Test
    void notifyNodeRolledBackKeepsTheReasonSnippet() {
        UserDO actor = new UserDO();
        actor.setId(7L);
        actor.setUsername("Alex.Zhang");
        actor.setNameZh("张伟");
        when(userService.listByIds(any())).thenReturn(List.of(actor));
        when(followerService.listUserIds(9L)).thenReturn(List.of());

        notificationService.notifyNodeRolledBack(9L, 4L, "需求澄清与范围基线", 8L, 8L, "范围需要重评");

        ArgumentCaptor<UserNotificationDO> captor = ArgumentCaptor.forClass(UserNotificationDO.class);
        verify(notificationMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(8L);
        assertThat(captor.getValue().getType()).isEqualTo(NotificationService.NODE_ROLLED_BACK);
        assertThat(captor.getValue().getContent()).contains("范围需要重评");
        assertThat(captor.getValue().getNodeId()).isEqualTo(4L);

        ArgumentCaptor<WebhookEvent> webhook = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookPublisher).publish(webhook.capture());
        assertThat(webhook.getValue().type()).isEqualTo(NotificationService.NODE_ROLLED_BACK);
        assertThat(webhook.getValue().nodeId()).isEqualTo(4L);
    }

    private static UserNotificationDO row(Long id, Long projectId, LocalDateTime readAt) {
        UserNotificationDO row = new UserNotificationDO();
        row.setId(id);
        row.setUserId(7L);
        row.setProjectId(projectId);
        row.setReadAt(readAt);
        row.setType(NotificationService.TASK_ASSIGNED);
        row.setTitle("任务已指派给你");
        return row;
    }
}
