package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.ProjectImageDO;
import com.brad.pms.mapper.ProjectImageMapper;
import com.brad.pms.storage.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectImageServiceTest {
    @Mock ProjectImageMapper imageMapper;
    @Mock ProjectPermissionService permissionService;
    @Mock FileStorageService storageService;
    @Mock OperationLogService operationLogService;

    @InjectMocks ProjectImageService imageService;

    @Test
    void uploadPersistsTheOwningProjectAndReturnsBoundUrl() {
        when(storageService.store(any())).thenReturn(
                new FileStorageService.StoredFile("random-key.png", "../设计稿.png", "image/png", 12));

        var result = imageService.upload(7L,
                new MockMultipartFile("file", "../设计稿.png", "image/png", new byte[]{1}));

        ArgumentCaptor<ProjectImageDO> captor = ArgumentCaptor.forClass(ProjectImageDO.class);
        verify(imageMapper).insert(captor.capture());
        assertThat(captor.getValue().getProjectId()).isEqualTo(7L);
        assertThat(captor.getValue().getOriginalName()).isEqualTo("设计稿.png");
        assertThat(result).containsEntry("url", "/api/projects/7/images/random-key.png");
    }

    @Test
    void readRejectsAnImageKeyFromAnotherProject() {
        when(imageMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> imageService.read(7L, "random-key.png"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("图片不存在");
        verify(storageService, never()).load(any());
    }
}
