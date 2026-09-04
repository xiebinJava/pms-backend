package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.ProjectImageDO;
import com.brad.pms.mapper.ProjectImageMapper;
import com.brad.pms.security.UserContext;
import com.brad.pms.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProjectImageService {
    private final ProjectImageMapper imageMapper;
    private final ProjectPermissionService permissionService;
    private final FileStorageService storageService;
    private final OperationLogService operationLogService;

    public Map<String, String> upload(Long projectId, MultipartFile file) {
        permissionService.requireProjectWritable(projectId, "上传项目图片");
        FileStorageService.StoredFile stored = storageService.store(file);
        ProjectImageDO image = new ProjectImageDO();
        image.setProjectId(projectId);
        image.setFileKey(stored.key());
        image.setOriginalName(sanitizeName(stored.originalFilename(), stored.key()));
        image.setContentType(stored.contentType());
        image.setSizeBytes(stored.size());
        imageMapper.insert(image);
        operationLogService.record(AuditEvent.success(
                AuditAction.PROJECT_IMAGE_UPLOADED.name(), AuditResourceType.PROJECT_IMAGE.name(), image.getId(), projectId,
                null, null, java.util.Map.of("filename", image.getOriginalName(),
                        "contentType", String.valueOf(image.getContentType()), "sizeBytes", image.getSizeBytes())));
        return Map.of("name", image.getOriginalName(),
                "url", "/api/projects/" + projectId + "/images/" + stored.key());
    }

    public Resource read(Long projectId, String filename) {
        permissionService.requireProject(projectId);
        ProjectImageDO image = find(projectId, filename);
        return storageService.load(image.getFileKey());
    }

    public void delete(Long projectId, String filename) {
        permissionService.requireProjectWritable(projectId, "删除项目图片");
        ProjectImageDO image = find(projectId, filename);
        imageMapper.deleteById(image.getId());
        storageService.delete(image.getFileKey());
        operationLogService.record(AuditEvent.success(
                AuditAction.PROJECT_IMAGE_DELETED.name(), AuditResourceType.PROJECT_IMAGE.name(), image.getId(), projectId,
                null, java.util.Map.of("filename", image.getOriginalName()), null));
    }

    private ProjectImageDO find(Long projectId, String filename) {
        ProjectImageDO image = imageMapper.selectOne(new LambdaQueryWrapper<ProjectImageDO>()
                .eq(ProjectImageDO::getProjectId, projectId)
                .eq(ProjectImageDO::getFileKey, filename));
        if (image == null) throw BusinessException.error("图片不存在");
        return image;
    }

    private static String sanitizeName(String original, String fallback) {
        if (!StringUtils.hasText(original)) return fallback;
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[\\r\\n\\t]", "").trim();
        return name.isBlank() ? fallback : name;
    }
}
