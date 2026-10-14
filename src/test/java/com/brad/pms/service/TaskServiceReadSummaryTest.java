package com.brad.pms.service;

import com.brad.pms.dto.response.NodeTaskSummaryDTO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.mapper.ProjectCommentMapper;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import com.brad.pms.mapper.ProjectNodeRequirementMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.mapper.ProjectTaskRequirementMapper;
import com.brad.pms.mapper.ProjectTaskScheduleHistoryMapper;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Flat, delete-aware task statistics used by the AI readonly workflow context. */
@ExtendWith(MockitoExtension.class)
class TaskServiceReadSummaryTest {

    private static final Long PROJECT_ID = 8L;
    private static final Long NODE_ID = 3001L;

    @Mock ProjectTaskMapper taskMapper;
    @Mock ProjectTaskScheduleHistoryMapper scheduleHistoryMapper;
    @Mock ProjectTaskRequirementMapper taskRequirementMapper;
    @Mock ProjectNodeRequirementMapper requirementMapper;
    @Mock ProjectNodeIterationPlanMapper iterationPlanMapper;
    @Mock ProjectCommentMapper commentMapper;
    @Mock UserService userService;
    @Mock ProjectPermissionService permissionService;
    @Mock TaskAttachmentService attachmentService;
    @Mock NotificationService notificationService;
    @Mock OperationLogService operationLogService;
    @Mock MemberService memberService;
    @Mock IterationPlanSystemService iterationPlanSystemService;

    @InjectMocks TaskService taskService;

    @BeforeEach
    void initMybatisLambdaCaches() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        TableInfoHelper.initTableInfo(assistant, ProjectTaskDO.class);
    }

    @Test
    void summarizesFlatRowsIncludingSubtasksAndSurvivingChildrenOfDeletedParents() {
        ProjectDO project = new ProjectDO();
        project.setId(PROJECT_ID);
        when(permissionService.requireProjectReadable(PROJECT_ID)).thenReturn(project);
        ProjectTaskDO doneRoot = task(1L, null, 2);
        ProjectTaskDO openSubtask = task(2L, 1L, 0);
        ProjectTaskDO doneSubtask = task(3L, 1L, 2);
        when(taskMapper.selectList(any())).thenReturn(List.of(doneRoot, openSubtask, doneSubtask));

        NodeTaskSummaryDTO summary = taskService.summarizeByNode(PROJECT_ID, NODE_ID);

        assertThat(summary).isEqualTo(new NodeTaskSummaryDTO(3, 2, 1));
    }

    @Test
    void summarizeCountsSurvivingChildrenOfDeletedParents() {
        ProjectDO project = new ProjectDO();
        project.setId(PROJECT_ID);
        when(permissionService.requireProjectReadable(PROJECT_ID)).thenReturn(project);
        ProjectTaskDO openRoot = task(1L, null, 0);
        when(taskMapper.selectList(any())).thenReturn(List.of(openRoot));

        NodeTaskSummaryDTO summary = taskService.summarizeByNode(PROJECT_ID, NODE_ID);

        assertThat(summary).isEqualTo(new NodeTaskSummaryDTO(1, 0, 1));
    }

    @Test
    void emptyNodeCountsZeroTasksWithoutTodo() {
        ProjectDO project = new ProjectDO();
        project.setId(PROJECT_ID);
        when(permissionService.requireProjectReadable(PROJECT_ID)).thenReturn(project);
        when(taskMapper.selectList(any())).thenReturn(List.of());

        NodeTaskSummaryDTO summary = taskService.summarizeByNode(PROJECT_ID, NODE_ID);

        assertThat(summary).isEqualTo(new NodeTaskSummaryDTO(0, 0, 0));
    }

    private ProjectTaskDO task(Long id, Long parentId, int status) {
        ProjectTaskDO task = new ProjectTaskDO();
        task.setId(id);
        task.setProjectId(PROJECT_ID);
        task.setNodeId(NODE_ID);
        task.setParentId(parentId);
        task.setTitle("task-" + id);
        task.setStatus(status);
        task.setVersion(0);
        return task;
    }
}
