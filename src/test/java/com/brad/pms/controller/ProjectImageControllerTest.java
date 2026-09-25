package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.service.ProjectImageService;
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
        ProjectImageService imageService = mock(ProjectImageService.class);
        when(imageService.upload(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of("name", "设计稿.png", "url", "/api/projects/7/images/random-key.png"));
        ProjectImageController controller = new ProjectImageController(imageService);

        ResponseResult<Map<String, String>> response = controller.upload(
                7L, new MockMultipartFile("file", "设计稿.png", "image/png", new byte[]{1}));

        assertThat(response.getData()).containsEntry("name", "设计稿.png");
        assertThat(response.getData().get("url")).isEqualTo("/api/projects/7/images/random-key.png");
        assertThat(response.getData().get("url")).doesNotContain("/tmp", "uploads");
    }

    @Test
    void readDelegatesToStorage() {
        ProjectImageService imageService = mock(ProjectImageService.class);
        when(imageService.read(7L, "random-key.png")).thenReturn(new ByteArrayResource(new byte[]{1, 2}));
        ProjectImageController controller = new ProjectImageController(imageService);

        assertThat(controller.read(7L, "random-key.png").getStatusCodeValue()).isEqualTo(200);
        assertThat(controller.read(7L, "random-key.png").getBody()).isNotNull();
    }
}
