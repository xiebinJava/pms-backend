package com.brad.pms.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.brad.pms.common.page.PageResult;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.response.AuditLogDTO;
import com.brad.pms.entity.OperationLogDO;
import com.brad.pms.service.AuditQueryService;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {
    private final AuditQueryService auditQueryService;

    @GetMapping
    @RequirePermission(PermissionCode.AUDIT_READ)
    public ResponseResult<PageResult<AuditLogDTO>> list(@RequestParam(required = false) String action,
                                                     @RequestParam(required = false) String resourceType,
                                                     @RequestParam(required = false) Long resourceId,
                                                     @RequestParam(required = false) Long operatorId,
                                                     @RequestParam(required = false) Long projectId,
                                                     @RequestParam(required = false) String result,
                                                     @RequestParam(required = false) String requestId,
                                                     @RequestParam(required = false) String from,
                                                     @RequestParam(required = false) String to,
                                                     @RequestParam(defaultValue = "1") long currPage,
                                                     @RequestParam(defaultValue = "20") long pageSize) {
        LocalDateTime fromAt = parseDateTime(from);
        LocalDateTime toAt = parseDateTime(to);
        return ResponseResult.success(auditQueryService.page(new AuditQueryService.Query(
                action, resourceType, resourceId, operatorId, projectId, result, requestId,
                fromAt, toAt, currPage, pageSize)));
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionCode.AUDIT_READ)
    public ResponseResult<AuditLogDTO> detail(@PathVariable Long id) {
        return ResponseResult.success(auditQueryService.get(id));
    }

    static QueryWrapper<OperationLogDO> buildQuery(String action, String resourceType,
                                                          Long resourceId, Long operatorId,
                                                          LocalDateTime from, LocalDateTime to) {
        return buildQuery(action, resourceType, resourceId, operatorId, from, to, null, null, null);
    }

    static QueryWrapper<OperationLogDO> buildQuery(String action, String resourceType,
                                                          Long resourceId, Long operatorId,
                                                          LocalDateTime from, LocalDateTime to,
                                                          Long projectId, String result, String requestId) {
        return new QueryWrapper<OperationLogDO>()
                .eq(action != null && !action.isBlank(), "action", action == null ? null : action.trim())
                .eq(resourceType != null && !resourceType.isBlank(), "resource_type", resourceType == null ? null : resourceType.trim())
                .eq(resourceId != null, "resource_id", resourceId)
                .eq(operatorId != null, "operator_id", operatorId)
                .eq(projectId != null, "project_id", projectId)
                .eq(result != null && !result.isBlank(), "result", result == null ? null : result.trim())
                .eq(requestId != null && !requestId.isBlank(), "request_id", requestId == null ? null : requestId.trim())
                .ge(from != null, "created_at", from)
                .lt(to != null, "created_at", to)
                .orderByDesc("created_at").orderByDesc("id");
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            throw BusinessException.error("时间格式不正确，请使用 ISO-8601 格式");
        }
    }
}
