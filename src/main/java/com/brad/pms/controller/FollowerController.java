package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.service.FollowerService;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects/{projectId}/followers")
@RequiredArgsConstructor
public class FollowerController {

    private final FollowerService followerService;

    @GetMapping
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<?> list(@PathVariable Long projectId) {
        return ResponseResult.success(followerService.list(projectId));
    }
}
