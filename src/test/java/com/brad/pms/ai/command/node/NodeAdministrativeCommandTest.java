package com.brad.pms.ai.command.node;

import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.dto.request.NodeOwnerUpdateCmd;
import com.brad.pms.dto.request.NodeScheduleUpdateCmd;
import com.brad.pms.dto.response.ProjectNodeDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.service.NodeService;
import com.brad.pms.service.ProjectPermissionService;
import com.brad.pms.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeAdministrativeCommandTest {

    @Mock NodeService nodeService;
    @Mock ProjectPermissionService permissionService;
    @Mock UserService userService;

    @Test
    void ownerUpdatePreviewsAndExecutesWithNodeVersion() {
        when(permissionService.requireProjectManageable(22L, "分配节点负责人")).thenReturn(project(5));
        when(permissionService.requireNode(22L, 7L)).thenReturn(node(7L, 9));
        UserDO user = new UserDO();
        user.setId(31L);
        user.setStatus("ACTIVE");
        when(userService.listByIds(List.of(31L))).thenReturn(List.of(user));
        UpdateNodeOwnerCommand command = new UpdateNodeOwnerCommand(nodeService, permissionService, userService, new ObjectMapper());

        CommandPreview preview = command.preview(new CommandPreviewRequest(CommandName.NODE_OWNER_UPDATE,
                Map.of("projectId", 22L, "nodeId", 7L, "ownerId", 31L), "project-detail", "v1"));

        assertThat(preview.changes()).singleElement().satisfies(change ->
                assertThat(change).containsEntry("nodeVersion", 9));
        ProjectNodeDTO updated = new ProjectNodeDTO();
        updated.setId(7L);
        when(nodeService.updateOwner(eq(22L), eq(7L), any(NodeOwnerUpdateCmd.class))).thenReturn(updated);
        CommandResult result = command.execute(operation("{\"projectId\":22,\"nodeId\":7,\"ownerId\":31}",
                "[{\"projectVersion\":5,\"nodeVersion\":9}]"));

        ArgumentCaptor<NodeOwnerUpdateCmd> captor = ArgumentCaptor.forClass(NodeOwnerUpdateCmd.class);
        verify(nodeService).updateOwner(eq(22L), eq(7L), captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(9);
        assertThat(result.message()).isEqualTo("节点负责人已更新");
    }

    @Test
    void scheduleUpdateKeepsAnOmittedDateAndChangesTheProvidedDate() {
        when(permissionService.requireProjectReadable(22L)).thenReturn(project(5));
        when(permissionService.requireManageableNode(22L, 7L, "编辑节点排期")).thenReturn(nodeWithDates(7L, 9));
        UpdateNodeScheduleCommand command = new UpdateNodeScheduleCommand(nodeService, permissionService, new ObjectMapper());

        CommandPreview preview = command.preview(new CommandPreviewRequest(CommandName.NODE_SCHEDULE_UPDATE,
                Map.of("projectId", 22L, "nodeId", 7L, "endDate", "2026-10-05"), "project-detail", "v1"));

        assertThat(preview.changes()).singleElement().satisfies(change -> {
            assertThat(change).containsEntry("fromStartDate", java.time.LocalDate.of(2026, 9, 20));
            assertThat(change).containsEntry("toStartDate", java.time.LocalDate.of(2026, 9, 20));
            assertThat(change).containsEntry("toEndDate", java.time.LocalDate.of(2026, 10, 5));
        });
        ProjectNodeDTO updated = new ProjectNodeDTO();
        updated.setId(7L);
        when(permissionService.requireNode(22L, 7L)).thenReturn(nodeWithDates(7L, 9));
        when(nodeService.updateSchedule(eq(22L), eq(7L), any(NodeScheduleUpdateCmd.class))).thenReturn(updated);
        command.execute(operation("{\"projectId\":22,\"nodeId\":7,\"endDate\":\"2026-10-05\"}",
                "[{\"projectVersion\":5,\"nodeVersion\":9}]"));

        ArgumentCaptor<NodeScheduleUpdateCmd> captor = ArgumentCaptor.forClass(NodeScheduleUpdateCmd.class);
        verify(nodeService).updateSchedule(eq(22L), eq(7L), captor.capture());
        assertThat(captor.getValue().getStartDate().toString()).isEqualTo("2026-09-20");
        assertThat(captor.getValue().getEndDate().toString()).isEqualTo("2026-10-05");
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
        node.setName("方案设计");
        node.setVersion(version);
        node.setStatus(1);
        return node;
    }

    private static ProjectNodeDO nodeWithDates(Long id, int version) {
        ProjectNodeDO node = node(id, version);
        node.setStartDate(java.time.LocalDate.of(2026, 9, 20));
        node.setEndDate(java.time.LocalDate.of(2026, 9, 30));
        return node;
    }

    private static AiOperationDO operation(String arguments, String versions) {
        AiOperationDO operation = new AiOperationDO();
        operation.setId("op-node-1");
        operation.setArgumentsJson(arguments);
        operation.setExpectedVersionsJson(versions);
        return operation;
    }
}
