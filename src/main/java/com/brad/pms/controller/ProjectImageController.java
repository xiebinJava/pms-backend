package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.storage.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/projects/images")
public class ProjectImageController {

    private final FileStorageService storageService;

    public ProjectImageController(FileStorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        FileStorageService.StoredFile stored = storageService.store(file);
        return ResponseResult.success(Map.of("name", StringUtils.hasText(stored.originalFilename())
                ? stored.originalFilename() : stored.key(), "url", "/api/projects/images/" + stored.key()));
    }

    @GetMapping("/{filename}")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseEntity<Resource> read(@PathVariable String filename) {
        try {
            Resource resource = storageService.load(filename);
            MediaType mediaType = MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok().contentType(mediaType).body(resource);
        } catch (com.brad.pms.common.exception.BusinessException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
