package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.mapper.ProjectLifecycleLogMapper;
import com.brad.pms.mapper.ProjectFollowerMapper;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectMemberMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeServiceCompletionTest {

    @Mock ProjectNodeMapper nodeMapper;
    @Mock ProjectMapper projectMapper;
    @Mock ProjectMemberMapper memberMapper;
    @Mock UserService userService;
    @Mock ProjectPermissionService permissionService;
    @Mock ProjectLifecycleLogMapper lifecycleLogMapper;
    @Mock ProjectTaskMapper taskMapper;
    @Mock OperationLogService operationLogService;
    @Mock NodeRequirementScopeService requirementScopeService;
    @Mock NodeSolutionDesignService solutionDesignService;
    @Mock NodePlanResourceRiskService planResourceRiskService;
    @Mock NodeAcceptanceService acceptanceService;
    @Mock NodeDevelopmentControlService developmentControlService;
    @Mock NodeReleaseService releaseService;
    @Mock NodeValueReviewService valueReviewService;
    @Mock MemberService memberService;
    @Mock NotificationService notificationService;
    @Mock WorkflowTemplateService workflowTemplateService;
    @Mock WorkflowComponentBindingService workflowComponentBindingService;
    @Mock NodeCustomFieldService nodeCustomFieldService;
    @Mock ProjectFollowerMapper followerMapper;

    @InjectMocks NodeService nodeService;

    @BeforeEach
    void wireOptionalCollaborators() {
        nodeService.setNotificationService(notificationService);
        nodeService.setWorkflowTemplateService(workflowTemplateService);
        nodeService.setWorkflowComponentBindingService(workflowComponentBindingService);
        nodeService.setNodeCustomFieldService(nodeCustomFieldService);
        nodeService.setFollowerMapper(followerMapper);
        org.mockito.Mockito.lenient().when(workflowComponentBindingService.applyTopicBinding(
                any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    void rejectsActiveProjectNodeWithoutACompleteSchedule() {
        ProjectDO project = project();
        ProjectNodeDO node = node(10L);
        node.setOwnerId(7L);
        when(permissionService.requireProject(1L)).thenReturn(project);
        when(permissionService.requireCompletableNode(1L, 10L)).thenReturn(node);

        assertThatThrownBy(() -> nodeService.complete(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先设置节点排期");
    }

    @Test
    void completesActiveProjectNodeWhenOwnerScheduleAndTasksAreReady() {
        ProjectDO project = project();
        ProjectNodeDO node = node(10L);
        node.setOwnerId(7L);
        node.setStartDate(LocalDate.of(2026, 10, 1));
        node.setEndDate(LocalDate.of(2026, 10, 3));
        when(permissionService.requireProject(1L)).thenReturn(project);
        when(permissionService.requireCompletableNode(1L, 10L)).thenReturn(node);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        when(nodeMapper.updateById(any(ProjectNodeDO.class))).thenReturn(1);
        when(nodeMapper.selectOne(any())).thenReturn(null);
        when(nodeMapper.selectList(any())).thenReturn(java.util.List.of());

        nodeService.complete(1L, 10L);

        verify(nodeMapper).updateById(node);
    }

    private ProjectDO project() {
        ProjectDO project = new ProjectDO();
        project.setId(1L);
        project.setStatus(1);
        return project;
    }

    private ProjectNodeDO node(Long id) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(id);
        node.setProjectId(1L);
        node.setNodeKey("develop");
        node.setName("开发测试与项目控制");
        node.setSort(0);
        node.setStatus(1);
        node.setVersion(0);
        return node;
    }
}
