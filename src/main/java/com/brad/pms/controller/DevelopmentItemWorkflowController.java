package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.DevelopmentItemNodeUpdateCmd;
import com.brad.pms.dto.request.DevelopmentItemTaskSaveCmd;
import com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.service.DevelopmentItemPermissionService;
import com.brad.pms.service.DevelopmentItemWorkflowService;
import com.brad.pms.workflow.DevelopmentItemType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/development")
@RequiredArgsConstructor
public class DevelopmentItemWorkflowController {

    private final DevelopmentItemWorkflowService service;
    private final DevelopmentItemPermissionService permissionService;

    @GetMapping("/topics/{id}")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<DevelopmentItemWorkflowDetailDTO> topic(@PathVariable Long id) {
        return ResponseResult.success(service.detail(DevelopmentItemType.TOPIC, id));
    }

    @GetMapping("/stories/{id}")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<DevelopmentItemWorkflowDetailDTO> story(@PathVariable Long id) {
        return ResponseResult.success(service.detail(DevelopmentItemType.STORY, id));
    }

    @PutMapping("/items/{type}/{id}/nodes/{nodeId}")
    public ResponseResult<DevelopmentItemWorkflowDetailDTO> updateNode(
            @PathVariable String type, @PathVariable Long id, @PathVariable Long nodeId,
            @RequestBody DevelopmentItemNodeUpdateCmd cmd) {
        DevelopmentItemType itemType = DevelopmentItemType.from(type);
        permissionService.requireWrite(itemType);
        return ResponseResult.success(service.updateNode(itemType, id, nodeId, cmd));
    }

    @PostMapping("/items/{type}/{id}/nodes/{nodeId}/complete")
    public ResponseResult<DevelopmentItemWorkflowDetailDTO> completeNode(
            @PathVariable String type, @PathVariable Long id, @PathVariable Long nodeId) {
        DevelopmentItemType itemType = DevelopmentItemType.from(type);
        permissionService.requireWrite(itemType);
        return ResponseResult.success(service.completeNode(itemType, id, nodeId));
    }

    @PostMapping("/items/{type}/{id}/nodes/{nodeId}/tasks")
    public ResponseResult<DevelopmentItemWorkflowDetailDTO> createTask(
            @PathVariable String type, @PathVariable Long id, @PathVariable Long nodeId,
            @RequestBody DevelopmentItemTaskSaveCmd cmd) {
        DevelopmentItemType itemType = DevelopmentItemType.from(type);
        permissionService.requireWrite(itemType);
        return ResponseResult.success(service.saveTask(itemType, id, nodeId, null, cmd));
    }

    @PutMapping("/items/{type}/{id}/tasks/{taskId}")
    public ResponseResult<DevelopmentItemWorkflowDetailDTO> updateTask(
            @PathVariable String type, @PathVariable Long id, @PathVariable Long taskId,
            @RequestBody DevelopmentItemTaskSaveCmd cmd) {
        DevelopmentItemType itemType = DevelopmentItemType.from(type);
        permissionService.requireWrite(itemType);
        return ResponseResult.success(service.saveTask(itemType, id, null, taskId, cmd));
    }

    @DeleteMapping("/items/{type}/{id}/tasks/{taskId}")
    public ResponseResult<DevelopmentItemWorkflowDetailDTO> deleteTask(
            @PathVariable String type, @PathVariable Long id, @PathVariable Long taskId) {
        DevelopmentItemType itemType = DevelopmentItemType.from(type);
        permissionService.requireWrite(itemType);
        return ResponseResult.success(service.deleteTask(itemType, id, taskId));
    }
}
