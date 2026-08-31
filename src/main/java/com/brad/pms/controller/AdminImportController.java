package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.response.ImportPreviewDTO;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.service.EnterpriseImportService;
import com.brad.pms.service.ImportTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/import")
@RequiredArgsConstructor
public class AdminImportController {
    private final EnterpriseImportService importService;
    private final ImportTemplateService templateService;

    @PostMapping("/preview/organizations")
    @RequirePermission(PermissionCode.IMPORT_WRITE)
    public ResponseResult<ImportPreviewDTO> previewOrganizations(@RequestParam("file") MultipartFile file) {
        return ResponseResult.success(importService.previewOrganizations(file));
    }

    @PostMapping("/preview/users")
    @RequirePermission(PermissionCode.IMPORT_WRITE)
    public ResponseResult<ImportPreviewDTO> previewUsers(@RequestParam("file") MultipartFile file) {
        return ResponseResult.success(importService.previewUsers(file));
    }

    @PostMapping("/{jobId}/commit")
    @RequirePermission(PermissionCode.IMPORT_WRITE)
    public ResponseResult<Void> commit(@PathVariable String jobId) {
        importService.commit(jobId);
        return ResponseResult.success();
    }

    @GetMapping("/template/organizations.csv")
    @RequirePermission(PermissionCode.IMPORT_WRITE)
    public ResponseEntity<byte[]> organizationTemplate() { return csv(templateService.organizationCsvTemplate(), "organizations-template.csv"); }

    @GetMapping("/template/users.csv")
    @RequirePermission(PermissionCode.IMPORT_WRITE)
    public ResponseEntity<byte[]> userTemplate() { return csv(templateService.userCsvTemplate(), "users-template.csv"); }

    private ResponseEntity<byte[]> csv(byte[] content, String filename) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(content);
    }
}
