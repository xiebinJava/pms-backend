package com.brad.pms.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageServiceTest {

    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 0
    };

    private static final byte[] JPEG = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00, 0x01};

    @Test
    void storesByRandomKeyAndNeverUsesOriginalPath(@TempDir Path tempDir) throws Exception {
        LocalFileStorageService service = new LocalFileStorageService(tempDir, 1024 * 1024);
        LocalFileStorageService.StoredFile stored = service.store(
                new MockMultipartFile("file", "../../secret.png", "image/png", PNG));

        assertThat(stored.key()).matches("[0-9a-f-]{36}\\.png");
        assertThat(stored.originalFilename()).isEqualTo("../../secret.png");
        assertThat(stored.key()).doesNotContain("secret");
        assertThat(Files.exists(tempDir.resolve(stored.key()))).isTrue();
        assertThat(stored.key()).doesNotContain(tempDir.toString());
    }

    @Test
    void rejectsForgedMimeAndOversizedOrOverQuotaFiles(@TempDir Path tempDir) throws Exception {
        LocalFileStorageService service = new LocalFileStorageService(tempDir, 16);

        assertThatThrownBy(() -> service.store(new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0])))
                .hasMessageContaining("图片");

        assertThatThrownBy(() -> service.store(new MockMultipartFile(
                "file", "photo.png", "image/png", "not-a-png".getBytes())))
                .hasMessageContaining("文件类型");
        assertThatThrownBy(() -> service.store(new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[5 * 1024 * 1024 + 1])))
                .hasMessageContaining("5MB");

        service.store(new MockMultipartFile("file", "first.png", "image/png", PNG));
        assertThatThrownBy(() -> service.store(new MockMultipartFile(
                "file", "one.png", "image/png", PNG)))
                .hasMessageContaining("配额");
    }

    @Test
    void acceptsJpegAndKeepsDoubleExtensionOutOfStorageKey(@TempDir Path tempDir) {
        LocalFileStorageService service = new LocalFileStorageService(tempDir, 1024 * 1024);

        LocalFileStorageService.StoredFile stored = service.store(new MockMultipartFile(
                "file", "invoice.php.jpg", "image/jpeg", JPEG));

        assertThat(stored.key()).matches("[0-9a-f-]{36}\\.jpg");
        assertThat(stored.key()).doesNotContain("invoice", "php");
    }

    @Test
    void rejectsPathLikeStorageKeys(@TempDir Path tempDir) {
        LocalFileStorageService service = new LocalFileStorageService(tempDir, 1024);

        assertThatThrownBy(() -> service.load("../secret.png"))
                .hasMessageContaining("文件不存在");
    }
}
