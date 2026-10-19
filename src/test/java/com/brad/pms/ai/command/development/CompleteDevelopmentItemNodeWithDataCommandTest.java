package com.brad.pms.ai.command.development;

import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.PmsCommandSupport;
import com.brad.pms.dto.request.DevelopmentItemNodeUpdateCmd;
import com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO;
import com.brad.pms.dto.response.DevelopmentItemWorkflowNodeDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.service.DevelopmentItemWorkflowService;
import com.brad.pms.workflow.DevelopmentItemType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;

class CompleteDevelopmentItemNodeWithDataCommandTest {

    @Test
    void previewsTheEffectiveDetailsThatWillBeSavedBeforeCompletion() {
        DevelopmentItemWorkflowService workflowService = mock(DevelopmentItemWorkflowService.class);
        PmsCommandSupport support = new PmsCommandSupport(new ObjectMapper().findAndRegisterModules());
        DevelopmentItemCommandSupport itemSupport = new DevelopmentItemCommandSupport(workflowService, support);
        CompleteDevelopmentItemNodeWithDataCommand command = new CompleteDevelopmentItemNodeWithDataCommand(
                workflowService, itemSupport, support);

        DevelopmentItemWorkflowNodeDTO node = new DevelopmentItemWorkflowNodeDTO();
        node.setId(383L);
        node.setNodeKey("custom-node");
        node.setVersion(4);
        node.setTasks(List.of());
        node.setFieldValues(Map.of());
        DevelopmentItemWorkflowDetailDTO detail = new DevelopmentItemWorkflowDetailDTO();
        detail.setNodes(List.of(node));
        when(workflowService.detail(DevelopmentItemType.STORY, 34L)).thenReturn(detail);

        CommandPreview preview = command.preview(new CommandPreviewRequest(
                CommandName.DEVELOPMENT_ITEM_NODE_COMPLETE_WITH_DATA,
                Map.of(
                        "itemType", "STORY", "itemId", 34L, "nodeId", 383L, "version", 4,
                        "ownerId", 2L, "startDate", "2026-10-10", "endDate", "2026-10-20",
                        "fieldValues", Map.of("businessResult", "已完成")),
                "story:34", "4"));

        assertThat(preview.preconditions()).isEmpty();
        assertThat(preview.changes().get(0))
                .containsEntry("action", "complete-with-data")
                .containsEntry("ownerId", 2L)
                .containsEntry("startDate", LocalDate.of(2026, 10, 10))
                .containsEntry("endDate", LocalDate.of(2026, 10, 20))
                .containsEntry("completionMode", "AUTO_CONFIRM")
                .containsEntry("allowedMutations", List.of("status", "ownerId", "startDate", "endDate"));
        assertThat(preview.changes().get(0)).containsKey("fieldValues");
    }

