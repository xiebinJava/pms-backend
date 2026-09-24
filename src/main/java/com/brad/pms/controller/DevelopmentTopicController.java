package com.brad.pms.controller;

import com.brad.pms.common.page.PageResult;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.DevelopmentTopicProjectQry;
import com.brad.pms.dto.request.DevelopmentTopicUpdateCmd;
import com.brad.pms.dto.response.DevelopmentTopicProjectOptionDTO;
import com.brad.pms.dto.response.DevelopmentTopicStoryDTO;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.service.DevelopmentTopicManagementService;
import com.brad.pms.service.DevelopmentStoryManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/development/topics")
@RequiredArgsConstructor
public class DevelopmentTopicController {

    private final DevelopmentTopicManagementService service;
    private final DevelopmentStoryManagementService storyService;

    @GetMapping("/{topicId}/stories")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<List<DevelopmentTopicStoryDTO>> stories(@PathVariable Long topicId) {
        return ResponseResult.success(storyService.listByTopic(topicId));
    }

    @PostMapping("")
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<Long> create(@Valid @RequestBody DevelopmentTopicUpdateCmd cmd) {
        return ResponseResult.success(service.create(cmd));
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<Void> update(@PathVariable Long id, @Valid @RequestBody DevelopmentTopicUpdateCmd cmd) {
        service.update(id, cmd);
        return ResponseResult.success();
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseResult.success();
    }

    @PostMapping("/{id}/restore")
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<Void> restore(@PathVariable Long id) {
        service.restore(id);
        return ResponseResult.success();
    }

    @PostMapping("/projects/page")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<PageResult<DevelopmentTopicProjectOptionDTO>> projectOptions(
            @RequestBody(required = false) DevelopmentTopicProjectQry qry) {
        return ResponseResult.success(service.projectOptions(qry));
    }
}
