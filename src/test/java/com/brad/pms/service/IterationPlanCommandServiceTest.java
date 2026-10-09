package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.IterationPlanStatusUpdateCmd;
import com.brad.pms.dto.request.NodeIterationPlanCmd;
import com.brad.pms.entity.ProjectNodeIterationPlanDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IterationPlanCommandServiceTest {

    @Mock ProjectNodeIterationPlanMapper iterationPlanMapper;
    @Mock ProjectNodeDevelopmentStoryMapper storyMapper;
    @Mock ProjectNodeMapper nodeMapper;
    @Mock ProjectTaskMapper taskMapper;
    @Mock ProjectPermissionService permissionService;
    @Mock UserService userService;
    @Mock SystemVersionReferenceService systemVersionReferenceService;
    @Mock IterationPlanSystemService iterationPlanSystemService;
    @InjectMocks IterationPlanCommandService service;

    @ParameterizedTest
    @ValueSource(strings = {"PLANNED", "IN_PROGRESS", "DONE", "PAUSED"})
    void updatesOnlyTheIterationStatusForAllSupportedValues(String status) {
        ProjectNodeIterationPlanDO plan = plan();
        when(iterationPlanMapper.selectByIdForUpdate(21L)).thenReturn(plan);
        when(iterationPlanMapper.updateStatus(21L, status)).thenReturn(1);

        IterationPlanStatusUpdateCmd cmd = new IterationPlanStatusUpdateCmd();
        cmd.setStatus(status);

        service.updateStatus(21L, cmd);

        verify(permissionService).requireProjectManageable(9L, "更新迭代计划状态");
        verify(iterationPlanMapper).updateStatus(21L, status);
    }

    @Test
    void rejectsUnsupportedIterationStatusWithoutUpdatingThePlan() {
        ProjectNodeIterationPlanDO plan = plan();
        when(iterationPlanMapper.selectByIdForUpdate(21L)).thenReturn(plan);

        IterationPlanStatusUpdateCmd cmd = new IterationPlanStatusUpdateCmd();
        cmd.setStatus("CANCELLED");

        assertThatThrownBy(() -> service.updateStatus(21L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("迭代计划状态不合法");
        verify(iterationPlanMapper, never()).updateStatus(21L, "CANCELLED");
    }

    @Test
    void rejectsMissingStatusAsABusinessError() {
        when(iterationPlanMapper.selectByIdForUpdate(21L)).thenReturn(plan());
        assertThatThrownBy(() -> service.updateStatus(21L, new IterationPlanStatusUpdateCmd()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("迭代计划状态不合法");
    }

    @Test
    void createsIterationPlanWithSystemDerivedFromProjectRequirementChain() {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(31L);
        node.setProjectId(9L);
        when(nodeMapper.selectById(31L)).thenReturn(node);
        when(iterationPlanSystemService.resolveForSave(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(true))).thenReturn(88L);
        when(iterationPlanMapper.insert(org.mockito.ArgumentMatchers.<ProjectNodeIterationPlanDO>any())).thenAnswer(invocation -> {
            ProjectNodeIterationPlanDO inserted = invocation.getArgument(0);
            inserted.setId(101L);
            return 1;
        });

        NodeIterationPlanCmd cmd = new NodeIterationPlanCmd();
        cmd.setName("一期迭代");

        Long id = service.create(9L, 31L, cmd);

        assertThat(id).isEqualTo(101L);
        verify(iterationPlanMapper).insert(org.mockito.ArgumentMatchers.<ProjectNodeIterationPlanDO>argThat(item ->
                Long.valueOf(88L).equals(item.getSystemId())
                        && Long.valueOf(9L).equals(item.getProjectId())
                        && Long.valueOf(31L).equals(item.getNodeId())));
    }

    @Test
    void createsFromProjectByResolvingTheCurrentIterationWorkbenchNode() {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(41L);
        node.setProjectId(9L);
        when(permissionService.findNodeWithComponent(9L, "plan-resource-risk")).thenReturn(node);
        when(nodeMapper.selectById(41L)).thenReturn(node);
        when(iterationPlanMapper.insert(org.mockito.ArgumentMatchers.<ProjectNodeIterationPlanDO>any())).thenAnswer(invocation -> {
            ProjectNodeIterationPlanDO inserted = invocation.getArgument(0);
            inserted.setId(111L);
            return 1;
        });

        NodeIterationPlanCmd cmd = new NodeIterationPlanCmd();
        cmd.setName("全局创建的迭代");

        Long id = service.createForProject(9L, cmd);

        assertThat(id).isEqualTo(111L);
        verify(permissionService).findNodeWithComponent(9L, "plan-resource-risk");
        verify(iterationPlanMapper).insert(org.mockito.ArgumentMatchers.<ProjectNodeIterationPlanDO>argThat(item ->
                Long.valueOf(41L).equals(item.getNodeId())));
    }

    @Test
    void createsAnUnboundIterationPlanWithoutAProjectOrWorkflowNode() {
        when(iterationPlanMapper.insert(org.mockito.ArgumentMatchers.<ProjectNodeIterationPlanDO>any())).thenAnswer(invocation -> {
            ProjectNodeIterationPlanDO inserted = invocation.getArgument(0);
            inserted.setId(121L);
            return 1;
        });

        NodeIterationPlanCmd cmd = new NodeIterationPlanCmd();
        cmd.setName("独立迭代");

        Long id = service.create(null, null, cmd);

        assertThat(id).isEqualTo(121L);
        verify(iterationPlanMapper).insert(org.mockito.ArgumentMatchers.<ProjectNodeIterationPlanDO>argThat(item ->
                item.getProjectId() == null && item.getNodeId() == null));
    }

    @Test
    void storesManuallySelectedSystemOnAnIndependentIteration() throws Exception {
        NodeIterationPlanCmd cmd = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue("{\"name\":\"独立迭代\",\"systemId\":88}", NodeIterationPlanCmd.class);
        when(iterationPlanMapper.insert(org.mockito.ArgumentMatchers.<ProjectNodeIterationPlanDO>any())).thenAnswer(call -> {
            ProjectNodeIterationPlanDO row = call.getArgument(0);
            row.setId(121L);
            return 1;
        });
        when(iterationPlanSystemService.resolveForSave(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(cmd), org.mockito.ArgumentMatchers.eq(true))).thenReturn(88L);

        service.create(null, null, cmd);

        verify(iterationPlanMapper).insert(org.mockito.ArgumentMatchers.<ProjectNodeIterationPlanDO>argThat(row ->
                Long.valueOf(88L).equals(row.getSystemId())));
    }

    @Test
    void deletesAnUnreferencedIterationPlan() {
        ProjectNodeIterationPlanDO plan = plan();
        when(iterationPlanMapper.selectByIdForUpdate(21L)).thenReturn(plan);
        when(storyMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(taskMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(iterationPlanMapper.deleteById(21L)).thenReturn(1);

        service.delete(21L);

        verify(permissionService).requireProjectManageable(9L, "删除迭代计划");
        verify(iterationPlanMapper).deleteById(21L);
    }

    @Test
    void refusesToDeleteAnIterationPlanReferencedByStoriesOrTasks() {
        ProjectNodeIterationPlanDO plan = plan();
        when(iterationPlanMapper.selectByIdForUpdate(21L)).thenReturn(plan);
        when(storyMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(21L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被故事或任务使用");
        verify(iterationPlanMapper, never()).deleteById(21L);
    }

    @Test
    void associatesAStoryWithAnIndependentIterationWithoutChangingItsProject() {
        ProjectNodeIterationPlanDO plan = plan();
        plan.setProjectId(null);
        plan.setSystemId(88L);
        ProjectNodeDevelopmentStoryDO story = new ProjectNodeDevelopmentStoryDO();
        story.setId(30L);
        story.setProjectId(9L);
        when(iterationPlanMapper.selectByIdForUpdate(21L)).thenReturn(plan);
        when(storyMapper.selectByIdForUpdate(30L)).thenReturn(story);
        when(storyMapper.updateById(story)).thenReturn(1);

        service.addStory(21L, 30L);

        assertThat(story.getIterationPlanId()).isEqualTo(21L);
        assertThat(story.getProjectId()).isEqualTo(9L);
        assertThat(plan.getProjectId()).isNull();
        verify(permissionService).requireProjectManageable(9L, "关联故事到迭代计划");
    }

    private static ProjectNodeIterationPlanDO plan() {
        ProjectNodeIterationPlanDO plan = new ProjectNodeIterationPlanDO();
        plan.setId(21L);
        plan.setProjectId(9L);
        plan.setName("订单一期");
        plan.setStatus("PLANNED");
        return plan;
    }
}
