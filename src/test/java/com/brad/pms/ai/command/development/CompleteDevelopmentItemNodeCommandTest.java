package com.brad.pms.ai.command.development;

import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.PmsCommandSupport;
import com.brad.pms.dto.response.DevelopmentItemTaskDTO;
import com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO;
import com.brad.pms.dto.response.DevelopmentItemWorkflowNodeDTO;
import com.brad.pms.service.DevelopmentItemWorkflowService;
import com.brad.pms.workflow.DevelopmentItemType;
import com.brad.pms.workflow.WorkflowFieldDefinition;
import com.brad.pms.workflow.WorkflowFieldType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class CompleteDevelopmentItemNodeCommandTest {

    @Test
    void previewsBlockingOwnerScheduleAndTaskPreconditions() {
        DevelopmentItemWorkflowService workflowService = mock(DevelopmentItemWorkflowService.class);
        PmsCommandSupport support = new PmsCommandSupport(new ObjectMapper().findAndRegisterModules());
        DevelopmentItemCommandSupport itemSupport = new DevelopmentItemCommandSupport(workflowService, support);
        CompleteDevelopmentItemNodeCommand command = new CompleteDevelopmentItemNodeCommand(
                workflowService, itemSupport, support);

        DevelopmentItemWorkflowNodeDTO node = new DevelopmentItemWorkflowNodeDTO();
        node.setId(383L);
        node.setNodeKey("custom-node");
        node.setName("故事写卡");
        node.setStatus(1);
        node.setVersion(4);
        node.setTasks(List.of(task(11L, 0), task(12L, 2)));
        DevelopmentItemWorkflowDetailDTO detail = new DevelopmentItemWorkflowDetailDTO();
        detail.setNodes(List.of(node));
        when(workflowService.detail(DevelopmentItemType.STORY, 34L)).thenReturn(detail);

        CommandPreview preview = command.preview(new CommandPreviewRequest(
                CommandName.DEVELOPMENT_ITEM_NODE_COMPLETE,
                Map.of("itemType", "STORY", "itemId", 34L, "nodeId", 383L),
                "story:34", "4"));

        assertThat(preview.preconditions())
                .extracting(CommandPreview.Precondition::code)
                .containsExactly("NODE_OWNER_REQUIRED", "NODE_SCHEDULE_REQUIRED", "INCOMPLETE_TASKS");
        assertThat(preview.preconditions()).allSatisfy(precondition -> {
            assertThat(precondition.blocking()).isTrue();
            assertThat(precondition.suggestedAction()).isNotBlank();
            assertThat(precondition.message()).isNotBlank();
        });
        assertThat(preview.preconditions().get(2).details()).containsEntry("count", 1);
    }

    @Test
    void keepsPreconditionsEmptyForAReadyNode() {
        DevelopmentItemWorkflowService workflowService = mock(DevelopmentItemWorkflowService.class);
        PmsCommandSupport support = new PmsCommandSupport(new ObjectMapper().findAndRegisterModules());
        DevelopmentItemCommandSupport itemSupport = new DevelopmentItemCommandSupport(workflowService, support);
        CompleteDevelopmentItemNodeCommand command = new CompleteDevelopmentItemNodeCommand(
                workflowService, itemSupport, support);

        DevelopmentItemWorkflowNodeDTO node = new DevelopmentItemWorkflowNodeDTO();
        node.setId(383L);
        node.setNodeKey("custom-node");
        node.setName("故事写卡");
        node.setStatus(1);
        node.setVersion(4);
        node.setOwnerId(2L);
        node.setStartDate(java.time.LocalDate.of(2026, 10, 15));
        node.setEndDate(java.time.LocalDate.of(2026, 10, 22));
        node.setTasks(List.of());
        DevelopmentItemWorkflowDetailDTO detail = new DevelopmentItemWorkflowDetailDTO();
        detail.setNodes(List.of(node));
        when(workflowService.detail(DevelopmentItemType.STORY, 34L)).thenReturn(detail);

        CommandPreview preview = command.preview(new CommandPreviewRequest(
                CommandName.DEVELOPMENT_ITEM_NODE_COMPLETE,
                Map.of("itemType", "STORY", "itemId", 34L, "nodeId", 383L),
                "story:34", "4"));

        assertThat(preview.preconditions()).isEmpty();
    }

    @Test
    void previewsMissingWorkflowFields() {
        DevelopmentItemWorkflowService workflowService = mock(DevelopmentItemWorkflowService.class);
        PmsCommandSupport support = new PmsCommandSupport(new ObjectMapper().findAndRegisterModules());
        DevelopmentItemCommandSupport itemSupport = new DevelopmentItemCommandSupport(workflowService, support);
        CompleteDevelopmentItemNodeCommand command = new CompleteDevelopmentItemNodeCommand(
                workflowService, itemSupport, support);

        DevelopmentItemWorkflowNodeDTO node = new DevelopmentItemWorkflowNodeDTO();
        node.setId(383L);
        node.setVersion(4);
        node.setOwnerId(2L);
        node.setStartDate(java.time.LocalDate.of(2026, 10, 15));
        node.setEndDate(java.time.LocalDate.of(2026, 10, 22));
        node.setFields(List.of(new WorkflowFieldDefinition(
                "goal", "故事目标", WorkflowFieldType.TEXTAREA, true, List.of())));
        node.setFieldValues(Map.of());
        DevelopmentItemWorkflowDetailDTO detail = new DevelopmentItemWorkflowDetailDTO();
        detail.setNodes(List.of(node));
        when(workflowService.detail(DevelopmentItemType.STORY, 34L)).thenReturn(detail);

        CommandPreview preview = command.preview(new CommandPreviewRequest(
                CommandName.DEVELOPMENT_ITEM_NODE_COMPLETE,
                Map.of("itemType", "STORY", "itemId", 34L, "nodeId", 383L),
                "story:34", "4"));

        assertThat(preview.preconditions()).extracting(CommandPreview.Precondition::code)
                .containsExactly("REQUIRED_FIELDS");
        assertThat(preview.preconditions().get(0).details()).containsEntry("fields", List.of("故事目标"));
    }

    @Test
    void olderPreviewConstructionDefaultsPreconditionsToEmpty() {
        CommandPreview preview = new CommandPreview(
                "op-1", CommandName.STORY_CREATE, Instant.EPOCH, "v1",
                List.of(), List.of(), List.of("story-list"));

        assertThat(preview.preconditions()).isEmpty();
    }

    private static DevelopmentItemTaskDTO task(Long id, int status) {
        DevelopmentItemTaskDTO task = new DevelopmentItemTaskDTO();
        task.setId(id);
        task.setStatus(status);
        task.setChildren(List.of());
        return task;
    }
}
