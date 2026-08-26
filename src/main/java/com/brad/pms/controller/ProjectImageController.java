package com.brad.pms.controller;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.response.ResponseResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/projects/images")
public class ProjectImageController {

    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");

    private final Path uploadDirectory;

    public ProjectImageController(@Value("${pms.upload-dir:./uploads}") String uploadDirectory) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    @PostMapping
    public ResponseResult<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (file.isEmpty()) throw BusinessException.error("请选择要上传的图片");
        if (!ALLOWED_TYPES.contains(contentType)) throw BusinessException.error("仅支持 PNG、JPG、GIF、WEBP 图片");
        if (file.getSize() > MAX_IMAGE_SIZE) throw BusinessException.error("图片大小不能超过 5MB");

        String extension = extensionOf(contentType);
        String filename = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(uploadDirectory);
            Files.copy(file.getInputStream(), uploadDirectory.resolve(filename));
        } catch (IOException ex) {
            throw BusinessException.error("图片保存失败，请稍后重试");
        }
        return ResponseResult.success(Map.of("name", StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : filename, "url", "/api/projects/images/" + filename));
    }

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> read(@PathVariable String filename) {
        Path target = uploadDirectory.resolve(filename).normalize();
        if (!target.startsWith(uploadDirectory) || !Files.isRegularFile(target)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(target);
        MediaType mediaType = MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }

    private String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
