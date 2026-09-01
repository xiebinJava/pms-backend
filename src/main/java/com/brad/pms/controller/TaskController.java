package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.TaskCreateCmd;
import com.brad.pms.dto.request.TaskMoveCmd;
import com.brad.pms.dto.request.TaskUpdateCmd;
import com.brad.pms.dto.response.ProjectTaskDTO;
import com.brad.pms.dto.response.TaskAttachmentDTO;
import com.brad.pms.dto.response.TaskDetailDTO;
import com.brad.pms.service.TaskAttachmentService;
import com.brad.pms.service.TaskService;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final TaskAttachmentService attachmentService;

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

    @GetMapping("/tasks/{id}")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<TaskDetailDTO> detail(@PathVariable Long id) {
        return ResponseResult.success(taskService.getDetail(id));
    }

    @PostMapping("/tasks/{id}/attachments")
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<TaskAttachmentDTO> uploadAttachment(@PathVariable Long id,
                                                              @RequestParam("file") MultipartFile file) {
        return ResponseResult.success(attachmentService.upload(id, file));
    }

    @GetMapping("/tasks/{id}/attachments/{attachmentId}")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        Resource resource = attachmentService.loadFile(id, attachmentId);
        MediaType mediaType = MediaType.parseMediaType(attachmentService.contentType(id, attachmentId));
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(attachmentService.downloadName(id, attachmentId), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    @DeleteMapping("/tasks/{id}/attachments/{attachmentId}")
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<Void> deleteAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        attachmentService.delete(id, attachmentId);
        return ResponseResult.success();
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
