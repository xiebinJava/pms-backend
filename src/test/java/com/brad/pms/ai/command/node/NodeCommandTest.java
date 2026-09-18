package com.brad.pms.ai.command.node;

import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.dto.response.ProjectNodeDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.service.NodeService;
import com.brad.pms.service.ProjectPermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeCommandTest {

    @Mock NodeService nodeService;
    @Mock ProjectPermissionService permissionService;

    @Test
    void previewsCompletionWithTheCurrentProjectAndNodeVersions() {
        ProjectDO project = project(3);
        ProjectNodeDO node = node(7L, 4, "方案设计");
        when(permissionService.requireProjectReadable(22L)).thenReturn(project);
        when(permissionService.requireCompletableNode(22L, 7L)).thenReturn(node);

        CommandPreview preview = new CompleteNodeCommand(nodeService, permissionService, new ObjectMapper()).preview(
                new CommandPreviewRequest(CommandName.NODE_COMPLETE,
                        Map.of("projectId", 22L, "nodeId", 7L), "pms:project-detail:22:7", "v1"));

        assertThat(preview.command()).isEqualTo(CommandName.NODE_COMPLETE);
        assertThat(preview.changes()).singleElement().satisfies(change -> {
            assertThat(change).containsEntry("action", "complete");
            assertThat(change).containsEntry("projectVersion", 3);
            assertThat(change).containsEntry("nodeVersion", 4);
        });
        assertThat(preview.warnings()).hasSize(1);
    }

    @Test
    void executesRollbackOnlyAfterMatchingThePreviewVersions() {
        ProjectDO project = project(8);
        ProjectNodeDO node = node(7L, 9, "方案设计");
        when(permissionService.requireProjectReadable(22L)).thenReturn(project);
        when(permissionService.requireNode(22L, 7L)).thenReturn(node);
        when(permissionService.requireRollbackableNode(22L, 7L)).thenReturn(node);
        when(nodeService.rollback(eq(22L), eq(7L), eq("重新评审方案"))).thenReturn(List.of(new ProjectNodeDTO()));

        AiOperationDO operation = new AiOperationDO();
        operation.setId("op-rollback-1");
        operation.setArgumentsJson("{\"projectId\":22,\"nodeId\":7,\"reason\":\"重新评审方案\"}");
        operation.setExpectedVersionsJson("[{\"projectVersion\":8,\"nodeVersion\":9}]");

        CommandResult result = new RollbackNodeCommand(nodeService, permissionService, new ObjectMapper()).execute(operation);

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.message()).isEqualTo("节点已回退");
        assertThat(result.data()).containsEntry("reason", "重新评审方案");
        verify(nodeService).rollback(22L, 7L, "重新评审方案");
    }

    private static ProjectDO project(int version) {
        ProjectDO project = new ProjectDO();
        project.setId(22L);
        project.setVersion(version);
        return project;
    }

    private static ProjectNodeDO node(Long id, int version, String name) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(id);
        node.setProjectId(22L);
        node.setVersion(version);
        node.setName(name);
        node.setStatus(1);
        return node;
    }
}
