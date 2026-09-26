package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.RequirementPageQry;
import com.brad.pms.dto.request.RequirementSaveCmd;
import com.brad.pms.entity.RequirementDO;
import com.brad.pms.mapper.RequirementMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequirementManagementServiceTest {
    @Mock RequirementMapper requirementMapper;
    @Mock DevelopmentItemWorkflowService workflowService;
    @Mock RequirementExecutionTargetReadService targetReadService;
    @Mock UserService userService;
    @Mock OperationLogService operationLogService;
    @InjectMocks RequirementManagementService service;

    @Test
    void createsAnUnassociatedRequirementEvenWhenNoWorkflowDefaultExists() {
        RequirementSaveCmd cmd = new RequirementSaveCmd();
        cmd.setTitle("统一需求");
        cmd.setDescription("先记录，再决定执行对象");
        cmd.setPriority(2);
        cmd.setOwnerId(9L);
        doAnswer(invocation -> {
            RequirementDO saved = invocation.getArgument(0);
            saved.setId(11L);
            saved.setVersion(0);
            return 1;
        }).when(requirementMapper).insert(any(RequirementDO.class));
        when(workflowService.createIfDefaultExists(
                com.brad.pms.workflow.DevelopmentItemType.REQUIREMENT, 11L, null, null)).thenReturn(null);

        Long id = service.create(cmd);

        assertThat(id).isEqualTo(11L);
        ArgumentCaptor<RequirementDO> saved = ArgumentCaptor.forClass(RequirementDO.class);
        verify(requirementMapper).insert(saved.capture());
        assertThat(saved.getValue().getExecutionTargetType()).isNull();
        assertThat(saved.getValue().getExecutionTargetId()).isNull();
    }

    @Test
    void listsUnassociatedRequirementsAsNormalRows() {
        RequirementDO requirement = requirement(12L, 3);
        when(requirementMapper.selectList(any())).thenReturn(List.of(requirement));

        var result = service.page(new RequirementPageQry());

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().get(0).getExecutionTarget()).isNull();
    }

    @Test
    void rejectsAnUpdateWithAStaleRequirementVersion() {
        RequirementDO requirement = requirement(13L, 4);
        when(requirementMapper.selectByIdForUpdate(13L)).thenReturn(requirement);
        RequirementSaveCmd cmd = new RequirementSaveCmd();
        cmd.setTitle("修改");
        cmd.setVersion(3);

        assertThatThrownBy(() -> service.update(13L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被其他人修改");
        verify(requirementMapper, never()).updateById(any(RequirementDO.class));
    }

    @Test
    void softDeletesAndRestoresTheRequirementWithoutChangingItsExecutionTarget() {
        RequirementDO requirement = requirement(14L, 0);
        when(requirementMapper.selectByIdForUpdate(14L)).thenReturn(requirement);
        when(requirementMapper.updateById(any(RequirementDO.class))).thenReturn(1);

        service.delete(14L);
        assertThat(requirement.getDeleted()).isTrue();
        service.restore(14L);
        assertThat(requirement.getDeleted()).isFalse();
        assertThat(requirement.getExecutionTargetId()).isNull();
    }

    @Test
    void allowsEditingBasicFieldsWhileRetainingAnInactiveOwner() {
        RequirementDO requirement = requirement(15L, 0);
        requirement.setOwnerId(99L);
        when(requirementMapper.selectByIdForUpdate(15L)).thenReturn(requirement);
        when(requirementMapper.updateById(requirement)).thenReturn(1);

        RequirementSaveCmd cmd = new RequirementSaveCmd();
        cmd.setTitle("更新标题");
        cmd.setVersion(0);
        cmd.setOwnerId(99L);

        service.update(15L, cmd);

        verify(userService, never()).requireActiveUser(99L);
        assertThat(requirement.getTitle()).isEqualTo("更新标题");
    }

    private static RequirementDO requirement(Long id, int version) {
        RequirementDO requirement = new RequirementDO();
        requirement.setId(id);
        requirement.setTitle("需求" + id);
        requirement.setPriority(1);
        requirement.setStatus("ACTIVE");
        requirement.setDeleted(false);
        requirement.setVersion(version);
        return requirement;
    }
}
