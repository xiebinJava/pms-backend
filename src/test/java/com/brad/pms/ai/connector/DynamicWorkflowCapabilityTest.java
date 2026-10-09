package com.brad.pms.ai.connector;

import com.brad.pms.ai.query.AiTaskQueryService;
import com.brad.pms.ai.command.PmsCommandRegistry;
import com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO;
import com.brad.pms.dto.response.DevelopmentItemWorkflowNodeDTO;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.brad.pms.service.DevelopmentItemService;
import com.brad.pms.service.DevelopmentItemWorkflowService;
import com.brad.pms.service.IterationPlanService;
import com.brad.pms.service.ProjectService;
import com.brad.pms.workflow.DevelopmentItemType;
import com.brad.pms.workflow.WorkflowFieldDefinition;
import com.brad.pms.workflow.WorkflowFieldType;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicWorkflowCapabilityTest {

    @Mock ProjectService projectService;
    @Mock DevelopmentItemService developmentItemService;
    @Mock IterationPlanService iterationPlanService;
    @Mock AiTaskQueryService taskQueryService;
    @Mock DevelopmentItemWorkflowService workflowService;
    @Mock PmsCommandRegistry commandRegistry;

    @Test
    void contextUsesTheBoundWorkflowSnapshotAndItsRuntimeComponents() {
        DevelopmentItemWorkflowDetailDTO detail = new DevelopmentItemWorkflowDetailDTO();
        detail.setItemType("TOPIC");
        detail.setId(7L);
        detail.setTitle("订单中心");
        detail.setTemplateVersionId(88L);
        detail.setTemplateVersionNo(2);
        detail.setWorkflowConfigured(true);
        DevelopmentItemWorkflowNodeDTO node = new DevelopmentItemWorkflowNodeDTO();
        node.setId(701L);
        node.setNodeKey("topic-review");
        node.setName("专题评审");
        node.setSort(1);
        node.setStatus(1);
        node.setRuntimeComponents(List.of("story-list", "custom-review"));
        node.setFields(List.of(new WorkflowFieldDefinition(
                "decision", "评审结论", WorkflowFieldType.TEXTAREA, true, List.of(), true,
                "topic-review.decision", true)));
        node.setFieldValues(Map.of("decision", JsonNodeFactory.instance.textNode("继续")));
        detail.setNodes(List.of(node));
        when(workflowService.detail(DevelopmentItemType.TOPIC, 7L)).thenReturn(detail);

        AiWorkflowContextDTO context = service().context("topic", 7L);

        assertThat(context.workflow().templateVersionId()).isEqualTo(88L);
        assertThat(context.workflow().templateVersionNo()).isEqualTo(2);
        assertThat(context.currentNode().key()).isEqualTo("topic-review");
        assertThat(context.workflow().nodes()).singleElement().satisfies(item -> {
            assertThat(item.components()).extracting(AiWorkflowContextDTO.Component::key)
                    .containsExactly("story-list", "custom-review");
            assertThat(item.fields()).extracting(AiWorkflowContextDTO.Field::key)
                    .containsExactly("decision");
            assertThat(item.fieldValues()).containsEntry("decision", "继续");
        });
    }

    private AiConnectorQueryService service() {
        return new AiConnectorQueryService(projectService, developmentItemService,
                iterationPlanService, taskQueryService, workflowService, commandRegistry);
    }
}
