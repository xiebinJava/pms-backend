package com.brad.pms.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.entity.OperationLogDO;
import com.brad.pms.mapper.OperationLogMapper;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {
    private final OperationLogMapper operationLogMapper;

    @GetMapping
    @RequirePermission(PermissionCode.AUDIT_READ)
    public ResponseResult<List<OperationLogDO>> list(@RequestParam(required = false) String action) {
        return ResponseResult.success(operationLogMapper.selectList(new LambdaQueryWrapper<OperationLogDO>()
                .eq(action != null && !action.isBlank(), OperationLogDO::getAction, action)
                .orderByDesc(OperationLogDO::getCreatedAt)
                .last("LIMIT 200")));
    }
}
