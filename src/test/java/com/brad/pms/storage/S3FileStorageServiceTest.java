package com.brad.pms.storage;

import com.brad.pms.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3FileStorageServiceTest {

    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 0
    };

    @Test
    void storesValidatedAttachmentOnTheObjectStore() {
        MemoryObjectStore store = new MemoryObjectStore();
        S3FileStorageService service = new S3FileStorageService(store);

        FileStorageService.StoredFile stored = service.storeAttachment(new MockMultipartFile(
                "file", "../../secret.png", "image/png", PNG));

        assertThat(stored.key()).matches("[0-9a-f-]{36}\\.png");
        assertThat(stored.key()).doesNotContain("secret");
        assertThat(store.objects).containsKey(stored.key());
        assertThat(service.load(stored.key()).exists()).isTrue();

        service.delete(stored.key());
        assertThat(store.objects).doesNotContainKey(stored.key());
    }

    @Test
    void rejectsUnsafeKeysAndForgedMime() {
        S3FileStorageService service = new S3FileStorageService(new MemoryObjectStore());
        assertThatThrownBy(() -> service.store(new MockMultipartFile(
                "file", "photo.png", "image/png", "not-a-png".getBytes())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件类型");
        assertThatThrownBy(() -> service.load("../secret.png"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件不存在");
        service.delete("../secret.png");
    }

    private static final class MemoryObjectStore implements ObjectStore {
        private final Map<String, byte[]> objects = new LinkedHashMap<>();

        @Override
        public void put(String key, byte[] content, String contentType) {
            objects.put(key, content);
        }

        @Override
        public org.springframework.core.io.Resource load(String key) {
            byte[] bytes = objects.get(key);
            if (bytes == null) throw BusinessException.error("文件不存在");
            return new ByteArrayResource(bytes);
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
        }
    }
}
