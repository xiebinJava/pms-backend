package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequirementWorkflowServiceTest {
    private final RequirementMapper requirementMapper = mock(RequirementMapper.class);
    private final WorkflowTemplateService workflowTemplateService = mock(WorkflowTemplateService.class);
    private final DevelopmentItemWorkflowService service = new DevelopmentItemWorkflowService(
            mock(DevelopmentItemWorkflowMapper.class), mock(DevelopmentItemWorkflowNodeMapper.class),
            mock(DevelopmentItemTaskMapper.class), mock(ProjectNodeDevelopmentTopicMapper.class),
            mock(ProjectNodeDevelopmentStoryMapper.class), requirementMapper, mock(ProjectNodeMapper.class),
            mock(ProjectNodeIterationPlanMapper.class), mock(WorkflowTemplateVersionMapper.class),
            mock(ProjectPermissionService.class), mock(UserService.class), workflowTemplateService,
            mock(WorkflowComponentBindingService.class), mock(ProjectMemberAssignmentService.class),
            new ObjectMapper());

    @Test
    void detailSupportsAnIndependentRequirementWithoutAConfiguredTemplate() {
        RequirementDO requirement = requirement(7L);
        when(requirementMapper.selectById(7L)).thenReturn(requirement);
        when(requirementMapper.selectByIdForUpdate(7L)).thenReturn(requirement);
        when(workflowTemplateService.resolveDefaultForProcessType("requirement-management")).thenReturn(null);

        var detail = service.detail(DevelopmentItemType.REQUIREMENT, 7L);

        assertThat(detail.getItemType()).isEqualTo("requirement");
        assertThat(detail.getId()).isEqualTo(7L);
        assertThat(detail.getTitle()).isEqualTo("需求七");
        assertThat(detail.getWorkflowConfigured()).isFalse();
    }

    @Test
    void rejectsProjectContextForAnIndependentRequirementWorkflow() {
        when(requirementMapper.selectByIdForUpdate(7L)).thenReturn(requirement(7L));

        assertThatThrownBy(() -> service.createIfDefaultExists(
                DevelopmentItemType.REQUIREMENT, 7L, 3L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("独立事项范围");
    }

    private static RequirementDO requirement(Long id) {
        RequirementDO requirement = new RequirementDO();
        requirement.setId(id);
        requirement.setTitle("需求七");
        requirement.setStatus("DRAFT");
        requirement.setDeleted(false);
        return requirement;
    }
}
