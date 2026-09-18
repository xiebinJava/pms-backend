package com.brad.pms.ai.command.task;

import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.dto.request.TaskUpdateCmd;
import com.brad.pms.dto.response.ProjectTaskDTO;
import com.brad.pms.dto.response.TaskDetailDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.service.ProjectPermissionService;
import com.brad.pms.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateTaskCommandTest {

    @Mock TaskService taskService;
    @Mock ProjectPermissionService permissionService;

    @Test
    void previewsOnlyWhitelistedTaskFieldsAndCapturesAllVersions() {
        TaskDetailDTO task = task(41L, 5);
        when(taskService.getDetail(41L)).thenReturn(task);
        when(permissionService.requireProjectReadable(22L)).thenReturn(project(2));
        when(permissionService.requireNode(22L, 7L)).thenReturn(node(7L, 3));

        CommandPreview preview = command().preview(new CommandPreviewRequest(
                CommandName.TASK_UPDATE,
                Map.of("taskId", 41L, "title", "更新后的标题", "status", 1),
                "pms:project-detail:22:7", "v1"));

        assertThat(preview.command()).isEqualTo(CommandName.TASK_UPDATE);
        assertThat(preview.changes()).singleElement().satisfies(change -> {
            assertThat(change).containsEntry("title", "更新后的标题");
            assertThat(change).containsEntry("status", 1);
            assertThat(change).containsEntry("expectedVersion", 5);
            assertThat(change).containsEntry("projectVersion", 2);
            assertThat(change).containsEntry("nodeVersion", 3);
        });
    }

    @Test
    void executesWithTheVersionCapturedByThePreview() {
        when(taskService.getDetail(41L)).thenReturn(task(41L, 5));
        when(permissionService.requireProjectReadable(22L)).thenReturn(project(2));
        when(permissionService.requireNode(22L, 7L)).thenReturn(node(7L, 3));
        ProjectTaskDTO updated = new ProjectTaskDTO();
        updated.setId(41L);
        when(taskService.update(eq(41L), org.mockito.ArgumentMatchers.any(TaskUpdateCmd.class))).thenReturn(updated);

        AiOperationDO operation = new AiOperationDO();
        operation.setId("op-task-update-1");
        operation.setArgumentsJson("{\"taskId\":41,\"status\":2}");
        operation.setExpectedVersionsJson("[{\"expectedVersion\":5,\"projectVersion\":2,\"nodeVersion\":3}]");

        CommandResult result = command().execute(operation);

        ArgumentCaptor<TaskUpdateCmd> captor = ArgumentCaptor.forClass(TaskUpdateCmd.class);
        verify(taskService).update(eq(41L), captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(5);
        assertThat(captor.getValue().getStatus()).isEqualTo(2);
        assertThat(result.message()).isEqualTo("任务已更新");
    }

    @Test
    void rejectsUnknownPatchFieldsBeforeCreatingAPreview() {
        assertThatThrownBy(() -> command().preview(new CommandPreviewRequest(
                        CommandName.TASK_UPDATE,
                        Map.of("taskId", 41L, "sql", "drop"), "ctx", "v1")))
                .isInstanceOf(com.brad.pms.common.exception.BusinessException.class)
                .hasMessageContaining("不支持的 task.update 参数");
    }

    private UpdateTaskCommand command() {
        return new UpdateTaskCommand(taskService, permissionService, new ObjectMapper());
    }

    private static TaskDetailDTO task(Long id, int version) {
        TaskDetailDTO task = new TaskDetailDTO();
        task.setId(id);
        task.setProjectId(22L);
        task.setNodeId(7L);
        task.setVersion(version);
        task.setTitle("原任务");
        return task;
    }

    private static ProjectDO project(int version) {
        ProjectDO project = new ProjectDO();
        project.setId(22L);
        project.setVersion(version);
        return project;
    }

    private static ProjectNodeDO node(Long id, int version) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(id);
        node.setProjectId(22L);
        node.setVersion(version);
        node.setStatus(1);
        return node;
    }
}
