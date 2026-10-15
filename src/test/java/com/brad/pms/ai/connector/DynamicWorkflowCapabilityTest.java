package com.brad.pms.ai.connector;

import com.brad.pms.ai.query.AiTaskQueryService;
import com.brad.pms.ai.command.PmsCommandRegistry;
import com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO;
import com.brad.pms.dto.response.DevelopmentItemWorkflowNodeDTO;
import com.brad.pms.ai.connector.context.ContextReadOptions;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.brad.pms.service.DevelopmentItemService;
import com.brad.pms.service.DevelopmentItemWorkflowService;
import com.brad.pms.service.IterationPlanService;
import com.brad.pms.service.ProjectService;
import com.brad.pms.service.RequirementManagementService;
import com.brad.pms.service.WorkflowTemplateService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicWorkflowCapabilityTest {

    @Mock ProjectService projectService;
    @Mock DevelopmentItemService developmentItemService;
    @Mock IterationPlanService iterationPlanService;
    @Mock AiTaskQueryService taskQueryService;
    @Mock DevelopmentItemWorkflowService workflowService;
    @Mock PmsCommandRegistry commandRegistry;
    @Mock RequirementManagementService requirementManagementService;
    @Mock WorkflowTemplateService workflowTemplateService;

    @Test
    void contextDelegatesWithNormalizedTypeAndPreservesTheWorkflowSnapshot() {
        AiWorkflowContextDTO contract = new AiWorkflowContextDTO("topic", 7L, 9,
                new AiWorkflowContextDTO.CurrentNode(701L, "topic-review", "专题评审"),
                new AiWorkflowContextDTO.Workflow(88L, 2, List.of(
                        new AiWorkflowContextDTO.Node(701L, "topic-review", "专题评审", 0, 1,
                                List.of(new AiWorkflowContextDTO.Component("story-list", "story-list",
                                        List.of(), List.of()),
                                        new AiWorkflowContextDTO.Component("custom-review", "custom-review",
                                                List.of(), List.of())),
                                List.of(), Map.of("decision", "继续")))),
                List.of(), null, null, null, null);
        when(readService.read("topic", 7L, ContextReadOptions.legacy(), null)).thenReturn(contract);

        AiWorkflowContextDTO context = service().context("topic", 7L);

        assertThat(context.workflow().templateVersionId()).isEqualTo(88L);
        assertThat(context.workflow().templateVersionNo()).isEqualTo(2);
        assertThat(context.version()).isEqualTo(9);
        assertThat(context.currentNode().key()).isEqualTo("topic-review");
        assertThat(context.workflow().nodes()).singleElement().satisfies(item -> {
            assertThat(item.components()).extracting(AiWorkflowContextDTO.Component::key)
                    .containsExactly("story-list", "custom-review");
            assertThat(item.fieldValues()).containsEntry("decision", "继续");
        });
        verify(readService).read("topic", 7L, ContextReadOptions.legacy(), null);
    }

    @org.mockito.Mock com.brad.pms.ai.connector.context.AiWorkflowContextReadService readService;
    @org.mockito.Mock com.brad.pms.ai.connector.context.AiContextReadPolicy contextReadPolicy;
    @org.mockito.Mock com.brad.pms.ai.connector.context.AiContextReferenceProjector referenceProjector;

    private AiConnectorQueryService service() {
        return new AiConnectorQueryService(projectService, developmentItemService,
                iterationPlanService, taskQueryService, workflowService, commandRegistry,
                requirementManagementService, workflowTemplateService, readService,
                contextReadPolicy, referenceProjector);
    }
}
