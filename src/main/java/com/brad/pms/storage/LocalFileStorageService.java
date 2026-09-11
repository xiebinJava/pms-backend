package com.brad.pms.storage;

import com.brad.pms.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import java.util.stream.Stream;

/** Secure local implementation used by the default self-hosted deployment. */
@Service
@ConditionalOnProperty(name = "pms.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    public static final long MAX_FILE_SIZE = UploadFiles.MAX_FILE_SIZE;
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
        return persist(file, UploadFiles.requireImage(file), "图片保存失败，请稍后重试");
    }

    @Override
    public synchronized StoredFile storeAttachment(MultipartFile file) {
        return persist(file, UploadFiles.requireAttachment(file), "附件保存失败，请稍后重试");
    }

    private StoredFile persist(MultipartFile file, String actualType, String saveError) {
        String key = UploadFiles.newKey(actualType);
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
        if (!UploadFiles.isSafeStorageKey(key)) {
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
        if (!UploadFiles.isSafeStorageKey(key)) return;
        try {
            Path storageRoot = verifiedRoot();
            Path target = storageRoot.resolve(key).normalize();
            if (!target.startsWith(storageRoot) || Files.isSymbolicLink(target)) return;
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // Soft-delete in the database still hides the file from the API.
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

}

