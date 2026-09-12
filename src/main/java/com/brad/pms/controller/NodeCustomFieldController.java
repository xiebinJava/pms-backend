package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.WorkflowNodeFieldValuesCmd;
import com.brad.pms.dto.response.WorkflowFieldAttachmentDTO;
import com.brad.pms.dto.response.WorkflowNodeFieldValuesDTO;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.service.NodeCustomFieldService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/projects/{projectId}/nodes/{nodeId}/fields")
@RequiredArgsConstructor
public class NodeCustomFieldController {
    private final NodeCustomFieldService customFieldService;

    @GetMapping
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<WorkflowNodeFieldValuesDTO> get(@PathVariable Long projectId, @PathVariable Long nodeId) {
        return ResponseResult.success(customFieldService.get(projectId, nodeId));
    }

    @PutMapping
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<WorkflowNodeFieldValuesDTO> save(@PathVariable Long projectId, @PathVariable Long nodeId,
                                                             @Valid @RequestBody WorkflowNodeFieldValuesCmd cmd) {
        return ResponseResult.success(customFieldService.save(projectId, nodeId, cmd));
    }

    @PostMapping("/attachments")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<WorkflowFieldAttachmentDTO> upload(@PathVariable Long projectId, @PathVariable Long nodeId,
                                                               @RequestParam String fieldKey,
                                                               @RequestParam("file") MultipartFile file) {
        return ResponseResult.success(customFieldService.upload(projectId, nodeId, fieldKey, file));
    }

    @GetMapping("/{fieldKey}/attachments/{attachmentId}")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseEntity<Resource> read(@PathVariable Long projectId, @PathVariable Long nodeId,
                                         @PathVariable String fieldKey, @PathVariable Long attachmentId) {
        Resource resource = customFieldService.loadAttachment(projectId, nodeId, fieldKey, attachmentId);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(customFieldService.attachmentContentType(
                    projectId, nodeId, fieldKey, attachmentId));
        } catch (RuntimeException ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        String filename = customFieldService.attachmentName(projectId, nodeId, fieldKey, attachmentId);
        return ResponseEntity.ok().contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(resource);
    }

    @DeleteMapping("/{fieldKey}/attachments/{attachmentId}")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<Void> delete(@PathVariable Long projectId, @PathVariable Long nodeId,
                                       @PathVariable String fieldKey, @PathVariable Long attachmentId) {
        customFieldService.deleteAttachment(projectId, nodeId, fieldKey, attachmentId);
        return ResponseResult.success();
    }
}
