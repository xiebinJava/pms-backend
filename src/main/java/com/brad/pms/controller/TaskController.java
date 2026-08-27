package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.TaskCreateCmd;
import com.brad.pms.dto.request.TaskMoveCmd;
import com.brad.pms.dto.request.TaskUpdateCmd;
import com.brad.pms.dto.response.ProjectTaskDTO;
import com.brad.pms.service.TaskService;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping("/projects/{projectId}/tasks")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<List<ProjectTaskDTO>> list(@PathVariable Long projectId,
                                                     @RequestParam(required = false) Long nodeId) {
        return ResponseResult.success(taskService.listByProject(projectId, nodeId));
    }

    @PostMapping("/projects/{projectId}/tasks")
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<ProjectTaskDTO> create(@PathVariable Long projectId, @Validated @RequestBody TaskCreateCmd cmd) {
        cmd.setProjectId(projectId);
        return ResponseResult.success(taskService.create(cmd));
    }

    @PutMapping("/tasks/{id}")
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<ProjectTaskDTO> update(@PathVariable Long id, @Validated @RequestBody TaskUpdateCmd cmd) {
        return ResponseResult.success(taskService.update(id, cmd));
    }

    @PutMapping("/tasks/{id}/move")
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<ProjectTaskDTO> move(@PathVariable Long id, @Validated @RequestBody TaskMoveCmd cmd) {
        return ResponseResult.success(taskService.move(id, cmd));
    }

    @DeleteMapping("/tasks/{id}")
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseResult.success();
    }
}
