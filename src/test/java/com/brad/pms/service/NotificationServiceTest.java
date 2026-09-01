package com.brad.pms.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.dto.response.UserNotificationDTO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.entity.UserNotificationDO;
import com.brad.pms.mapper.UserNotificationMapper;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock UserNotificationMapper notificationMapper;
    @Mock ProjectService projectService;
    @Mock UserService userService;

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
    }

    @Test
    void listDropsNotificationsFromUnreadableProjectsAndKeepsUnreadFirst() {
        UserNotificationDO unread = row(1L, 9L, null);
        UserNotificationDO read = row(2L, 9L, LocalDateTime.of(2026, 9, 1, 8, 0));
        UserNotificationDO hidden = row(3L, 88L, null);
        unread.setCreatedAt(LocalDateTime.of(2026, 9, 1, 9, 0));
        read.setCreatedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        hidden.setCreatedAt(LocalDateTime.of(2026, 9, 1, 11, 0));
        when(notificationMapper.selectList(any())).thenReturn(List.of(hidden, read, unread));
        ProjectDTO visible = new ProjectDTO();
        visible.setId(9L);
        when(projectService.listReadableByIds(any())).thenReturn(List.of(visible));
        when(userService.listByIds(any())).thenReturn(List.of());

        List<UserNotificationDTO> result = notificationService.list(true, 20);

        assertThat(result).extracting(UserNotificationDTO::getId).containsExactly(1L, 2L);
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
