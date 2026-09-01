package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.dto.response.UserNotificationDTO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.entity.UserNotificationDO;
import com.brad.pms.mapper.UserNotificationMapper;
import com.brad.pms.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final String TASK_ASSIGNED = "TASK_ASSIGNED";
    public static final String TASK_COMMENTED = "TASK_COMMENTED";
    public static final String PROJECT_COMMENTED = "PROJECT_COMMENTED";

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;
    static final int LOOKBACK = 80;
    static final int SNIPPET = 120;

    private final UserNotificationMapper notificationMapper;
    private final ProjectService projectService;
    private final UserService userService;

    public void notifyTaskAssigned(Long projectId, Long taskId, String taskTitle, Long assigneeId) {
        emit(assigneeId, TASK_ASSIGNED, "任务已指派给你", truncate(taskTitle, SNIPPET),
                projectId, taskId, UserContext.userId());
    }

    public void notifyComment(Long projectId, Long taskId, String content, Long assigneeId, Long managerId) {
        Long actorId = UserContext.userId();
        String actorName = actorDisplayName(actorId);
        String snippet = truncate(content, SNIPPET);
        if (taskId != null) {
            emit(assigneeId, TASK_COMMENTED, actorName + " 评论了任务", snippet, projectId, taskId, actorId);
            if (!Objects.equals(assigneeId, managerId)) {
                emit(managerId, TASK_COMMENTED, actorName + " 评论了任务", snippet, projectId, taskId, actorId);
            }
            return;
        }
        emit(managerId, PROJECT_COMMENTED, actorName + " 评论了项目", snippet, projectId, null, actorId);
    }

    public void emit(Long userId, String type, String title, String content,
                     Long projectId, Long taskId, Long actorId) {
        if (userId == null || Objects.equals(userId, actorId)) return;
        UserNotificationDO row = new UserNotificationDO();
        row.setUserId(userId);
        row.setType(type);
        row.setTitle(title == null || title.isBlank() ? "通知" : title);
        row.setContent(content);
        row.setProjectId(projectId);
        row.setTaskId(taskId);
        row.setActorId(actorId);
        notificationMapper.insert(row);
    }

    public List<UserNotificationDTO> list(boolean unreadFirst, int limit) {
        Long userId = UserContext.userId();
        int size = clamp(limit, DEFAULT_LIMIT, MAX_LIMIT);
        List<UserNotificationDO> rows = notificationMapper.selectList(
                new LambdaQueryWrapper<UserNotificationDO>()
                        .eq(UserNotificationDO::getUserId, userId)
                        .orderByDesc(UserNotificationDO::getCreatedAt)
                        .last("LIMIT " + LOOKBACK));
        List<UserNotificationDO> visible = keepReadable(rows);
        if (unreadFirst) {
            visible.sort(Comparator
                    .comparing((UserNotificationDO row) -> row.getReadAt() != null)
                    .thenComparing(UserNotificationDO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        }
        return toDTOs(visible.stream().limit(size).collect(Collectors.toList()));
    }

    public int unreadCount() {
        Long userId = UserContext.userId();
        List<UserNotificationDO> rows = notificationMapper.selectList(
                new LambdaQueryWrapper<UserNotificationDO>()
                        .eq(UserNotificationDO::getUserId, userId)
                        .isNull(UserNotificationDO::getReadAt));
        return keepReadable(rows).size();
    }

    public void markRead(Long id) {
        UserNotificationDO row = notificationMapper.selectById(id);
        if (row == null || !Objects.equals(row.getUserId(), UserContext.userId())) {
            throw BusinessException.error("通知不存在");
        }
        if (row.getReadAt() != null) return;
        row.setReadAt(LocalDateTime.now());
        notificationMapper.updateById(row);
    }

    public void markAllRead() {
        notificationMapper.update(null, new LambdaUpdateWrapper<UserNotificationDO>()
                .eq(UserNotificationDO::getUserId, UserContext.userId())
                .isNull(UserNotificationDO::getReadAt)
                .set(UserNotificationDO::getReadAt, LocalDateTime.now()));
    }

    private List<UserNotificationDO> keepReadable(List<UserNotificationDO> rows) {
        Set<Long> projectIds = rows.stream()
                .map(UserNotificationDO::getProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> readable = projectService.listReadableByIds(projectIds).stream()
                .map(ProjectDTO::getId)
                .collect(Collectors.toSet());
        return rows.stream()
                .filter(row -> row.getProjectId() == null || readable.contains(row.getProjectId()))
                .collect(Collectors.toList());
    }

    private List<UserNotificationDTO> toDTOs(List<UserNotificationDO> rows) {
        if (rows.isEmpty()) return List.of();
        Map<Long, UserDO> actors = userService.listByIds(rows.stream()
                        .map(UserNotificationDO::getActorId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(UserDO::getId, Function.identity(), (left, right) -> left));
        return rows.stream().map(row -> {
            UserNotificationDTO dto = new UserNotificationDTO();
            dto.setId(row.getId());
            dto.setType(row.getType());
            dto.setTitle(row.getTitle());
            dto.setContent(row.getContent());
            dto.setProjectId(row.getProjectId());
            dto.setTaskId(row.getTaskId());
            dto.setActorId(row.getActorId());
            dto.setActorName(Convertors.userDisplayName(actors.get(row.getActorId())));
            dto.setReadAt(row.getReadAt());
            dto.setCreatedAt(row.getCreatedAt());
            return dto;
        }).collect(Collectors.toList());
    }

    private String actorDisplayName(Long actorId) {
        if (actorId == null) return "有人";
        return userService.listByIds(List.of(actorId)).stream()
                .findFirst()
                .map(Convertors::userDisplayName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("有人");
    }

    static String truncate(String value, int max) {
        if (value == null) return "";
        String text = value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }

    static int clamp(int limit, int fallback, int max) {
        if (limit < 1) return fallback;
        return Math.min(limit, max);
    }
}
