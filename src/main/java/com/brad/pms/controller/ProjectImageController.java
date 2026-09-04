package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.service.ProjectImageService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/projects/{projectId}/images")
public class ProjectImageController {

    private final ProjectImageService imageService;

    public ProjectImageController(ProjectImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<Map<String, String>> upload(@PathVariable Long projectId,
                                                      @RequestParam("file") MultipartFile file) {
        return ResponseResult.success(imageService.upload(projectId, file));
    }

    @GetMapping("/{filename}")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseEntity<Resource> read(@PathVariable Long projectId, @PathVariable String filename) {
        try {
            Resource resource = imageService.read(projectId, filename);
            MediaType mediaType = MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok().contentType(mediaType).body(resource);
        } catch (com.brad.pms.common.exception.BusinessException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{filename}")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<Void> delete(@PathVariable Long projectId, @PathVariable String filename) {
        imageService.delete(projectId, filename);
        return ResponseResult.success();
    }
}
