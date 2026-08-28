package com.brad.pms.storage;

import com.brad.pms.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/** Secure local implementation used by the default self-hosted deployment. */
@Service
public class LocalFileStorageService implements FileStorageService {

    public static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");
    private final Path root;
    private final long quotaBytes;

    @Autowired
    public LocalFileStorageService(
            @Value("${pms.upload-dir:/var/lib/pms/uploads}") String uploadDir,
            @Value("${pms.upload.quota-bytes:524288000}") long quotaBytes) {
        this(Path.of(uploadDir), quotaBytes);
    }

    public LocalFileStorageService(Path uploadDir, long quotaBytes) {
        if (quotaBytes < 1) {
            throw new IllegalArgumentException("upload quota must be positive");
        }
        this.root = uploadDir.toAbsolutePath().normalize();
        this.quotaBytes = quotaBytes;
    }

    @Override
    public synchronized StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw BusinessException.error("请选择要上传的图片");
        if (file.getSize() > MAX_FILE_SIZE) throw BusinessException.error("图片大小不能超过 5MB");
        String declaredType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String actualType = detectImageType(file);
        if (!IMAGE_TYPES.contains(actualType) || !actualType.equals(declaredType)) {
            throw BusinessException.error("文件类型与图片内容不匹配");
        }
        if (directorySize() + file.getSize() > quotaBytes) {
            throw BusinessException.error("上传空间已超过配额");
        }

        String key = UUID.randomUUID() + extensionOf(actualType);
        try {
            Files.createDirectories(root);
            Path target = root.resolve(key).normalize();
            if (!target.startsWith(root)) throw new IOException("invalid storage key");
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target);
            }
            return new StoredFile(key, file.getOriginalFilename(), actualType, file.getSize());
        } catch (IOException ex) {
            throw BusinessException.error("图片保存失败，请稍后重试");
        }
    }

    @Override
    public Resource load(String key) {
        if (key == null || key.isBlank() || key.contains("/") || key.contains("\\")) {
            throw BusinessException.error("文件不存在");
        }
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            throw BusinessException.error("文件不存在");
        }
        return new FileSystemResource(target);
    }

    private String detectImageType(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            if (header.length >= 8
                    && (header[0] & 0xff) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                    && (header[4] & 0xff) == 0x0d && (header[5] & 0xff) == 0x0a
                    && (header[6] & 0xff) == 0x1a && (header[7] & 0xff) == 0x0a) return "image/png";
            if (header.length >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff) return "image/jpeg";
            if (header.length >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F'
                    && header[3] == '8' && (header[4] == '7' || header[4] == '9') && header[5] == 'a') return "image/gif";
            if (header.length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') return "image/webp";
            return "";
        } catch (IOException ex) {
            throw BusinessException.error("无法读取上传文件");
        }
    }

    private long directorySize() {
        if (!Files.isDirectory(root)) return 0L;
        try (Stream<Path> files = Files.list(root)) {
            return files.filter(Files::isRegularFile).mapToLong(path -> {
                try { return Files.size(path); } catch (IOException ex) { return 0L; }
            }).sum();
        } catch (IOException ex) {
            throw BusinessException.error("无法读取上传空间");
        }
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
