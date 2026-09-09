package com.brad.pms.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.brad.pms.common.enums.TaskStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.TaskCreateCmd;
import com.brad.pms.dto.request.TaskMoveCmd;
import com.brad.pms.dto.request.TaskUpdateCmd;
import com.brad.pms.dto.response.ProjectTaskDTO;
import com.brad.pms.dto.response.TaskAttachmentDTO;
import com.brad.pms.dto.response.TaskDetailDTO;
import com.brad.pms.dto.response.TaskPermissionsDTO;
import com.brad.pms.entity.ProjectCommentDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeRequirementDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.entity.ProjectTaskRequirementDO;
import com.brad.pms.mapper.ProjectCommentMapper;
import com.brad.pms.mapper.ProjectNodeRequirementMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.mapper.ProjectTaskRequirementMapper;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock ProjectTaskMapper taskMapper;
    @Mock ProjectTaskRequirementMapper taskRequirementMapper;
    @Mock ProjectNodeRequirementMapper requirementMapper;
    @Mock ProjectCommentMapper commentMapper;
    @Mock UserService userService;
    @Mock ProjectPermissionService permissionService;
    @Mock TaskAttachmentService attachmentService;
    @Mock NotificationService notificationService;
    @Mock OperationLogService operationLogService;

    @InjectMocks TaskService taskService;

    @BeforeEach
    void init() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        TableInfoHelper.initTableInfo(assistant, ProjectTaskDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectTaskRequirementDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectNodeRequirementDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectCommentDO.class);
        UserContext.set(new LoginUser(7L, "Alex.Zhang", "张伟", 0, "张伟", "张伟（Alex.Zhang）", 1L));
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void listByProjectHidesSubtasksAndCountsChildren() {
        when(permissionService.requireProject(9L)).thenReturn(openProject());
        when(permissionService.requireNode(eq(9L), eq(3L))).thenReturn(openNode());
        when(permissionService.taskPermissions(any(), any(), any())).thenReturn(new TaskPermissionsDTO());
        when(taskMapper.selectList(any())).thenReturn(List.of(task(1L, null), task(2L, 1L)));
        when(userService.listByIds(any())).thenReturn(List.of());

        List<ProjectTaskDTO> result = taskService.listByProject(9L, 3L);

        assertThat(result).extracting(ProjectTaskDTO::getId).containsExactly(1L);
        assertThat(result.get(0).getSubtaskCount()).isEqualTo(1);
    }

    @Test
    void createRejectsNestedSubtasksAndCrossNodeParents() {
        when(permissionService.requireProject(9L)).thenReturn(openProject());
        when(permissionService.requireManageableNode(9L, 3L, "创建任务")).thenReturn(openNode());
        ProjectTaskDO nested = task(2L, 1L);
        when(taskMapper.selectById(2L)).thenReturn(nested);

        TaskCreateCmd cmd = new TaskCreateCmd();
        cmd.setProjectId(9L);
        cmd.setNodeId(3L);
        cmd.setParentId(2L);
        cmd.setTitle("再拆一层");

        assertThatThrownBy(() -> taskService.create(cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能再拆分");
    }

    @Test
    void createsTaskWithAnOptionalRequirementLink() {
        when(permissionService.requireProject(9L)).thenReturn(openProject());
        when(permissionService.requireManageableNode(9L, 3L, "创建任务")).thenReturn(openNode());
        ProjectNodeRequirementDO requirement = new ProjectNodeRequirementDO();
        requirement.setId(51L);
        requirement.setProjectId(9L);
        requirement.setNodeId(3L);
        requirement.setCode("REQ-001");
        requirement.setStatus(1);
        when(requirementMapper.selectById(51L)).thenReturn(requirement);
        when(taskMapper.insert(any(ProjectTaskDO.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, ProjectTaskDO.class).setId(77L);
            return 1;
        });
        when(permissionService.taskPermissions(any(), any(), any())).thenReturn(new TaskPermissionsDTO());

        TaskCreateCmd cmd = new TaskCreateCmd();
        cmd.setProjectId(9L);
        cmd.setNodeId(3L);
        cmd.setTitle("商品详情页开发");
        cmd.setRequirementId(51L);

        ProjectTaskDTO dto = taskService.create(cmd);

        ArgumentCaptor<ProjectTaskRequirementDO> captor = ArgumentCaptor.forClass(ProjectTaskRequirementDO.class);
        verify(taskRequirementMapper).insert(captor.capture());
        assertThat(captor.getValue().getTaskId()).isEqualTo(77L);
        assertThat(captor.getValue().getRequirementId()).isEqualTo(51L);
        assertThat(dto.getRequirementId()).isEqualTo(51L);
        assertThat(dto.getRequirementCode()).isEqualTo("REQ-001");
    }

    @Test
    void taskContractsDoNotExposeDevelopmentStoryAssociation() {
        assertThat(java.util.Arrays.stream(TaskCreateCmd.class.getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .doesNotContain("developmentStoryId");
        assertThat(java.util.Arrays.stream(ProjectTaskDTO.class.getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .doesNotContain("developmentStoryId", "developmentStoryTitle");
    }

    @Test
    void createRejectsTaskLinkToUnconfirmedRequirement() {
        when(permissionService.requireProject(9L)).thenReturn(openProject());
        when(permissionService.requireManageableNode(9L, 3L, "创建任务")).thenReturn(openNode());
        ProjectNodeRequirementDO requirement = new ProjectNodeRequirementDO();
        requirement.setId(52L);
        requirement.setProjectId(9L);
        requirement.setNodeId(3L);
        requirement.setCode("REQ-002");
        requirement.setStatus(0);
        when(requirementMapper.selectById(52L)).thenReturn(requirement);

        TaskCreateCmd cmd = new TaskCreateCmd();
        cmd.setProjectId(9L);
        cmd.setNodeId(3L);
        cmd.setTitle("未确认需求不应创建任务");
        cmd.setRequirementId(52L);

        assertThatThrownBy(() -> taskService.create(cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("需求必须先确认");
        verify(taskMapper, never()).insert(any(ProjectTaskDO.class));
    }

    @Test
    void createRejectsSubtasksUnderCompletedParents() {
        when(permissionService.requireProject(9L)).thenReturn(openProject());
        when(permissionService.requireManageableNode(9L, 3L, "创建任务")).thenReturn(openNode());
        ProjectTaskDO parent = task(1L, null);
        parent.setStatus(TaskStatus.DONE.getCode());
        when(taskMapper.selectById(1L)).thenReturn(parent);

        TaskCreateCmd cmd = new TaskCreateCmd();
        cmd.setProjectId(9L);
        cmd.setNodeId(3L);
        cmd.setParentId(1L);
        cmd.setTitle("已完成父任务下的新子任务");

        assertThatThrownBy(() -> taskService.create(cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("父任务已完成");
    }

    @Test
    void getDetailAssemblesSubtasksCommentsAndAttachments() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, null));
        when(permissionService.requireProject(9L)).thenReturn(openProject());
        when(permissionService.requireNode(9L, 3L)).thenReturn(openNode());
        when(permissionService.taskPermissions(any(), any(), any())).thenReturn(new TaskPermissionsDTO());
        when(taskMapper.selectList(any())).thenReturn(List.of(task(2L, 1L)));
        when(commentMapper.selectList(any())).thenReturn(List.of());
        when(userService.listByIds(any())).thenReturn(List.of());
        TaskAttachmentDTO attachment = new TaskAttachmentDTO();
        attachment.setId(8L);
        attachment.setOriginalName("设计稿.pdf");
        when(attachmentService.listByTask(1L)).thenReturn(List.of(attachment));

        TaskDetailDTO detail = taskService.getDetail(1L);

        assertThat(detail.getId()).isEqualTo(1L);
        assertThat(detail.getSubtasks()).extracting(ProjectTaskDTO::getId).containsExactly(2L);
        assertThat(detail.getSubtaskCount()).isEqualTo(1);
        assertThat(detail.getAttachments()).extracting(TaskAttachmentDTO::getOriginalName)
                .containsExactly("设计稿.pdf");
        assertThat(detail.getComments()).isEmpty();
    }

    @Test
    void deleteRemovesChildrenAndAttachments() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, null));
        when(permissionService.requireProject(9L)).thenReturn(openProject());
        when(permissionService.requireNode(9L, 3L)).thenReturn(openNode());
        when(taskMapper.selectList(any())).thenReturn(List.of(task(2L, 1L)));

        taskService.delete(1L);

        verify(attachmentService).deleteAllForTask(2L);
        verify(taskMapper).deleteById(2L);
        verify(attachmentService).deleteAllForTask(1L);
        verify(taskMapper).deleteById(1L);
    }

    @Test
    void completingParentCompletesChildrenAndBackfillsOnlyMissingDueDates() {
        ProjectTaskDO parent = task(1L, null);
        ProjectTaskDO missingDateChild = task(2L, 1L);
        ProjectTaskDO datedChild = task(3L, 1L);
        datedChild.setDueDate(LocalDate.of(2026, 9, 8));
        when(taskMapper.selectById(1L)).thenReturn(parent);
        when(permissionService.requireProject(9L)).thenReturn(openProject());
        when(permissionService.requireNode(9L, 3L)).thenReturn(openNode());
        when(taskMapper.selectList(any())).thenReturn(List.of(missingDateChild, datedChild));

        TaskUpdateCmd cmd = new TaskUpdateCmd();
        cmd.setStatus(TaskStatus.DONE.getCode());

        taskService.update(1L, cmd);

        assertThat(missingDateChild.getStatus()).isEqualTo(TaskStatus.DONE.getCode());
        assertThat(missingDateChild.getDueDate()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Shanghai")));
        assertThat(datedChild.getStatus()).isEqualTo(TaskStatus.DONE.getCode());
        assertThat(datedChild.getDueDate()).isEqualTo(LocalDate.of(2026, 9, 8));
        verify(taskMapper, times(3)).updateById(any(ProjectTaskDO.class));
    }

    @Test
    void completedParentRejectsReopeningChild() {
        ProjectTaskDO child = task(2L, 1L);
        ProjectTaskDO parent = task(1L, null);
        parent.setStatus(TaskStatus.DONE.getCode());
        when(taskMapper.selectById(2L)).thenReturn(child);
        when(taskMapper.selectById(1L)).thenReturn(parent);
        when(permissionService.requireProject(9L)).thenReturn(openProject());
        when(permissionService.requireNode(9L, 3L)).thenReturn(openNode());

        TaskUpdateCmd cmd = new TaskUpdateCmd();
        cmd.setStatus(TaskStatus.DOING.getCode());

        assertThatThrownBy(() -> taskService.update(2L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("父任务已完成");
    }

    @Test
    void updateCanExplicitlyClearDueDate() {
        ProjectTaskDO task = task(2L, 1L);
        task.setDueDate(LocalDate.of(2026, 9, 8));
        when(taskMapper.selectById(2L)).thenReturn(task);
        when(permissionService.requireProject(9L)).thenReturn(openProject());
        when(permissionService.requireNode(9L, 3L)).thenReturn(openNode());
        TaskUpdateCmd cmd = new TaskUpdateCmd();
        cmd.setClearDueDate(true);

        taskService.update(2L, cmd);

        assertThat(task.getDueDate()).isNull();
    }

    @Test
    void movingParentToDoneAlsoCompletesChildren() {
        ProjectTaskDO parent = task(1L, null);
        ProjectTaskDO child = task(2L, 1L);
        when(taskMapper.selectById(1L)).thenReturn(parent);
        when(permissionService.requireProject(9L)).thenReturn(openProject());
        when(permissionService.requireNode(9L, 3L)).thenReturn(openNode());
        when(permissionService.taskPermissions(any(), any(), any())).thenReturn(new TaskPermissionsDTO());
        when(taskMapper.selectList(any())).thenReturn(List.of(child));

        TaskMoveCmd cmd = new TaskMoveCmd();
        cmd.setStatus(TaskStatus.DONE.getCode());

        taskService.move(1L, cmd);

        assertThat(child.getStatus()).isEqualTo(TaskStatus.DONE.getCode());
        assertThat(child.getDueDate()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Shanghai")));
        verify(taskMapper, times(2)).updateById(any(ProjectTaskDO.class));
    }

    private static ProjectDO openProject() {
        ProjectDO project = new ProjectDO();
        project.setId(9L);
        project.setStatus(1);
        project.setCreatedBy(7L);
        project.setProjectManagerId(7L);
        return project;
    }

    private static ProjectNodeDO openNode() {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(3L);
        node.setProjectId(9L);
        node.setStatus(1);
        node.setOwnerId(7L);
        return node;
    }

    private static ProjectTaskDO task(Long id, Long parentId) {
        ProjectTaskDO task = new ProjectTaskDO();
        task.setId(id);
        task.setProjectId(9L);
        task.setNodeId(3L);
        task.setParentId(parentId);
        task.setTitle(parentId == null ? "父任务" : "子任务");
        task.setStatus(0);
        task.setPriority(1);
        return task;
    }
}
