package com.brad.pms.service;

import com.brad.pms.common.enums.ProjectStatus;
import com.brad.pms.common.enums.RequirementExecutionTargetType;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.RequirementExecutionTargetCmd;
import com.brad.pms.dto.request.RequirementExecutionTargetOptionQry;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.RequirementDO;
import com.brad.pms.entity.RequirementExecutionTargetHistoryDO;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.mapper.RequirementExecutionTargetHistoryMapper;
import com.brad.pms.mapper.RequirementMapper;
import com.brad.pms.workflow.DevelopmentItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequirementExecutionTargetServiceTest {
    @Mock RequirementMapper requirementMapper;
    @Mock RequirementExecutionTargetHistoryMapper historyMapper;
    @Mock ProjectMapper projectMapper;
    @Mock ProjectNodeDevelopmentTopicMapper topicMapper;
    @Mock ProjectNodeDevelopmentStoryMapper storyMapper;
    @Mock ProjectPermissionService permissionService;
    @Mock UserService userService;
    @Mock OperationLogService operationLogService;
    @InjectMocks RequirementExecutionTargetService service;

    @Test
    void linksOneActiveProjectAndPersistsTheTargetHistoryInTheSameServiceCall() {
        RequirementDO requirement = requirement(21L, 0);
        ProjectDO project = project(78L, ProjectStatus.ACTIVE.getCode());
        when(requirementMapper.selectByIdForUpdate(21L)).thenReturn(requirement);
        when(projectMapper.selectIncludingDeleted(78L)).thenReturn(project);
        when(permissionService.requireProjectReadable(78L)).thenReturn(project);
        when(requirementMapper.updateById(requirement)).thenReturn(1);
        when(historyMapper.insert(any(RequirementExecutionTargetHistoryDO.class))).thenReturn(1);

        RequirementExecutionTargetCmd cmd = target(RequirementExecutionTargetType.PROJECT, 78L, 0, null);
        var result = service.link(21L, cmd);

        assertThat(result.getTargetType()).isEqualTo(RequirementExecutionTargetType.PROJECT);
        assertThat(result.getTargetId()).isEqualTo(78L);
        assertThat(requirement.getExecutionTargetType()).isEqualTo(RequirementExecutionTargetType.PROJECT);
        assertThat(requirement.getExecutionTargetId()).isEqualTo(78L);
        ArgumentCaptor<com.brad.pms.entity.RequirementExecutionTargetHistoryDO> history =
                ArgumentCaptor.forClass(com.brad.pms.entity.RequirementExecutionTargetHistoryDO.class);
        verify(historyMapper).insert(history.capture());
        assertThat(history.getValue().getAction()).isEqualTo("LINK");
        assertThat(history.getValue().getPreviousTargetId()).isNull();
    }

    @Test
    void rejectsACompletedProjectBeforeChangingTheRequirement() {
        RequirementDO requirement = requirement(22L, 0);
        when(requirementMapper.selectByIdForUpdate(22L)).thenReturn(requirement);
        when(projectMapper.selectIncludingDeleted(78L)).thenReturn(project(78L, ProjectStatus.COMPLETED.getCode()));

        assertThatThrownBy(() -> service.link(22L,
                target(RequirementExecutionTargetType.PROJECT, 78L, 0, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("进行中");
        verify(requirementMapper, never()).updateById(any(RequirementDO.class));
        verify(historyMapper, never()).insert(any(RequirementExecutionTargetHistoryDO.class));
    }

    @Test
    void rejectsASecondTargetAndRequiresReasonForChangingIt() {
        RequirementDO requirement = requirement(23L, 2);
        requirement.setExecutionTargetType(RequirementExecutionTargetType.TOPIC);
        requirement.setExecutionTargetId(9L);
        when(requirementMapper.selectByIdForUpdate(23L)).thenReturn(requirement);

        assertThatThrownBy(() -> service.link(23L,
                target(RequirementExecutionTargetType.STORY, 10L, 2, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有执行对象");
        assertThatThrownBy(() -> service.change(23L,
                target(RequirementExecutionTargetType.STORY, 10L, 2, "")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("原因");
    }

    @Test
    void rejectsAStaleVersionBeforeTargetValidationOrMutation() {
        RequirementDO requirement = requirement(24L, 3);
        when(requirementMapper.selectByIdForUpdate(24L)).thenReturn(requirement);

        assertThatThrownBy(() -> service.link(24L,
                target(RequirementExecutionTargetType.PROJECT, 78L, 2, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被其他人修改");
        verify(projectMapper, never()).selectIncludingDeleted(any());
    }

    @Test
    void allowsUnlinkingWhenThePreviouslyLinkedProjectIsNoLongerOpen() {
        RequirementDO requirement = requirement(25L, 1);
        requirement.setExecutionTargetType(RequirementExecutionTargetType.PROJECT);
        requirement.setExecutionTargetId(78L);
        when(requirementMapper.selectByIdForUpdate(25L)).thenReturn(requirement);
        when(projectMapper.selectIncludingDeleted(78L)).thenReturn(project(78L, ProjectStatus.COMPLETED.getCode()));
        when(requirementMapper.updateById(requirement)).thenReturn(1);
        when(historyMapper.insert(any(RequirementExecutionTargetHistoryDO.class))).thenReturn(1);

        service.unlink(25L, 1, "不再执行");

        assertThat(requirement.getExecutionTargetType()).isNull();
        assertThat(requirement.getExecutionTargetId()).isNull();
    }

    @Test
    void targetOptionsMustBelongToAnExistingActiveRequirement() {
        when(requirementMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.options(99L, new RequirementExecutionTargetOptionQry()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("需求不存在");
    }

    private static RequirementDO requirement(Long id, int version) {
        RequirementDO requirement = new RequirementDO();
        requirement.setId(id);
        requirement.setTitle("需求" + id);
        requirement.setStatus("ACTIVE");
        requirement.setDeleted(false);
        requirement.setVersion(version);
        return requirement;
    }

    private static ProjectDO project(Long id, int status) {
        ProjectDO project = new ProjectDO();
        project.setId(id);
        project.setCode("PRJ-" + id);
        project.setName("项目" + id);
        project.setStatus(status);
        project.setDeleted(status == ProjectStatus.DELETED.getCode());
        return project;
    }

    private static RequirementExecutionTargetCmd target(RequirementExecutionTargetType type, Long id,
                                                        Integer version, String reason) {
        RequirementExecutionTargetCmd cmd = new RequirementExecutionTargetCmd();
        cmd.setTargetType(type);
        cmd.setTargetId(id);
        cmd.setRequirementVersion(version);
        cmd.setReason(reason);
        return cmd;
    }
}
