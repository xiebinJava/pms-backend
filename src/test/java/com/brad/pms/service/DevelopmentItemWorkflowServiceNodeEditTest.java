package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.DevelopmentItemNodeUpdateCmd;
import com.brad.pms.dto.request.DevelopmentItemTaskSaveCmd;
import com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO;
import com.brad.pms.entity.DevelopmentItemTaskDO;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import com.brad.pms.entity.DevelopmentItemWorkflowNodeDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.entity.WorkflowTemplateVersionDO;
import com.brad.pms.mapper.DevelopmentItemTaskMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowNodeMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.WorkflowTemplateVersionMapper;
import com.brad.pms.workflow.DevelopmentItemType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DevelopmentItemWorkflowServiceNodeEditTest {

    @Test
    void updatesOwnerAndScheduleOnAnUnstartedNode() {
        Fixture fixture = new Fixture(0);
        DevelopmentItemNodeUpdateCmd cmd = new DevelopmentItemNodeUpdateCmd();
        cmd.setOwnerId(42L);
        cmd.setStartDate(LocalDate.of(2026, 10, 1));
        cmd.setEndDate(LocalDate.of(2026, 10, 3));
        cmd.setVersion(0);

        DevelopmentItemWorkflowDetailDTO result = fixture.service.updateNode(
                DevelopmentItemType.TOPIC, 7L, 21L, cmd);

        assertThat(result.getNodes()).singleElement().satisfies(node -> {
            assertThat(node.getStatus()).isZero();
            assertThat(node.getOwnerId()).isEqualTo(42L);
            assertThat(node.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
            assertThat(node.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 3));
        });
        verify(fixture.nodeMapper).updateById(fixture.node);
    }

    @Test
    void createsTasksOnAnUnstartedNode() {
        Fixture fixture = new Fixture(0);
        DevelopmentItemTaskSaveCmd cmd = new DevelopmentItemTaskSaveCmd();
        cmd.setTitle("准备方案评审材料");
        cmd.setStatus(0);
        cmd.setPriority(1);

        DevelopmentItemWorkflowDetailDTO result = fixture.service.saveTask(
                DevelopmentItemType.TOPIC, 7L, 21L, null, cmd);

        assertThat(fixture.savedTasks).singleElement().satisfies(task -> {
            assertThat(task.getNodeId()).isEqualTo(21L);
            assertThat(task.getTitle()).isEqualTo("准备方案评审材料");
        });
        assertThat(result.getNodes()).singleElement().satisfies(node ->
                assertThat(node.getStatus()).isZero());
    }

    @Test
    void deletesTasksFromAnUnstartedNode() {
        Fixture fixture = new Fixture(0);
        DevelopmentItemTaskDO task = fixture.task(51L);
        fixture.savedTasks.add(task);
        when(fixture.taskMapper.selectById(51L)).thenReturn(task);
        when(fixture.taskMapper.selectList(any())).thenReturn(List.of());
        when(fixture.taskMapper.deleteById(51L)).thenAnswer(invocation -> {
            fixture.savedTasks.remove(task);
            return 1;
        });

        fixture.service.deleteTask(DevelopmentItemType.TOPIC, 7L, 51L);

        assertThat(fixture.savedTasks).isEmpty();
        verify(fixture.taskMapper).deleteById(51L);
    }

    @Test
    void doesNotAllowCompletingAnUnstartedNode() {
        Fixture fixture = new Fixture(0);

        assertThatThrownBy(() -> fixture.service.completeNode(DevelopmentItemType.TOPIC, 7L, 21L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前进行中");
    }

    @Test
    void doesNotAllowEditingACompletedNode() {
        Fixture fixture = new Fixture(2);
        DevelopmentItemNodeUpdateCmd cmd = new DevelopmentItemNodeUpdateCmd();
        cmd.setVersion(0);

        assertThatThrownBy(() -> fixture.service.updateNode(DevelopmentItemType.TOPIC, 7L, 21L, cmd))
                .isInstanceOf(BusinessException.class);
    }

    private static final class Fixture {
        private final DevelopmentItemWorkflowMapper workflowMapper = mock(DevelopmentItemWorkflowMapper.class);
        private final DevelopmentItemWorkflowNodeMapper nodeMapper = mock(DevelopmentItemWorkflowNodeMapper.class);
        private final DevelopmentItemTaskMapper taskMapper = mock(DevelopmentItemTaskMapper.class);
        private final ProjectNodeDevelopmentTopicMapper topicMapper = mock(ProjectNodeDevelopmentTopicMapper.class);
        private final ProjectNodeDevelopmentStoryMapper storyMapper = mock(ProjectNodeDevelopmentStoryMapper.class);
        private final ProjectNodeMapper projectNodeMapper = mock(ProjectNodeMapper.class);
        private final ProjectNodeIterationPlanMapper iterationPlanMapper = mock(ProjectNodeIterationPlanMapper.class);
        private final WorkflowTemplateVersionMapper templateVersionMapper = mock(WorkflowTemplateVersionMapper.class);
        private final ProjectPermissionService permissionService = mock(ProjectPermissionService.class);
        private final UserService userService = mock(UserService.class);
        private final WorkflowTemplateService workflowTemplateService = mock(WorkflowTemplateService.class);
        private final DevelopmentItemWorkflowService service;
        private final DevelopmentItemWorkflowNodeDO node = new DevelopmentItemWorkflowNodeDO();
        private final List<DevelopmentItemTaskDO> savedTasks = new ArrayList<>();

        private Fixture(int status) {
            service = new DevelopmentItemWorkflowService(workflowMapper, nodeMapper, taskMapper, topicMapper,
                    storyMapper, projectNodeMapper, iterationPlanMapper, templateVersionMapper,
                    permissionService, userService, workflowTemplateService, new ObjectMapper());

            ProjectDO project = new ProjectDO();
            project.setId(5L);
            project.setName("项目");
            project.setCode("PRJ-0001");
            ProjectNodeDO sourceNode = new ProjectNodeDO();
            sourceNode.setId(9L);
            sourceNode.setProjectId(5L);
            ProjectNodeDevelopmentTopicDO topic = new ProjectNodeDevelopmentTopicDO();
            topic.setId(7L);
            topic.setTitle("专题");
            topic.setProjectId(5L);
            topic.setNodeId(9L);

            DevelopmentItemWorkflowDO workflow = new DevelopmentItemWorkflowDO();
            workflow.setId(31L);
            workflow.setItemType("TOPIC");
            workflow.setItemId(7L);
            workflow.setProjectId(5L);
            workflow.setSourceNodeId(9L);
            workflow.setTemplateVersionId(88L);

            node.setId(21L);
            node.setWorkflowId(31L);
            node.setNodeKey("design");
            node.setName("方案设计与评审");
            node.setSort(1);
            node.setStatus(status);
            node.setVersion(0);

            when(permissionService.requireProjectReadable(5L)).thenReturn(project);
            when(topicMapper.selectById(7L)).thenReturn(topic);
            when(topicMapper.selectByIdForUpdate(7L)).thenReturn(topic);
            when(storyMapper.selectList(any())).thenReturn(List.of());
            when(projectNodeMapper.selectById(9L)).thenReturn(sourceNode);
            when(workflowMapper.selectByItem("TOPIC", 7L)).thenReturn(workflow);
            when(workflowMapper.selectForUpdate("TOPIC", 7L)).thenReturn(workflow);
            when(nodeMapper.selectById(21L)).thenReturn(node);
            when(nodeMapper.updateById(any(DevelopmentItemWorkflowNodeDO.class))).thenReturn(1);
            when(nodeMapper.selectList(any())).thenReturn(List.of(node));
            when(taskMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(savedTasks));
            when(taskMapper.insert(any(DevelopmentItemTaskDO.class))).thenAnswer(invocation -> {
                DevelopmentItemTaskDO task = invocation.getArgument(0);
                task.setId(51L);
                savedTasks.add(task);
                return 1;
            });
            when(templateVersionMapper.selectById(88L)).thenReturn(new WorkflowTemplateVersionDO());
            when(userService.listByIds(anyList())).thenAnswer(invocation -> {
                List<Long> ids = invocation.getArgument(0);
                if (!ids.contains(42L)) return List.of();
                UserDO owner = new UserDO();
                owner.setId(42L);
                owner.setNickname("负责人");
                return List.of(owner);
            });
        }

        private DevelopmentItemTaskDO task(Long id) {
            DevelopmentItemTaskDO task = new DevelopmentItemTaskDO();
            task.setId(id);
            task.setWorkflowId(31L);
            task.setNodeId(21L);
            task.setTitle("待删除任务");
            task.setStatus(0);
            task.setPriority(1);
            task.setVersion(0);
            return task;
        }
    }
}
