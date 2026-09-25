package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.DevelopmentStorySaveCmd;
import com.brad.pms.dto.response.DevelopmentTopicStoryDTO;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.service.DevelopmentStoryManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/development/stories")
@RequiredArgsConstructor
public class DevelopmentStoryManagementController {

    private final DevelopmentStoryManagementService service;

    @PostMapping
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<Long> create(@Valid @RequestBody DevelopmentStorySaveCmd cmd) {
        return ResponseResult.success(service.create(cmd));
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<Void> update(@PathVariable Long id, @Valid @RequestBody DevelopmentStorySaveCmd cmd) {
        service.update(id, cmd);
        return ResponseResult.success();
    }

    @GetMapping("/by-topic/{topicId}")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<List<DevelopmentTopicStoryDTO>> listByTopic(@PathVariable Long topicId) {
        return ResponseResult.success(service.listByTopic(topicId));
    }
}
