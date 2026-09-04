package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.common.page.PageResult;
import com.brad.pms.dto.response.UserNotificationDTO;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<List<UserNotificationDTO>> list(
            @RequestParam(defaultValue = "true") boolean unreadFirst,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseResult.success(notificationService.list(unreadFirst, limit));
    }

    @GetMapping("/page")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<PageResult<UserNotificationDTO>> page(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "1") long currPage,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ResponseResult.success(notificationService.page(type, unreadOnly, currPage, pageSize));
    }

    @GetMapping("/unread-count")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<Map<String, Integer>> unreadCount() {
        return ResponseResult.success(Map.of("unreadCount", notificationService.unreadCount()));
    }

    @PostMapping("/{id}/read")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return ResponseResult.success();
    }

    @PostMapping("/read-all")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<Void> markAllRead() {
        notificationService.markAllRead();
        return ResponseResult.success();
    }
}
