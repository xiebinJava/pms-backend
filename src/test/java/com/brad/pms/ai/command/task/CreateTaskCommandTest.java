package com.brad.pms.ai.command.task;

import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.security.LoginUser;
import com.brad.pms.service.ProjectPermissionService;
import com.brad.pms.security.UserContext;
import com.brad.pms.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateTaskCommandTest {

    @Mock TaskService taskService;
    @Mock ProjectPermissionService permissionService;
    @Mock ObjectMapper objectMapper;

    @BeforeEach
    void setUpUserContext() {
        UserContext.set(new LoginUser(18L, "alex.zhang", "张伟"));
    }

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void previewResolvesCurrentUserAssigneeFromAuthenticatedContext() {
        ProjectDO project = new ProjectDO();
        project.setId(22L);
        project.setVersion(4);
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(192L);
        node.setProjectId(22L);
        node.setVersion(2);
        when(permissionService.requireProjectReadable(22L)).thenReturn(project);
        when(permissionService.requireManageableNode(eq(22L), eq(192L), eq("创建任务"))).thenReturn(node);

        CommandPreview preview = new CreateTaskCommand(taskService, permissionService, objectMapper).preview(
                new CommandPreviewRequest(
                        CommandName.TASK_CREATE,
                        Map.of(
                                "projectId", 22,
                                "nodeId", 192,
                                "title", "任务1",
                                "assigneeScope", "current_user"),
                        "project-detail:22:192",
                        "project-4:node-2"));

        assertThat(preview.changes()).hasSize(1);
        assertThat(preview.changes().get(0).get("assigneeId")).isEqualTo(18L);
    }
}
