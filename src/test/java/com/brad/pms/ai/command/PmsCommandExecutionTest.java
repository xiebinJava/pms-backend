package com.brad.pms.ai.command;

import com.brad.pms.ai.command.task.CreateTaskCommand;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.service.ProjectPermissionService;
import com.brad.pms.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PmsCommandExecutionTest {

    @Mock TaskService taskService;
    @Mock ProjectPermissionService permissionService;

    @Test
    void taskCreatePreviewValidatesPermissionWithoutWriting() {
        ProjectDO project = new ProjectDO();
        project.setVersion(3);
        ProjectNodeDO node = new ProjectNodeDO();
        node.setVersion(2);
        when(permissionService.requireProjectReadable(7L)).thenReturn(project);
        when(permissionService.requireManageableNode(7L, 71L, "创建任务")).thenReturn(node);
        CreateTaskCommand command = new CreateTaskCommand(taskService, permissionService,
                new ObjectMapper().findAndRegisterModules());

        CommandPreview preview = command.preview(new CommandPreviewRequest(
                CommandName.TASK_CREATE,
                Map.of("projectId", 7, "nodeId", 71, "title", "整理接口"),
                "project-detail:7:71", "project-3:node-2"));

        verify(permissionService).requireManageableNode(7L, 71L, "创建任务");
        verify(taskService, never()).create(org.mockito.ArgumentMatchers.any());
        assertThat(preview.changes()).singleElement().satisfies(change -> {
            assertThat(change).containsEntry("action", "create");
            assertThat(change).containsEntry("title", "整理接口");
        });
        assertThat(preview.refreshScopes()).contains("task-board", "project-dashboard");
    }
}
