package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.response.RequirementListDTO;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import com.brad.pms.entity.DevelopmentItemWorkflowNodeDO;
import com.brad.pms.entity.RequirementDO;
import com.brad.pms.mapper.DevelopmentItemWorkflowMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowNodeMapper;
import com.brad.pms.mapper.RequirementMapper;
import com.brad.pms.security.AuthorizationService;
import com.brad.pms.security.DataScopeResolver;
import com.brad.pms.mapper.OrgUnitMapper;
import com.brad.pms.workflow.DevelopmentItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** detailForContext reads the actual instance summary; the current default template never blocks reads. */
@ExtendWith(MockitoExtension.class)
class RequirementManagementContextReadTest {

    @Mock RequirementMapper requirementMapper;
    @Mock DevelopmentItemWorkflowService workflowService;
    @Mock RequirementExecutionTargetReadService targetReadService;
    @Mock UserService userService;
    @Mock OperationLogService operationLogService;
    @Mock DevelopmentItemWorkflowMapper workflowMapper;
    @Mock DevelopmentItemWorkflowNodeMapper workflowNodeMapper;
    @Mock WorkflowTemplateService workflowTemplateService;
    @Mock OrgUnitMapper orgUnitMapper;
    @Mock RequirementSystemReferenceService requirementSystemReferenceService;
    @Mock com.brad.pms.security.AuthorizationService authorizationService;
    @Mock com.brad.pms.security.DataScopeResolver dataScopeResolver;

    @InjectMocks RequirementManagementService requirementManagementService;

    @Test
    void detailForContextKeepsRealObjectFactsWhenNoInstanceAndNoDefaultTemplateExist() {
        when(requirementMapper.selectById(23L)).thenReturn(requirement(23L, "ACTIVE"));
        when(targetReadService.findCurrentTarget(any(RequirementDO.class))).thenReturn(null);

        RequirementListDTO detail = requirementManagementService.detailForContext(23L);

        assertThat(detail.getId()).isEqualTo(23L);
        assertThat(detail.getTitle()).isEqualTo("需求");
        assertThat(detail.getWorkflowConfigured()).isFalse();
        assertThat(detail.getWorkflowStatus()).isEqualTo("NOT_CONFIGURED");
        assertThat(detail.getVersion()).isZero();
        verify(workflowTemplateService, never()).resolveDefaultForProcessType(any());
    }

    @Test
    void detailForContextReadsTheActualInstanceWithoutParsingTheDefaultTemplate() {
        when(requirementMapper.selectById(23L)).thenReturn(requirement(23L, "ACTIVE"));
        DevelopmentItemWorkflowDO workflow = workflow(31L, 23L);
        workflow.setVersion(5);
        when(workflowMapper.selectByItem("REQUIREMENT", 23L)).thenReturn(workflow);
        DevelopmentItemWorkflowNodeDO active = node(101L, 1);
        DevelopmentItemWorkflowNodeDO done = node(102L, 2);
        when(workflowNodeMapper.selectList(any())).thenReturn(List.of(active, done));
        when(targetReadService.findCurrentTarget(any(RequirementDO.class))).thenReturn(null);

        RequirementListDTO detail = requirementManagementService.detailForContext(23L);

        assertThat(detail.getWorkflowConfigured()).isTrue();
        assertThat(detail.getWorkflowStatus()).isEqualTo("IN_PROGRESS");
        assertThat(detail.getWorkflowProgress()).isEqualTo(50);
        verify(workflowTemplateService, never()).resolveDefaultForProcessType(any());
        verify(workflowMapper, never()).selectList(any());
    }

    @Test
    void detailForContextReflectsTheRejectedTerminalState() {
        when(requirementMapper.selectById(23L)).thenReturn(requirement(23L, "REJECTED"));
        when(targetReadService.findCurrentTarget(any(RequirementDO.class))).thenReturn(null);

        RequirementListDTO detail = requirementManagementService.detailForContext(23L);

        assertThat(detail.getStatus()).isEqualTo("REJECTED");
        assertThat(detail.getTerminalStatus()).isEqualTo("REJECTED");
        assertThat(detail.getWorkflowConfigured()).isFalse();
    }

    private static RequirementDO requirement(Long id, String status) {
        RequirementDO requirement = new RequirementDO();
        requirement.setId(id);
        requirement.setTitle("需求");
        requirement.setDescription("");
        requirement.setPriority(1);
        requirement.setStatus(status);
        requirement.setDeleted(false);
        requirement.setVersion(0);
        return requirement;
    }

    private static DevelopmentItemWorkflowDO workflow(Long id, Long itemId) {
        DevelopmentItemWorkflowDO workflow = new DevelopmentItemWorkflowDO();
        workflow.setId(id);
        workflow.setItemType("REQUIREMENT");
        workflow.setItemId(itemId);
        workflow.setVersion(0);
        return workflow;
    }

    private static DevelopmentItemWorkflowNodeDO node(Long id, int status) {
        DevelopmentItemWorkflowNodeDO node = new DevelopmentItemWorkflowNodeDO();
        node.setId(id);
        node.setWorkflowId(31L);
        node.setStatus(status);
        return node;
    }
}
