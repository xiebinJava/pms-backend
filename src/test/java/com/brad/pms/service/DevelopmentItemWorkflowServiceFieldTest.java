package com.brad.pms.service;

import com.brad.pms.dto.response.DevelopmentItemWorkflowNodeDTO;
import com.brad.pms.entity.DevelopmentItemWorkflowNodeDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.DevelopmentItemTaskMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowNodeMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.WorkflowTemplateVersionMapper;
import com.brad.pms.workflow.WorkflowFieldDefinition;
import com.brad.pms.workflow.WorkflowFieldType;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DevelopmentItemWorkflowServiceFieldTest {
    private final WorkflowTemplateService workflowTemplateService = mock(WorkflowTemplateService.class);
    private final DevelopmentItemWorkflowService service = new DevelopmentItemWorkflowService(
            mock(DevelopmentItemWorkflowMapper.class), mock(DevelopmentItemWorkflowNodeMapper.class),
            mock(DevelopmentItemTaskMapper.class), mock(ProjectNodeDevelopmentTopicMapper.class),
            mock(ProjectNodeDevelopmentStoryMapper.class), mock(ProjectNodeMapper.class),
            mock(ProjectNodeIterationPlanMapper.class), mock(WorkflowTemplateVersionMapper.class),
            mock(ProjectPermissionService.class), mock(UserService.class), workflowTemplateService,
            mock(ProjectMemberAssignmentService.class), new ObjectMapper());

    @Test
    void nodeDtoIncludesTemplateFieldDefinitionsAndSavedValues() {
        WorkflowFieldDefinition field = new WorkflowFieldDefinition(
                "researchSummary", "调研结论", WorkflowFieldType.TEXTAREA, true, List.of());
        WorkflowNodeDefinition definition = new WorkflowNodeDefinition("research", "需求调研", "", "", "",
                List.of(), List.of(field), false, List.of());
        DevelopmentItemWorkflowNodeDO node = new DevelopmentItemWorkflowNodeDO();
        node.setId(21L);
        node.setNodeKey("research");
        node.setName("需求调研");
        node.setSort(0);
        node.setStatus(1);
        node.setOwnerId(9L);
        node.setVersion(3);
        node.setFieldValuesJson("{\"researchSummary\":\"已记录调研结论\"}");
        when(workflowTemplateService.getNodeDefinition(88L, "research")).thenReturn(definition);

        DevelopmentItemWorkflowNodeDTO result = ReflectionTestUtils.invokeMethod(
                service, "toNodeDTO", node, List.of(), Map.of(9L, new UserDO()), 88L);

        assertThat(result.getFields()).containsExactly(field);
        assertThat(result.getFieldValues()).containsEntry("researchSummary",
                new ObjectMapper().getNodeFactory().textNode("已记录调研结论"));
    }
}
