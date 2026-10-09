package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.*;
import com.brad.pms.mapper.*;
import com.brad.pms.workflow.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class DevelopmentItemWorkflowServiceDesignReviewTest {
    @Test void rejectedReviewDoesNotMutateNodeOrAdvanceWorkflow() {
        var topics = mock(ProjectNodeDevelopmentTopicMapper.class);
        var stories = mock(ProjectNodeDevelopmentStoryMapper.class);
        var workflows = mock(DevelopmentItemWorkflowMapper.class);
        var nodes = mock(DevelopmentItemWorkflowNodeMapper.class);
        var templates = mock(WorkflowTemplateService.class);
        var topic = new ProjectNodeDevelopmentTopicDO(); topic.setId(11L); topic.setTitle("专题"); topic.setDeleted(false);
        when(topics.selectById(11L)).thenReturn(topic);
        when(topics.selectByIdForUpdate(11L)).thenReturn(topic);
        when(stories.selectList(any())).thenReturn(List.of());
        var workflow = new DevelopmentItemWorkflowDO(); workflow.setId(13L); workflow.setItemId(11L);
        workflow.setItemType("TOPIC"); workflow.setTemplateVersionId(25L);
        when(workflows.selectByItem("TOPIC", 11L)).thenReturn(workflow);
        when(workflows.selectForUpdate("TOPIC", 11L)).thenReturn(workflow);
        var node = new DevelopmentItemWorkflowNodeDO(); node.setId(80L); node.setWorkflowId(13L); node.setNodeKey("review");
        node.setStatus(1); node.setOwnerId(1L); node.setStartDate(LocalDate.of(2026,9,30)); node.setEndDate(LocalDate.of(2026,10,1));
        node.setFieldValuesJson("{\"__components\":{\"topic-design-review\":{\"productPlanUrl\":\"https://example.com/plan\",\"productReviewerIds\":[1],\"productReviewStatus\":\"REJECTED\"}}}");
        when(nodes.selectByWorkflowIdsForUpdate(any())).thenReturn(List.of(node));
        when(nodes.selectList(any())).thenReturn(List.of(node));
        var definition = new WorkflowNodeDefinition("review", "方案设计与评审", "", "", "", List.of("topic-design-review"), List.of(), false, List.of());
        when(templates.getNodeDefinition(25L,"review")).thenReturn(definition);
        var service = new DevelopmentItemWorkflowService(workflows, nodes, mock(DevelopmentItemTaskMapper.class),topics, stories,
                mock(RequirementMapper.class),mock(ProjectNodeMapper.class),mock(ProjectNodeIterationPlanMapper.class),mock(WorkflowTemplateVersionMapper.class),
                mock(ProjectPermissionService.class),mock(UserService.class),templates,mock(WorkflowComponentBindingService.class),
                mock(ProjectMemberAssignmentService.class),new ObjectMapper());
        assertThatThrownBy(() -> service.completeNode(DevelopmentItemType.TOPIC,11L,80L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("产品评审");
        verify(nodes,never()).updateById(any(DevelopmentItemWorkflowNodeDO.class));
        verify(workflows,never()).updateById(any(DevelopmentItemWorkflowDO.class));
    }
}
