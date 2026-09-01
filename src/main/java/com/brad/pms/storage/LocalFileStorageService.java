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
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/** Secure local implementation used by the default self-hosted deployment. */
@Service
public class LocalFileStorageService implements FileStorageService {

    public static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");
    private static final Duration TEMP_FILE_MAX_AGE = Duration.ofHours(1);
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
        if (uploadDir == null || !uploadDir.isAbsolute()) {
            throw new IllegalArgumentException("upload directory must be an absolute path outside the application directory");
        }
        this.root = uploadDir.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(this.root)) {
            throw new IllegalArgumentException("upload directory must not be a symbolic link");
        }
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (root.equals(workingDirectory) || root.startsWith(workingDirectory)) {
            throw new IllegalArgumentException("upload directory must be outside the application directory");
        }
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
        return persist(file, actualType, "图片保存失败，请稍后重试");
    }

    @Override
    public synchronized StoredFile storeAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) throw BusinessException.error("请选择要上传的附件");
        if (file.getSize() > MAX_FILE_SIZE) throw BusinessException.error("附件大小不能超过 5MB");
        String declaredType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String actualType = detectImageType(file);
        if (actualType.isEmpty() && isPdf(file) && "application/pdf".equals(declaredType)) {
            actualType = "application/pdf";
        }
        if (actualType.isEmpty() || (!IMAGE_TYPES.contains(actualType) && !"application/pdf".equals(actualType))) {
            throw BusinessException.error("附件仅支持图片或 PDF");
        }
        if (!actualType.equals(declaredType)) {
            throw BusinessException.error("文件类型与内容不匹配");
        }
        return persist(file, actualType, "附件保存失败，请稍后重试");
    }

    private StoredFile persist(MultipartFile file, String actualType, String saveError) {
        String key = UUID.randomUUID() + extensionOf(actualType);
        try {
            Files.createDirectories(root);
            Path storageRoot = verifiedRoot();
            Path lockPath = storageRoot.resolve(".quota.lock");
            if (Files.isSymbolicLink(lockPath)) {
                throw new IOException("quota lock must not be a symbolic link");
            }
            try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
                 FileLock ignored = lockChannel.lock()) {
                cleanupTemporaryFiles(storageRoot);
                if (directorySize(storageRoot) > quotaBytes - file.getSize()) {
                    throw BusinessException.error("上传空间已超过配额");
                }
                Path target = storageRoot.resolve(key).normalize();
                if (!target.startsWith(storageRoot) || Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("invalid storage key");
                }
                Path temporary = Files.createTempFile(storageRoot, ".upload-", ".tmp");
                try {
                    try (InputStream input = file.getInputStream()) {
                        Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                    }
                    try {
                        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
            return new StoredFile(key, file.getOriginalFilename(), actualType, file.getSize());
        } catch (IOException ex) {
            throw BusinessException.error(saveError);
        }
    }

    @Override
    public Resource load(String key) {
        if (key == null || key.isBlank() || key.contains("/") || key.contains("\\") || key.indexOf('\0') >= 0) {
            throw BusinessException.error("文件不存在");
        }
        if (!isSafeStorageKey(key)) {
            throw BusinessException.error("文件不存在");
        }
        try {
            Path storageRoot = verifiedRoot();
            Path target = storageRoot.resolve(key).normalize();
            if (!target.startsWith(storageRoot) || Files.isSymbolicLink(target)) {
                throw BusinessException.error("文件不存在");
            }
            BasicFileAttributes attributes = Files.readAttributes(target, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) throw BusinessException.error("文件不存在");
            return new FileSystemResource(target);
        } catch (IOException ex) {
            throw BusinessException.error("文件不存在");
        }
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank() || key.contains("/") || key.contains("\\") || key.indexOf('\0') >= 0) {
            return;
        }
        if (!isSafeStorageKey(key)) return;
        try {
            Path storageRoot = verifiedRoot();
            Path target = storageRoot.resolve(key).normalize();
            if (!target.startsWith(storageRoot) || Files.isSymbolicLink(target)) return;
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // Soft-delete in the database still hides the file from the API.
        }
    }

    private static boolean isSafeStorageKey(String key) {
        return key.matches("[0-9a-fA-F-]{36}\\.(png|jpg|gif|webp|pdf)");
    }

    private boolean isPdf(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(5);
            return header.length >= 5
                    && header[0] == '%' && header[1] == 'P' && header[2] == 'D'
                    && header[3] == 'F' && header[4] == '-';
        } catch (IOException ex) {
            throw BusinessException.error("无法读取上传文件");
        }
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

    private Path verifiedRoot() throws IOException {
        if (Files.isSymbolicLink(root)) throw new IOException("upload root must not be a symbolic link");
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(root);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("upload root is not a directory");
        }
        Path realRoot = root.toRealPath();
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path realWorkingDirectory = Files.exists(workingDirectory, LinkOption.NOFOLLOW_LINKS)
                ? workingDirectory.toRealPath() : workingDirectory;
        if (realRoot.equals(realWorkingDirectory) || realRoot.startsWith(realWorkingDirectory)) {
            throw new IOException("upload directory resolves inside application directory");
        }
        return realRoot;
    }

    private long directorySize(Path storageRoot) {
        if (!Files.isDirectory(storageRoot, LinkOption.NOFOLLOW_LINKS)) return 0L;
        try (Stream<Path> files = Files.list(storageRoot)) {
            long total = 0L;
            for (Path path : (Iterable<Path>) files::iterator) {
                if (Files.isSymbolicLink(path)) throw new IOException("symbolic links are not allowed");
                if (path.getFileName().toString().startsWith(".upload-")) continue;
                BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                if (attributes.isRegularFile()) total = Math.addExact(total, attributes.size());
            }
            return total;
        } catch (IOException | ArithmeticException ex) {
            throw BusinessException.error("无法读取上传空间");
        }
    }

    private void cleanupTemporaryFiles(Path storageRoot) throws IOException {
        Instant cutoff = Instant.now().minus(TEMP_FILE_MAX_AGE);
        try (Stream<Path> files = Files.list(storageRoot)) {
            for (Path path : (Iterable<Path>) files::iterator) {
                if (Files.isSymbolicLink(path)) throw new IOException("symbolic links are not allowed");
                if (!path.getFileName().toString().startsWith(".upload-")) continue;
                BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                if (attributes.isRegularFile() && attributes.lastModifiedTime().toInstant().isBefore(cutoff)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            default -> ".jpg";
        };
    }
}