    @Test
    void requiresTheCurrentNodeVersion() {
        DevelopmentItemWorkflowService workflowService = mock(DevelopmentItemWorkflowService.class);
        PmsCommandSupport support = new PmsCommandSupport(new ObjectMapper().findAndRegisterModules());
        DevelopmentItemCommandSupport itemSupport = new DevelopmentItemCommandSupport(workflowService, support);
        CompleteDevelopmentItemNodeWithDataCommand command = new CompleteDevelopmentItemNodeWithDataCommand(
                workflowService, itemSupport, support);

        DevelopmentItemWorkflowNodeDTO node = new DevelopmentItemWorkflowNodeDTO();
        node.setId(383L);
        node.setVersion(4);
        DevelopmentItemWorkflowDetailDTO detail = new DevelopmentItemWorkflowDetailDTO();
        detail.setNodes(List.of(node));
        when(workflowService.detail(DevelopmentItemType.STORY, 34L)).thenReturn(detail);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> command.preview(new CommandPreviewRequest(
                CommandName.DEVELOPMENT_ITEM_NODE_COMPLETE_WITH_DATA,
                Map.of("itemType", "STORY", "itemId", 34L, "nodeId", 383L, "version", 3),
                "story:34", "4")))
                .hasMessageContaining("流程节点已被其他人修改");
    }

    @Test
    void savesDetailsBeforeAdvancingTheNode() throws Exception {
        DevelopmentItemWorkflowService workflowService = mock(DevelopmentItemWorkflowService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        PmsCommandSupport support = new PmsCommandSupport(objectMapper);
        DevelopmentItemCommandSupport itemSupport = new DevelopmentItemCommandSupport(workflowService, support);
        CompleteDevelopmentItemNodeWithDataCommand command = new CompleteDevelopmentItemNodeWithDataCommand(
                workflowService, itemSupport, support);
        DevelopmentItemWorkflowNodeDTO node = new DevelopmentItemWorkflowNodeDTO();
        node.setId(383L);
        node.setVersion(4);
        node.setOwnerId(1L);
        node.setStartDate(LocalDate.of(2026, 10, 1));
        node.setEndDate(LocalDate.of(2026, 10, 9));
        node.setFieldValues(Map.of());
        node.setTasks(List.of());
        DevelopmentItemWorkflowDetailDTO initial = new DevelopmentItemWorkflowDetailDTO();
        initial.setNodes(List.of(node));
        when(workflowService.detail(DevelopmentItemType.STORY, 34L)).thenReturn(initial);
        DevelopmentItemWorkflowDetailDTO completed = new DevelopmentItemWorkflowDetailDTO();
        when(workflowService.completeNode(DevelopmentItemType.STORY, 34L, 383L)).thenReturn(completed);

        AiOperationDO operation = new AiOperationDO();
        operation.setId("op-1");
        operation.setArgumentsJson(objectMapper.writeValueAsString(Map.of(
                "itemType", "STORY", "itemId", 34L, "nodeId", 383L, "version", 4,
                "ownerId", 2L, "startDate", "2026-10-10", "endDate", "2026-10-20",
                "fieldValues", Map.of("businessResult", "已完成"))));

        var result = command.execute(operation);

        assertThat(result.data().get("allowedMutations"))
                .isEqualTo(List.of("status", "ownerId", "startDate", "endDate"));

        verify(workflowService).updateNode(org.mockito.ArgumentMatchers.eq(DevelopmentItemType.STORY),
                org.mockito.ArgumentMatchers.eq(34L), org.mockito.ArgumentMatchers.eq(383L),
                org.mockito.ArgumentMatchers.argThat(actual -> actual.getOwnerId().equals(2L)
                        && actual.getStartDate().equals(LocalDate.of(2026, 10, 10))
                        && actual.getEndDate().equals(LocalDate.of(2026, 10, 20))
                        && actual.getVersion().equals(4)
                        && actual.getFieldValues().containsKey("businessResult")));
        verify(workflowService, times(1)).completeNode(DevelopmentItemType.STORY, 34L, 383L);
    }

    @Test
    void propagatesCompletionFailureAfterTheDetailUpdateForTransactionRollback() throws Exception {
        DevelopmentItemWorkflowService workflowService = mock(DevelopmentItemWorkflowService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        PmsCommandSupport support = new PmsCommandSupport(objectMapper);
        DevelopmentItemCommandSupport itemSupport = new DevelopmentItemCommandSupport(workflowService, support);
        CompleteDevelopmentItemNodeWithDataCommand command = new CompleteDevelopmentItemNodeWithDataCommand(
                workflowService, itemSupport, support);
        DevelopmentItemWorkflowNodeDTO node = new DevelopmentItemWorkflowNodeDTO();
        node.setId(383L);
        node.setVersion(4);
        node.setOwnerId(1L);
        node.setStartDate(LocalDate.of(2026, 10, 1));
        node.setEndDate(LocalDate.of(2026, 10, 9));
        node.setFieldValues(Map.of());
        DevelopmentItemWorkflowDetailDTO initial = new DevelopmentItemWorkflowDetailDTO();
        initial.setNodes(List.of(node));
        when(workflowService.detail(DevelopmentItemType.STORY, 34L)).thenReturn(initial);
        doThrow(new IllegalStateException("completion failed"))
                .when(workflowService).completeNode(DevelopmentItemType.STORY, 34L, 383L);

        AiOperationDO operation = new AiOperationDO();
        operation.setId("op-failing-completion");
        operation.setArgumentsJson(objectMapper.writeValueAsString(Map.of(
                "itemType", "STORY", "itemId", 34L, "nodeId", 383L, "version", 4,
                "ownerId", 2L, "startDate", "2026-10-10", "endDate", "2026-10-20",
                "fieldValues", Map.of("businessResult", "已完成"))));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> command.execute(operation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("completion failed");
        verify(workflowService).updateNode(org.mockito.ArgumentMatchers.eq(DevelopmentItemType.STORY),
                org.mockito.ArgumentMatchers.eq(34L), org.mockito.ArgumentMatchers.eq(383L),
                org.mockito.ArgumentMatchers.any(DevelopmentItemNodeUpdateCmd.class));
        verify(workflowService, times(1)).completeNode(DevelopmentItemType.STORY, 34L, 383L);
    }
}
