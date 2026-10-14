package com.brad.pms.ai.connector;

import com.brad.pms.ai.query.AiTaskQueryResult;
import com.brad.pms.common.page.PageResult;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.response.DevelopmentTopicListDTO;
import com.brad.pms.dto.response.IterationPlanListDTO;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.dto.response.RequirementListDTO;
import com.brad.pms.integration.ai.api.AiQueryRequest;
import com.brad.pms.integration.ai.api.AiQueryResultDTO;
import com.brad.pms.integration.ai.api.AiResourceDetailDTO;
import com.brad.pms.service.DevelopmentItemService;
import com.brad.pms.service.DevelopmentItemWorkflowService;
import com.brad.pms.service.IterationPlanService;
import com.brad.pms.service.ProjectService;
import com.brad.pms.service.RequirementManagementService;
import com.brad.pms.service.WorkflowTemplateService;
import com.brad.pms.ai.query.AiTaskQueryService;
import com.brad.pms.ai.command.PmsCommandRegistry;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.brad.pms.workflow.DevelopmentItemType;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import com.brad.pms.workflow.WorkflowTemplateDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiConnectorQueryServiceTest {

    @Mock ProjectService projectService;
    @Mock DevelopmentItemService developmentItemService;
    @Mock IterationPlanService iterationPlanService;
    @Mock AiTaskQueryService taskQueryService;
    @Mock DevelopmentItemWorkflowService workflowService;
    @Mock PmsCommandRegistry commandRegistry;
    @Mock RequirementManagementService requirementManagementService;
    @Mock WorkflowTemplateService workflowTemplateService;

    @Test
    void queriesResourcesThroughExistingPermissionAwareServices() {
        DevelopmentTopicListDTO topic = new DevelopmentTopicListDTO();
        topic.setId(7L);
        topic.setTitle("订单中心");
        topic.setStatus("IN_PROGRESS");
        topic.setOwnerName("张伟");
        when(developmentItemService.pageTopics(any())).thenReturn(PageResult.of(1, 1, 20, List.of(topic)));

        AiQueryResultDTO result = service().query(new AiQueryRequest(
                "topic", "订单", Map.of("projectId", 78L), 1, 20));

        assertThat(result.resourceType()).isEqualTo("topic");
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(7L);
            assertThat(item.name()).isEqualTo("订单中心");
            assertThat(item.summary()).containsEntry("ownerName", "张伟");
        });
    }

    @Test
    void mapsProjectAndIterationPlanQueriesWithoutCreatingAWorkflowContract() {
        ProjectDTO project = new ProjectDTO();
        project.setId(78L);
        project.setVersion(3);
        project.setName("订单中心");
        when(projectService.page(any())).thenReturn(PageResult.of(1, 1, 20, List.of(project)));
        IterationPlanListDTO plan = new IterationPlanListDTO();
        plan.setId(9L);
        plan.setName("第一迭代");
        plan.setProgress(40);
        when(iterationPlanService.page(any())).thenReturn(PageResult.of(1, 1, 20, List.of(plan)));

        AiConnectorQueryService queryService = service();
        AiQueryResultDTO projectResult = queryService.query(new AiQueryRequest(
                "project", null, Map.of(), 1, 20));
        AiQueryResultDTO planResult = queryService.query(new AiQueryRequest(
                "iteration_plan", null, Map.of("projectId", 78L), 1, 20));

        assertThat(projectResult.items()).singleElement().satisfies(item -> {
            assertThat(item.version()).isEqualTo(3);
            assertThat(item.summary()).containsEntry("name", "订单中心");
        });
        assertThat(planResult.items()).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("第一迭代");
            assertThat(item.summary()).containsEntry("progress", 40);
        });
    }

    @Test
    void taskQueryRequiresAProjectScopeAndUnknownResourcesAreRejected() {
        when(taskQueryService.query(any())).thenReturn(new AiTaskQueryResult(
                "task-query", true, "Asia/Shanghai", LocalDate.now(), 0, 1, 20, 0,
                new AiTaskQueryResult.Pagination(1, 20, 0), List.of()));

        assertThat(service().query(new AiQueryRequest(
                "task", null, Map.of("projectId", 78L), 1, 20)).total()).isZero();
        assertThatThrownBy(() -> service().query(new AiQueryRequest(
                "unknown", null, Map.of(), 1, 20)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持");
    }

    @Test
    void projectContextPreservesTemplateNodePositions() {
        ProjectDTO project = new ProjectDTO();
        project.setId(78L);
        project.setVersion(3);
        project.setWorkflowTemplateVersionId(88L);
        project.setCurrentNodeKey("delivery");
        project.setCurrentNodeName("交付");
        when(projectService.detail(78L)).thenReturn(project);
        when(workflowTemplateService.getDefinition(88L)).thenReturn(new WorkflowTemplateDefinition(2, List.of(
                projectNode("intake", "接收"), projectNode("delivery", "交付"))));
        when(workflowTemplateService.getVersionNo(88L)).thenReturn(4);

        AiWorkflowContextDTO context = service().context("project", 78L);

        assertThat(context.workflow().nodes()).extracting(AiWorkflowContextDTO.Node::position)
                .containsExactly(0, 1);
        assertThat(context.workflow().nodes()).extracting(AiWorkflowContextDTO.Node::status)
                .containsExactly(0, 1);
    }

    private WorkflowNodeDefinition projectNode(String key, String name) {
        return new WorkflowNodeDefinition(key, name, null, null, null, List.of(), List.of(), false, List.of());
    }

    @Test
    void topicAndStoryResourcesReadThroughThePureEntryWithoutInitializingWorkflows() {
        com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO topic = new com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO();
        topic.setId(7L);
        topic.setTitle("订单流程专题");
        topic.setProjectId(78L);
        topic.setWorkflowConfigured(true);
        when(workflowService.detailIfInitialized(DevelopmentItemType.TOPIC, 7L)).thenReturn(topic);

        AiResourceDetailDTO resource = service().resource("topic", 7L);

        assertThat(resource.resourceType()).isEqualTo("topic");
        assertThat(resource.resourceId()).isEqualTo(7L);
        assertThat(resource.details()).containsEntry("title", "订单流程专题");
        verify(workflowService, never()).detail(any(), any());
    }

    @Test
    void storyContextUsesThePureDetailEntryToo() {
        com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO story = new com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO();
        story.setId(9L);
        story.setTitle("故事");
        story.setWorkflowConfigured(true);
        com.brad.pms.dto.response.DevelopmentItemWorkflowNodeDTO node = new com.brad.pms.dto.response.DevelopmentItemWorkflowNodeDTO();
        node.setId(21L);
        node.setNodeKey("design");
        node.setName("设计");
        node.setStatus(1);
        node.setVersion(2);
        story.setNodes(List.of(node));
        when(workflowService.detailIfInitialized(DevelopmentItemType.STORY, 9L)).thenReturn(story);
        when(commandRegistry.list()).thenReturn(java.util.Set.of());

        AiWorkflowContextDTO context = service().context("story", 9L);

        assertThat(context.resourceType()).isEqualTo("story");
        assertThat(context.currentNode()).isNotNull();
        assertThat(context.currentNode().id()).isEqualTo(21L);
        verify(workflowService, never()).detail(any(), any());
    }

    private AiConnectorQueryService service() {
        return new AiConnectorQueryService(projectService, developmentItemService,
                iterationPlanService, taskQueryService, workflowService, commandRegistry,
                requirementManagementService, workflowTemplateService);
    }
}
