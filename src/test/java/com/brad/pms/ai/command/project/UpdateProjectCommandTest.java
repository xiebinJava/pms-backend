package com.brad.pms.ai.command.project;

import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.ProjectUpdateCmd;
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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateProjectCommandTest {

    @Mock ProjectService projectService;
    @Mock ProjectPermissionService permissionService;

    @Test
    void updatePreviewsOnlyTheProvidedFieldsAndKeepsTheRest() {
        when(permissionService.requireProjectWritable(22L, "编辑项目")).thenReturn(project(7));

        CommandPreview preview = command().preview(new CommandPreviewRequest(CommandName.PROJECT_UPDATE, Map.of(
                "projectId", 22L,
                "description", "基于当前达模型进行调整",
                "endDate", "2026-10-30"), "project-detail", "v1"));

        assertThat(preview.command()).isEqualTo(CommandName.PROJECT_UPDATE);
        assertThat(preview.changes()).singleElement().satisfies(change -> {
            assertThat(change).containsEntry("entity", "project");
            assertThat(change).containsEntry("action", "update");
            assertThat(change).containsEntry("projectId", 22L);
            assertThat(change).containsEntry("fromDescription", "旧描述");
            assertThat(change).containsEntry("toDescription", "基于当前达模型进行调整");
            assertThat(change).containsEntry("fromEndDate", null);
            assertThat(change).containsEntry("toEndDate", LocalDate.of(2026, 10, 30));
            assertThat(change).containsEntry("projectVersion", 7);
        });
        assertThat(preview.refreshScopes()).containsExactly("project-detail", "project-list", "project-dashboard");
    }

    @Test
    void updateRejectsUnknownArguments() {
        assertThatThrownBy(() -> command().preview(new CommandPreviewRequest(CommandName.PROJECT_UPDATE, Map.of(
                "projectId", 22L,
                "memberIds", java.util.List.of(1L, 2L)), "project-detail", "v1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的 project.update 参数: memberIds");
        verifyNoInteractions(permissionService);
    }

    @Test
    void updateRejectsABlankName() {
        assertThatThrownBy(() -> command().preview(new CommandPreviewRequest(CommandName.PROJECT_UPDATE, Map.of(
                "projectId", 22L,
                "name", "   "), "project-detail", "v1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("参数 name 不能为空");
    }

    @Test
    void updateRejectsAnInvertedSchedule() {
        when(permissionService.requireProjectWritable(22L, "编辑项目")).thenReturn(project(7));

        assertThatThrownBy(() -> command().preview(new CommandPreviewRequest(CommandName.PROJECT_UPDATE, Map.of(
                "projectId", 22L,
                "startDate", "2026-10-10",
                "endDate", "2026-10-01"), "project-detail", "v1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目开始日期不能晚于结束日期");
    }

    @Test
    void updateAssignsAProjectManagerAndNeverClearsOneSilently() {
        when(permissionService.requireProjectWritable(22L, "编辑项目")).thenReturn(project(7));
        when(permissionService.requireProjectManageable(22L, "变更项目经理")).thenReturn(project(7));
        ProjectDTO updated = new ProjectDTO();
        updated.setId(22L);
        when(projectService.update(eq(22L), any(ProjectUpdateCmd.class))).thenReturn(updated);

        CommandPreview preview = command().preview(new CommandPreviewRequest(CommandName.PROJECT_UPDATE, Map.of(
                "projectId", 22L,
                "projectManagerId", 31L), "project-detail", "v1"));

        assertThat(preview.changes()).singleElement().satisfies(change -> {
            assertThat(change).containsEntry("fromProjectManagerId", null);
            assertThat(change).containsEntry("toProjectManagerId", 31L);
        });

        command().execute(operation("{\"projectId\":22,\"projectManagerId\":31}",
                "[{\"projectVersion\":7}]"));
        ArgumentCaptor<ProjectUpdateCmd> captor = ArgumentCaptor.forClass(ProjectUpdateCmd.class);
        verify(projectService).update(eq(22L), captor.capture());
        assertThat(captor.getValue().getProjectManagerId()).isEqualTo(31L);
        assertThat(captor.getValue().getMemberIds()).isNull();
        assertThat(captor.getValue().getFollowerIds()).isNull();
    }

    @Test
    void updateRejectsClearingTheProjectManagerThroughAnExplicitNull() {
        when(permissionService.requireProjectWritable(22L, "编辑项目")).thenReturn(project(7));
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("projectId", 22L);
        arguments.put("projectManagerId", null);

        assertThatThrownBy(() -> command().preview(new CommandPreviewRequest(
                CommandName.PROJECT_UPDATE, arguments, "project-detail", "v1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目经理不能通过项目更新清空");
        verifyNoInteractions(projectService);
    }

    @Test
    void updateExecuteMergesCurrentValuesAndNeverTouchesComposition() {
        when(permissionService.requireProjectWritable(22L, "编辑项目")).thenReturn(project(7));
        ProjectDTO updated = new ProjectDTO();
        updated.setId(22L);
        when(projectService.update(eq(22L), any(ProjectUpdateCmd.class))).thenReturn(updated);

        CommandResult result = command().execute(operation(
                "{\"projectId\":22,\"description\":\"基于当前达模型进行调整\"}",
                "[{\"projectVersion\":7}]"));

        ArgumentCaptor<ProjectUpdateCmd> captor = ArgumentCaptor.forClass(ProjectUpdateCmd.class);
        verify(projectService).update(eq(22L), captor.capture());
        ProjectUpdateCmd cmd = captor.getValue();
        assertThat(cmd.getVersion()).isEqualTo(7);
        assertThat(cmd.getName()).isEqualTo("方案项目");
        assertThat(cmd.getDescription()).isEqualTo("基于当前达模型进行调整");
        assertThat(cmd.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(cmd.getEndDate()).isNull();
        assertThat(cmd.getPriority()).isNull();
        assertThat(cmd.getProjectLevel()).isNull();
        assertThat(cmd.getOrgUnitId()).isNull();
        assertThat(cmd.getMemberIds()).isNull();
        assertThat(cmd.getFollowerIds()).isNull();
        assertThat(cmd.getProjectManagerId()).isNull();
        assertThat(result.message()).isEqualTo("项目已更新");
    }

    @Test
    void updateExecuteRejectsAPreviewThatIsNoLongerFresh() {
        when(permissionService.requireProjectWritable(22L, "编辑项目")).thenReturn(project(7));

        assertThatThrownBy(() -> command().execute(operation(
                "{\"projectId\":22,\"description\":\"新的描述\"}",
                "[{\"projectVersion\":6}]")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目已发生变化，请重新生成预览");
        verifyNoInteractions(projectService);
    }

    @Test
    void updateClearsTheDescriptionWhenTheCallerPassesNullExplicitly() {
        ProjectDO project = project(7);
        when(permissionService.requireProjectWritable(22L, "编辑项目")).thenReturn(project);
        ProjectDTO updated = new ProjectDTO();
        updated.setId(22L);
        when(projectService.update(eq(22L), any(ProjectUpdateCmd.class))).thenReturn(updated);
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("projectId", 22L);
        arguments.put("description", null);

        CommandPreview preview = command().preview(new CommandPreviewRequest(
                CommandName.PROJECT_UPDATE, arguments, "project-detail", "v1"));
        assertThat(preview.changes()).singleElement()
                .satisfies(change -> assertThat(change).containsEntry("toDescription", null));

        command().execute(operation("{\"projectId\":22,\"description\":null}", "[{\"projectVersion\":7}]"));
        ArgumentCaptor<ProjectUpdateCmd> captor = ArgumentCaptor.forClass(ProjectUpdateCmd.class);
        verify(projectService).update(eq(22L), captor.capture());
        assertThat(captor.getValue().getDescription()).isNull();
    }

    private UpdateProjectCommand command() {
        return new UpdateProjectCommand(projectService, permissionService, new ObjectMapper());
    }

    private static ProjectDO project(int version) {
        ProjectDO project = new ProjectDO();
        project.setId(22L);
        project.setName("方案项目");
        project.setDescription("旧描述");
        project.setStatus(1);
        project.setVersion(version);
        project.setStartDate(LocalDate.of(2026, 9, 1));
        return project;
    }

    private static AiOperationDO operation(String arguments, String versions) {
        AiOperationDO operation = new AiOperationDO();
        operation.setId("op-project-update-1");
        operation.setArgumentsJson(arguments);
        operation.setExpectedVersionsJson(versions);
        return operation;
    }
}
