package com.brad.pms.ai.command.project;

import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.dto.request.ProjectCreateCmd;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.service.ProjectPermissionService;
import com.brad.pms.service.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectCommandTest {

    @Mock ProjectService projectService;
    @Mock ProjectPermissionService permissionService;

    @Test
    void projectCreatePreviewsTheResolvedOrganizationAndDates() {
        when(permissionService.requireProjectCreateOrgUnit(11L)).thenReturn(11L);

        CommandPreview preview = new CreateProjectCommand(projectService, permissionService, new ObjectMapper())
                .preview(new CommandPreviewRequest(CommandName.PROJECT_CREATE, Map.of(
                        "name", "AI 项目",
                        "orgUnitId", 11L,
                        "startDate", "2026-09-17",
                        "endDate", "2026-09-30"), "project-list", "v1"));

        assertThat(preview.command()).isEqualTo(CommandName.PROJECT_CREATE);
        assertThat(preview.changes()).singleElement().satisfies(change -> {
            assertThat(change).containsEntry("name", "AI 项目");
            assertThat(change).containsEntry("orgUnitId", 11L);
            assertThat(change).containsEntry("startDate", java.time.LocalDate.of(2026, 9, 17));
            assertThat(change).containsEntry("endDate", java.time.LocalDate.of(2026, 9, 30));
        });
    }

    @Test
    void projectCreatePassesArgumentsToTheExistingProjectService() {
        ProjectDTO created = new ProjectDTO();
        created.setId(88L);
        when(projectService.create(any(ProjectCreateCmd.class))).thenReturn(created);
        AiOperationDO operation = operation("{\"name\":\"AI 项目\",\"priority\":3,\"startDate\":\"2026-09-17\"}");

        CommandResult result = new CreateProjectCommand(projectService, permissionService, new ObjectMapper()).execute(operation);

        ArgumentCaptor<ProjectCreateCmd> captor = ArgumentCaptor.forClass(ProjectCreateCmd.class);
        verify(projectService).create(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("AI 项目");
        assertThat(captor.getValue().getPriority()).isEqualTo(3);
        assertThat(captor.getValue().getStartDate().toString()).isEqualTo("2026-09-17");
        assertThat(result.message()).isEqualTo("项目已创建");
    }

    @Test
    void archiveUsesTheProjectVersionCapturedByThePreview() {
        ProjectDO project = project(4);
        when(permissionService.requireProjectManageable(22L, "归档项目")).thenReturn(project);
        ProjectDTO archived = new ProjectDTO();
        archived.setId(22L);
        when(projectService.terminate(22L, "需求取消")).thenReturn(archived);

        CommandResult result = new ArchiveProjectCommand(projectService, permissionService, new ObjectMapper())
                .execute(operation("{\"projectId\":22,\"reason\":\"需求取消\"}",
                        "[{\"projectVersion\":4}]"));

        verify(projectService).terminate(22L, "需求取消");
        assertThat(result.message()).isEqualTo("项目已归档");
    }

    @Test
    void deleteUsesTheExistingSoftDeleteService() {
        when(permissionService.requireProjectManageable(22L, "删除项目")).thenReturn(project(3));

        CommandResult result = new DeleteProjectCommand(projectService, permissionService, new ObjectMapper())
                .execute(operation("{\"projectId\":22,\"reason\":\"重复项目\"}",
                        "[{\"projectVersion\":3}]"));

        verify(projectService).delete(22L, "重复项目");
        assertThat(result.message()).isEqualTo("项目已删除");
    }

    private static ProjectDO project(int version) {
        ProjectDO project = new ProjectDO();
        project.setId(22L);
        project.setName("方案项目");
        project.setVersion(version);
        project.setStatus(1);
        return project;
    }

    private static AiOperationDO operation(String arguments) {
        return operation(arguments, "[]");
    }

    private static AiOperationDO operation(String arguments, String versions) {
        AiOperationDO operation = new AiOperationDO();
        operation.setId("op-project-1");
        operation.setArgumentsJson(arguments);
        operation.setExpectedVersionsJson(versions);
        return operation;
    }
}
