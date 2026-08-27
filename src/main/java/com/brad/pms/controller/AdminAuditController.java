package com.brad.pms.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.entity.OperationLogDO;
import com.brad.pms.mapper.OperationLogMapper;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {
    private final OperationLogMapper operationLogMapper;

    @GetMapping
    @RequirePermission(PermissionCode.AUDIT_READ)
    public ResponseResult<List<OperationLogDO>> list(@RequestParam(required = false) String action,
                                                     @RequestParam(required = false) String resourceType,
                                                     @RequestParam(required = false) Long resourceId,
                                                     @RequestParam(required = false) Long operatorId,
                                                     @RequestParam(required = false) String from,
                                                     @RequestParam(required = false) String to) {
        LocalDateTime fromAt = parseDateTime(from);
        LocalDateTime toAt = parseDateTime(to);
        return ResponseResult.success(operationLogMapper.selectList(buildQuery(action, resourceType, resourceId, operatorId, fromAt, toAt)));
    }

    static QueryWrapper<OperationLogDO> buildQuery(String action, String resourceType,
                                                          Long resourceId, Long operatorId,
                                                          LocalDateTime from, LocalDateTime to) {
        return new QueryWrapper<OperationLogDO>()
                .eq(action != null && !action.isBlank(), "action", action == null ? null : action.trim())
                .eq(resourceType != null && !resourceType.isBlank(), "resource_type", resourceType == null ? null : resourceType.trim())
                .eq(resourceId != null, "resource_id", resourceId)
                .eq(operatorId != null, "operator_id", operatorId)
                .ge(from != null, "created_at", from)
                .le(to != null, "created_at", to)
                .orderByDesc("created_at")
                .last("LIMIT 200");
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
