package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.DevelopmentItemTaskDO;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import com.brad.pms.entity.DevelopmentItemWorkflowNodeDO;
import com.brad.pms.entity.RequirementDO;
import com.brad.pms.mapper.DevelopmentItemTaskMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowNodeMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.RequirementMapper;
import com.brad.pms.mapper.WorkflowTemplateVersionMapper;
import com.brad.pms.workflow.DevelopmentItemType;
import com.brad.pms.workflow.WorkflowComponentKey;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import com.brad.pms.workflow.WorkflowTemplateDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DevelopmentItemWorkflowServiceReceivingCompletionTest {
    @Test
    void completionRequiresTheReceivingAnalysisComponentToBeFilled() {
        RequirementMapper requirementMapper = mock(RequirementMapper.class);
        DevelopmentItemWorkflowMapper workflowMapper = mock(DevelopmentItemWorkflowMapper.class);
        DevelopmentItemWorkflowNodeMapper nodeMapper = mock(DevelopmentItemWorkflowNodeMapper.class);
        DevelopmentItemTaskMapper taskMapper = mock(DevelopmentItemTaskMapper.class);
        WorkflowTemplateService templateService = mock(WorkflowTemplateService.class);

        RequirementDO requirement = new RequirementDO();
        requirement.setId(11L);
        requirement.setTitle("需求");
        requirement.setStatus("ACTIVE");
        requirement.setDeleted(false);
        DevelopmentItemWorkflowDO workflow = new DevelopmentItemWorkflowDO();
        workflow.setId(101L);
        workflow.setItemType("REQUIREMENT");
        workflow.setItemId(11L);
        workflow.setTemplateVersionId(201L);
        DevelopmentItemWorkflowNodeDO node = new DevelopmentItemWorkflowNodeDO();
        node.setId(301L);
        node.setWorkflowId(101L);
        node.setNodeKey("receive");
        node.setStatus(1);
        node.setOwnerId(9L);
        node.setStartDate(LocalDate.of(2026, 9, 28));
        node.setEndDate(LocalDate.of(2026, 9, 29));
        node.setVersion(0);
        WorkflowNodeDefinition nodeDefinition = new WorkflowNodeDefinition("receive", "需求接收", "", "", "",
                List.of(), List.of(), false, List.of(), List.of("component:" + WorkflowComponentKey.REQUIREMENT_RECEIVING_ANALYSIS),
                Map.of(WorkflowComponentKey.REQUIREMENT_RECEIVING_ANALYSIS, new ObjectMapper().createObjectNode()));

        when(requirementMapper.selectById(11L)).thenReturn(requirement);
        when(requirementMapper.selectByIdForUpdate(11L)).thenReturn(requirement);
        when(workflowMapper.selectByItem("REQUIREMENT", 11L)).thenReturn(workflow);
        when(workflowMapper.selectForUpdate("REQUIREMENT", 11L)).thenReturn(workflow);
        when(nodeMapper.selectByWorkflowIdsForUpdate(any())).thenReturn(List.of(node));
        when(nodeMapper.selectList(any())).thenReturn(List.of(node));
        when(taskMapper.selectList(any())).thenReturn(List.of());
        when(taskMapper.selectCount(any())).thenReturn(0L);
        when(nodeMapper.updateById(any(DevelopmentItemWorkflowNodeDO.class))).thenReturn(1);
        when(templateService.getNodeDefinition(201L, "receive")).thenReturn(nodeDefinition);
        when(templateService.getDefinition(201L)).thenReturn(new WorkflowTemplateDefinition(2, List.of(nodeDefinition)));

        DevelopmentItemWorkflowService service = new DevelopmentItemWorkflowService(
                workflowMapper, nodeMapper, taskMapper, mock(ProjectNodeDevelopmentTopicMapper.class),
                mock(ProjectNodeDevelopmentStoryMapper.class), requirementMapper, mock(ProjectNodeMapper.class),
                mock(ProjectNodeIterationPlanMapper.class), mock(WorkflowTemplateVersionMapper.class),
                mock(ProjectPermissionService.class), mock(UserService.class), templateService,
                mock(WorkflowComponentBindingService.class), mock(ProjectMemberAssignmentService.class), new ObjectMapper());

        assertThatThrownBy(() -> service.completeNode(DevelopmentItemType.REQUIREMENT, 11L, 301L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("需求接收分析数据不能为空");
    }
}
