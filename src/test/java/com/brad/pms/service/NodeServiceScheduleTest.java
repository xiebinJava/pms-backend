package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodeOwnerUpdateCmd;
import com.brad.pms.dto.request.NodeScheduleUpdateCmd;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.mapper.ProjectLifecycleLogMapper;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectMemberMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeServiceScheduleTest {

    @Mock ProjectNodeMapper nodeMapper;
    @Mock ProjectMapper projectMapper;
    @Mock ProjectMemberMapper memberMapper;
    @Mock UserService userService;
    @Mock ProjectPermissionService permissionService;
    @Mock ProjectLifecycleLogMapper lifecycleLogMapper;
    @Mock ProjectTaskMapper taskMapper;
    @Mock NotificationService notificationService;
    @Mock OperationLogService operationLogService;
    @Mock MemberService memberService;

    @InjectMocks NodeService nodeService;

    @org.junit.jupiter.api.BeforeEach
    void stubSuccessfulUpdates() {
        org.mockito.Mockito.lenient()
                .when(nodeMapper.updateById(org.mockito.ArgumentMatchers.any(ProjectNodeDO.class))).thenReturn(1);
    }

    @Test
    void savesInclusiveScheduleAndReturnsIt() {
        ProjectDO project = new ProjectDO();
        project.setId(1L);
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setStatus(1);
        node.setSort(0);
        node.setVersion(2);
        when(permissionService.requireProjectReadable(1L)).thenReturn(project);
        when(permissionService.requireManageableNode(1L, 10L, "编辑节点排期")).thenReturn(node);
        NodeScheduleUpdateCmd cmd = new NodeScheduleUpdateCmd();
        cmd.setStartDate(LocalDate.of(2026, 9, 1));
        cmd.setEndDate(LocalDate.of(2026, 9, 20));
        cmd.setVersion(2);
        when(nodeMapper.updateById(org.mockito.ArgumentMatchers.any(ProjectNodeDO.class))).thenReturn(1);

        var result = nodeService.updateSchedule(1L, 10L, cmd);

        assertThat(result.getStartDate()).isEqualTo(cmd.getStartDate());
        assertThat(result.getEndDate()).isEqualTo(cmd.getEndDate());
        verify(nodeMapper).updateById(node);
        verify(operationLogService).record(org.mockito.ArgumentMatchers.argThat(event ->
                "NODE_SCHEDULE_CHANGED".equals(event.action())
                        && Long.valueOf(1L).equals(event.projectId())
                        && Long.valueOf(10L).equals(event.resourceId())));
    }

    @Test
    void rejectsAnEndDateBeforeTheStartDate() {
        ProjectDO project = new ProjectDO();
        project.setId(1L);
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setStatus(1);
        node.setVersion(2);
        when(permissionService.requireProjectReadable(1L)).thenReturn(project);
        when(permissionService.requireManageableNode(1L, 10L, "编辑节点排期")).thenReturn(node);

        NodeScheduleUpdateCmd cmd = new NodeScheduleUpdateCmd();
        cmd.setStartDate(LocalDate.of(2026, 9, 20));
        cmd.setEndDate(LocalDate.of(2026, 9, 1));
        cmd.setVersion(2);

        assertThatThrownBy(() -> nodeService.updateSchedule(1L, 10L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("开始日期不能晚于结束日期");
    }

    @Test
    void rejectsAStaleVersionWhenUpdatingNodeOwner() {
        ProjectDO project = new ProjectDO();
        project.setId(1L);
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setStatus(1);
        node.setVersion(2);
        when(permissionService.requireProjectManageable(1L, "分配节点负责人")).thenReturn(project);
        when(permissionService.requireNode(1L, 10L)).thenReturn(node);

        NodeOwnerUpdateCmd cmd = new NodeOwnerUpdateCmd();
        cmd.setVersion(1);
        cmd.setOwnerId(8L);

        assertThatThrownBy(() -> nodeService.updateOwner(1L, 10L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("节点已被其他人修改")
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(BusinessException.ResponseCode.CONFLICT);
    }
}
