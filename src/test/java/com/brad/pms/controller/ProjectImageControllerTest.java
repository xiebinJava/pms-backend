package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.storage.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectImageControllerTest {

    @Test
    void uploadReturnsPublicKeyAndNeverARealServerPath() {
        FileStorageService storage = mock(FileStorageService.class);
        when(storage.store(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new FileStorageService.StoredFile("random-key.png", "设计稿.png", "image/png", 12));
        ProjectImageController controller = new ProjectImageController(storage);

        ResponseResult<Map<String, String>> response = controller.upload(
                new MockMultipartFile("file", "设计稿.png", "image/png", new byte[]{1}));

        assertThat(response.getData()).containsEntry("name", "设计稿.png");
        assertThat(response.getData().get("url")).isEqualTo("/api/projects/images/random-key.png");
        assertThat(response.getData().get("url")).doesNotContain("/tmp", "uploads");
    }

    @Test
    void readDelegatesToStorage() {
        FileStorageService storage = mock(FileStorageService.class);
        when(storage.load("random-key.png")).thenReturn(new ByteArrayResource(new byte[]{1, 2}));
        ProjectImageController controller = new ProjectImageController(storage);

        assertThat(controller.read("random-key.png").getStatusCodeValue()).isEqualTo(200);
        assertThat(controller.read("random-key.png").getBody()).isNotNull();
    }
}
