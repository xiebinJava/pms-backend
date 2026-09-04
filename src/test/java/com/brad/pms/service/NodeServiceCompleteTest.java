package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeServiceCompleteTest {

    @Mock ProjectNodeMapper nodeMapper;
    @Mock ProjectMapper projectMapper;
    @Mock ProjectMemberMapper memberMapper;
    @Mock UserService userService;
    @Mock ProjectPermissionService permissionService;
    @Mock ProjectLifecycleLogMapper lifecycleLogMapper;
    @Mock ProjectTaskMapper taskMapper;
    @Mock NotificationService notificationService;
    @Mock OperationLogService operationLogService;
    @Mock NodeSolutionDesignService solutionDesignService;

    @InjectMocks NodeService nodeService;

    @Test
    void rejectsCompleteWhenTheNodeStillHasUnfinishedTasks() {
        ProjectDO project = new ProjectDO();
        project.setId(1L);
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setNodeKey("develop");
        node.setOwnerId(3L);
        node.setStatus(1);
        when(permissionService.requireProject(1L)).thenReturn(project);
        when(permissionService.requireCompletableNode(1L, 10L)).thenReturn(node);
        when(taskMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> nodeService.complete(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未完成任务");
        verify(nodeMapper, never()).updateById(node);
    }

    @Test
    void requiresAConfirmedSolutionDecisionBeforeCompletingDesignNode() {
        ProjectDO project = new ProjectDO();
        project.setId(1L);
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setNodeKey("design");
        node.setOwnerId(3L);
        node.setStatus(1);
        when(permissionService.requireProject(1L)).thenReturn(project);
        when(permissionService.requireCompletableNode(1L, 10L)).thenReturn(node);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        org.mockito.Mockito.doThrow(BusinessException.error("请先确认方案决策"))
                .when(solutionDesignService).requireConfirmed(1L, 10L);

        assertThatThrownBy(() -> nodeService.complete(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("方案决策");
        verify(nodeMapper, never()).updateById(node);
    }
}
