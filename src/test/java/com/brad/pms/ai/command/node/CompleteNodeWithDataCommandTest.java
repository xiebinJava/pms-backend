package com.brad.pms.ai.command.node;

import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.node.NodeWorkbench;
import com.brad.pms.ai.node.NodeWorkbenchRegistry;
import com.brad.pms.ai.node.WorkbenchSnapshot;
import com.brad.pms.dto.response.ProjectNodeDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.service.NodeService;
import com.brad.pms.service.ProjectPermissionService;
import com.brad.pms.service.WorkflowTemplateService;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompleteNodeWithDataCommandTest {

    @Mock NodeService nodeService;
    @Mock ProjectPermissionService permissionService;
    @Mock WorkflowTemplateService workflowTemplateService;
    @Mock NodeWorkbenchRegistry workbenchRegistry;
    @Mock NodeWorkbench workbench;

    @Test
    void previewsWorkbenchChangesAndCompletionAgainstAllVersions() {
        ProjectDO project = project(3);
        ProjectNodeDO node = node(7L, 4);
        when(permissionService.requireProjectManageable(22L, "编辑节点内容")).thenReturn(project);
        when(permissionService.requireCompletableNode(22L, 7L)).thenReturn(node);
        when(workbenchRegistry.require("solution-design")).thenReturn(workbench);
        when(workbench.key()).thenReturn("solution-design");
        when(workbench.label()).thenReturn("方案设计");
        when(workbench.dynamicFields()).thenReturn(false);
        when(workbench.read(22L, 7L)).thenReturn(new WorkbenchSnapshot(
                Map.of("productSolution", "旧方案"), Map.of("productSolution", "产品方案"),
                9, Map.of("productSolution", 2)));
        when(workflowTemplateService.getNodeDefinition(3L, "node-key"))
                .thenReturn(definition("solution-design"));

        CommandPreview preview = command().preview(new CommandPreviewRequest(
                CommandName.NODE_COMPLETE_WITH_DATA,
                Map.of("projectId", 22L, "nodeId", 7L, "workbench", "solution-design",
                        "fields", Map.of("productSolution", "新方案")),
                "project:22:node:7", "v1"));

        assertThat(preview.command()).isEqualTo(CommandName.NODE_COMPLETE_WITH_DATA);
        assertThat(preview.changes()).singleElement().satisfies(change -> {
            assertThat(change).containsEntry("action", "complete-with-data");
            assertThat(change).containsEntry("projectVersion", 3);
            assertThat(change).containsEntry("nodeVersion", 4);
            assertThat(change).containsEntry("workbenchVersion", 9);
            assertThat(change).containsEntry("completionMode", "REQUIRES_DOMAIN_CONFIRMATION");
            assertThat(change.get("allowedMutations"))
                    .isEqualTo(List.of("status", "ownerId", "startDate", "endDate"));
            assertThat(change.get("fieldVersions")).isEqualTo(Map.of("productSolution", 2));
        });
    }

    @Test
    void executesWorkbenchWriteBeforeCompletingTheNode() {
        ProjectDO project = project(3);
        ProjectNodeDO node = node(7L, 4);
        when(permissionService.requireProjectManageable(22L, "编辑节点内容")).thenReturn(project);
        when(permissionService.requireNode(22L, 7L)).thenReturn(node);
        when(permissionService.requireCompletableNode(22L, 7L)).thenReturn(node);
        when(workbenchRegistry.require("solution-design")).thenReturn(workbench);
        when(workbench.key()).thenReturn("solution-design");
        when(workbench.read(22L, 7L)).thenReturn(new WorkbenchSnapshot(
                Map.of("productSolution", "旧方案"), Map.of("productSolution", "产品方案"),
                9, Map.of("productSolution", 2)));
        when(workflowTemplateService.getNodeDefinition(3L, "node-key"))
                .thenReturn(definition("solution-design"));
        when(workbench.write(22L, 7L, Map.of("productSolution", "新方案"))).thenReturn(Map.of("productSolution", "新方案"));
        when(nodeService.complete(eq(22L), eq(7L))).thenReturn(List.of(new ProjectNodeDTO()));

        AiOperationDO operation = new AiOperationDO();
        operation.setId("op-project-complete");
        operation.setArgumentsJson("{\"projectId\":22,\"nodeId\":7,\"workbench\":\"solution-design\",\"fields\":{\"productSolution\":\"新方案\"}}");
        operation.setExpectedVersionsJson("[{\"projectVersion\":3,\"nodeVersion\":4,\"workbenchVersion\":9,\"fieldVersions\":{\"productSolution\":2}}]");

        CommandResult result = command().execute(operation);

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.data().get("allowedMutations"))
                .isEqualTo(List.of("status", "ownerId", "startDate", "endDate"));
        var order = inOrder(workbench, nodeService);
        order.verify(workbench).write(22L, 7L, Map.of("productSolution", "新方案"));
        order.verify(workbench).confirmForCompletion(22L, 7L);
        order.verify(nodeService).complete(22L, 7L);
    }

    @Test
    void previewsRequirementScopeDomainBlockersBeforeExecute() {
        ProjectDO project = project(3);
        ProjectNodeDO node = node(7L, 4);
        when(permissionService.requireProjectManageable(22L, "编辑节点内容")).thenReturn(project);
        when(permissionService.requireCompletableNode(22L, 7L)).thenReturn(node);
        when(workbenchRegistry.require("requirement-scope")).thenReturn(workbench);
        when(workbench.key()).thenReturn("requirement-scope");
        when(workbench.label()).thenReturn("需求范围");
        when(workbench.dynamicFields()).thenReturn(false);
        when(workbench.read(22L, 7L)).thenReturn(new WorkbenchSnapshot(
                Map.of("scopeItems", List.of(Map.of("direction", "IN", "title", "范围")),
                        "requirements", List.of()),
                Map.of("scopeItems", "范围项", "requirements", "需求清单"),
                1, Map.of()));
        when(workflowTemplateService.getNodeDefinition(3L, "node-key"))
                .thenReturn(definition("requirement-scope"));

        CommandPreview preview = command().preview(new CommandPreviewRequest(
                CommandName.NODE_COMPLETE_WITH_DATA,
                Map.of("projectId", 22L, "nodeId", 7L, "workbench", "requirement-scope",
                        "fields", Map.of("scopeItems", List.of(Map.of("direction", "IN", "title", "范围"))),
                        "ownerId", 1L, "startDate", "2026-10-10", "endDate", "2026-10-20"),
                "project:22:node:7", "v1"));

        assertThat(preview.preconditions())
                .extracting(CommandPreview.Precondition::code)
                .contains("REQUIREMENT_SCOPE_REQUIREMENTS_REQUIRED");
    }

    private CompleteNodeWithDataCommand command() {
        return new CompleteNodeWithDataCommand(nodeService, permissionService, workbenchRegistry,
                workflowTemplateService, new ObjectMapper());
    }

    private static ProjectDO project(int version) {
        ProjectDO project = new ProjectDO();
        project.setId(22L);
        project.setVersion(version);
        project.setWorkflowTemplateVersionId(3L);
        return project;
    }

    private static ProjectNodeDO node(Long id, int version) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(id);
        node.setProjectId(22L);
        node.setVersion(version);
        node.setNodeKey("node-key");
        node.setName("方案设计");
        node.setStatus(1);
        return node;
    }

    private static WorkflowNodeDefinition definition(String component) {
        return new WorkflowNodeDefinition("node-key", "方案设计", null, null, null,
                List.of(component), List.of(), false, List.of());
    }
}
