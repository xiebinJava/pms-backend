package com.brad.pms.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.brad.pms.dto.response.TaskAttachmentDTO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectTaskAttachmentDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.mapper.ProjectTaskAttachmentMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import com.brad.pms.storage.FileStorageService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskAttachmentServiceTest {

    @Mock ProjectTaskAttachmentMapper attachmentMapper;
    @Mock ProjectTaskMapper taskMapper;
    @Mock ProjectPermissionService permissionService;
    @Mock UserService userService;
    @Mock FileStorageService fileStorageService;
    @Mock OperationLogService operationLogService;

    @InjectMocks TaskAttachmentService attachmentService;

    @BeforeEach
    void init() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        TableInfoHelper.initTableInfo(assistant, ProjectTaskAttachmentDO.class);
        UserContext.set(new LoginUser(7L, "Alex.Zhang", "张伟", 1));
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void uploadStripsPathFromOriginalName() {
        when(taskMapper.selectById(1L)).thenReturn(openTask());
        when(permissionService.requireProject(9L)).thenReturn(openProject());
        when(permissionService.requireNode(9L, 3L)).thenReturn(openNode());
        when(fileStorageService.storeAttachment(any())).thenReturn(
                new FileStorageService.StoredFile("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.pdf",
                        "../../secret/设计稿.pdf", "application/pdf", 12));
        when(userService.listByIds(any())).thenReturn(List.of());

        TaskAttachmentDTO dto = attachmentService.upload(1L, new MockMultipartFile(
                "file", "../../secret/设计稿.pdf", "application/pdf", "%PDF-".getBytes()));

        ArgumentCaptor<ProjectTaskAttachmentDO> captor = ArgumentCaptor.forClass(ProjectTaskAttachmentDO.class);
        verify(attachmentMapper).insert(captor.capture());
        assertThat(captor.getValue().getOriginalName()).isEqualTo("设计稿.pdf");
        assertThat(dto.getUrl()).startsWith("/api/tasks/1/attachments/");
        assertThat(dto.getOriginalName()).isEqualTo("设计稿.pdf");
        assertThat(dto.isCanDelete()).isTrue();
    }

    @Test
    void attachmentOwnerCanDeleteWithoutTaskOrProjectWritePermission() {
        UserContext.set(new LoginUser(7L, "Alex.Zhang", "张伟", 0));
        ProjectTaskAttachmentDO row = new ProjectTaskAttachmentDO();
        row.setId(5L);
        row.setTaskId(1L);
        row.setProjectId(9L);
        row.setCreatedBy(7L);
        row.setFileKey("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.pdf");
        ProjectDO project = openProject();
        project.setCreatedBy(99L);
        ProjectNodeDO node = openNode();
        node.setOwnerId(99L);
        ProjectTaskDO task = openTask();
        task.setAssigneeId(99L);
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(permissionService.requireProject(9L)).thenReturn(project);
        when(permissionService.requireNode(9L, 3L)).thenReturn(node);
        when(permissionService.canWriteProject(project)).thenReturn(false);
        when(attachmentMapper.selectById(5L)).thenReturn(row);

        attachmentService.delete(1L, 5L);

        verify(attachmentMapper).deleteById(5L);
        verify(fileStorageService).delete(row.getFileKey());
    }

    @Test
    void listMarksAttachmentOwnerAsDeletable() {
        UserContext.set(new LoginUser(7L, "Alex.Zhang", "张伟", 0));
        ProjectTaskAttachmentDO row = new ProjectTaskAttachmentDO();
        row.setId(5L);
        row.setTaskId(1L);
        row.setProjectId(9L);
        row.setCreatedBy(7L);
        ProjectDO project = openProject();
        project.setCreatedBy(99L);
        ProjectNodeDO node = openNode();
        node.setOwnerId(99L);
        ProjectTaskDO task = openTask();
        task.setAssigneeId(99L);
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(permissionService.requireProject(9L)).thenReturn(project);
        when(permissionService.requireNode(9L, 3L)).thenReturn(node);
        when(permissionService.canWriteProject(project)).thenReturn(false);
        when(attachmentMapper.selectList(any())).thenReturn(List.of(row));
        when(userService.listByIds(any())).thenReturn(List.of());

        List<TaskAttachmentDTO> result = attachmentService.listByTask(1L);

        assertThat(result).singleElement().extracting(TaskAttachmentDTO::isCanDelete).isEqualTo(true);
    }

    @Test
    void deleteAllForTaskRemovesRowsAndFiles() {
        ProjectTaskAttachmentDO row = new ProjectTaskAttachmentDO();
        row.setId(5L);
        row.setTaskId(1L);
        row.setFileKey("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.pdf");
        when(attachmentMapper.selectList(any())).thenReturn(List.of(row));

        attachmentService.deleteAllForTask(1L);

        verify(attachmentMapper).deleteById(5L);
        verify(fileStorageService).delete("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.pdf");
    }

    private static ProjectTaskDO openTask() {
        ProjectTaskDO task = new ProjectTaskDO();
        task.setId(1L);
        task.setProjectId(9L);
        task.setNodeId(3L);
        task.setAssigneeId(7L);
        return task;
    }

    private static ProjectDO openProject() {
        ProjectDO project = new ProjectDO();
        project.setId(9L);
        project.setStatus(1);
        project.setCreatedBy(7L);
        return project;
    }

    private static ProjectNodeDO openNode() {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(3L);
        node.setProjectId(9L);
        node.setStatus(1);
        node.setOwnerId(7L);
        return node;
    }
}
