package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.DevelopmentItemTaskDO;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import com.brad.pms.entity.DevelopmentItemWorkflowNodeDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.mapper.DevelopmentItemTaskMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowNodeMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.RequirementMapper;
import com.brad.pms.mapper.WorkflowTemplateVersionMapper;
import com.brad.pms.workflow.DevelopmentItemType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class DevelopmentItemWorkflowServiceRollbackTest {

    @Test
    void rejectsRollbackOfAnActiveNode() {
        Fixture fixture = new Fixture();
        fixture.target.setStatus(1);

        assertThatThrownBy(() -> fixture.service.rollbackNode(
                DevelopmentItemType.TOPIC, 7L, fixture.target.getId(), "需求调整"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能回滚已完成的流程节点");
        verify(fixture.nodeMapper, never()).updateById(any(DevelopmentItemWorkflowNodeDO.class));
    }

    @Test
    void requiresRollbackReason() {
        Fixture fixture = new Fixture();

        assertThatThrownBy(() -> fixture.service.rollbackNode(
                DevelopmentItemType.TOPIC, 7L, fixture.target.getId(), "  "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("回滚原因不能为空");
        verify(fixture.nodeMapper, never()).updateById(any(DevelopmentItemWorkflowNodeDO.class));
    }

    @Test
    void rollsBackTargetAndNormalizesFollowingNodeStatuses() {
        Fixture fixture = new Fixture();

        fixture.service.rollbackNode(
                DevelopmentItemType.TOPIC, 7L, fixture.target.getId(), "  需求范围调整  ");

        assertThat(fixture.before.getStatus()).isEqualTo(2);
        assertThat(fixture.target.getStatus()).isEqualTo(1);
        assertThat(fixture.after.getStatus()).isEqualTo(0);
        verify(fixture.nodeMapper).updateById(fixture.target);
        verify(fixture.nodeMapper).updateById(fixture.after);
    }

    private static final class Fixture {
        private final DevelopmentItemWorkflowMapper workflowMapper = mock(DevelopmentItemWorkflowMapper.class);
        private final DevelopmentItemWorkflowNodeMapper nodeMapper = mock(DevelopmentItemWorkflowNodeMapper.class);
        private final DevelopmentItemTaskMapper taskMapper = mock(DevelopmentItemTaskMapper.class);
        private final ProjectNodeDevelopmentTopicMapper topicMapper = mock(ProjectNodeDevelopmentTopicMapper.class);
        private final ProjectNodeDevelopmentStoryMapper storyMapper = mock(ProjectNodeDevelopmentStoryMapper.class);
        private final RequirementMapper requirementMapper = mock(RequirementMapper.class);
        private final ProjectNodeMapper projectNodeMapper = mock(ProjectNodeMapper.class);
        private final ProjectNodeIterationPlanMapper iterationPlanMapper = mock(ProjectNodeIterationPlanMapper.class);
        private final WorkflowTemplateVersionMapper templateVersionMapper = mock(WorkflowTemplateVersionMapper.class);
        private final ProjectPermissionService permissionService = mock(ProjectPermissionService.class);
        private final UserService userService = mock(UserService.class);
        private final WorkflowTemplateService workflowTemplateService = mock(WorkflowTemplateService.class);
        private final WorkflowComponentBindingService workflowComponentBindingService = mock(WorkflowComponentBindingService.class);
        private final ProjectMemberAssignmentService assignmentService = mock(ProjectMemberAssignmentService.class);
        private final DevelopmentItemWorkflowService service;
        private final ProjectDO project = new ProjectDO();
        private final ProjectNodeDO sourceNode = new ProjectNodeDO();
        private final ProjectNodeDevelopmentTopicDO topic = new ProjectNodeDevelopmentTopicDO();
        private final DevelopmentItemWorkflowDO workflow = new DevelopmentItemWorkflowDO();
        private final DevelopmentItemWorkflowNodeDO before = node(20L, 0, 2);
        private final DevelopmentItemWorkflowNodeDO target = node(21L, 1, 2);
        private final DevelopmentItemWorkflowNodeDO after = node(22L, 2, 1);

        private Fixture() {
            service = new DevelopmentItemWorkflowService(workflowMapper, nodeMapper, taskMapper, topicMapper,
                    storyMapper, requirementMapper, projectNodeMapper, iterationPlanMapper, templateVersionMapper,
                    permissionService, userService, workflowTemplateService, workflowComponentBindingService,
                    assignmentService, new ObjectMapper());

            project.setId(5L);
            project.setName("项目");
            project.setCode("PRJ-0001");
            sourceNode.setId(9L);
            sourceNode.setProjectId(5L);
            topic.setId(7L);
            topic.setTitle("专题");
            topic.setProjectId(5L);
            topic.setNodeId(9L);

            workflow.setId(31L);
            workflow.setItemType("TOPIC");
            workflow.setItemId(7L);
            workflow.setProjectId(5L);
            workflow.setSourceNodeId(9L);
            workflow.setTemplateVersionId(88L);

            when(permissionService.requireProjectReadable(5L)).thenReturn(project);
            when(permissionService.requireProjectWritable(5L, "回滚研发事项流程节点")).thenReturn(project);
            when(permissionService.requireProjectManageable(5L, "回滚研发事项流程节点")).thenReturn(project);
            when(topicMapper.selectById(7L)).thenReturn(topic);
            when(topicMapper.selectByIdForUpdate(7L)).thenReturn(topic);
            when(storyMapper.selectList(any())).thenReturn(List.of());
            when(projectNodeMapper.selectById(9L)).thenReturn(sourceNode);
            when(workflowMapper.selectByItem("TOPIC", 7L)).thenReturn(workflow);
            when(workflowMapper.selectForUpdate("TOPIC", 7L)).thenReturn(workflow);
            when(nodeMapper.selectByWorkflowIdsForUpdate(anyList())).thenReturn(List.of(before, target, after));
            when(nodeMapper.selectList(any())).thenReturn(List.of(before, target, after));
            when(nodeMapper.updateById(any(DevelopmentItemWorkflowNodeDO.class))).thenReturn(1);
            when(taskMapper.selectList(any())).thenReturn(List.of());
            when(templateVersionMapper.selectById(88L)).thenReturn(null);
            when(userService.listByIdsIncludingDeleted(anyList())).thenReturn(List.of());
        }

        private DevelopmentItemWorkflowNodeDO node(Long id, int sort, int status) {
            DevelopmentItemWorkflowNodeDO node = new DevelopmentItemWorkflowNodeDO();
            node.setId(id);
            node.setWorkflowId(31L);
            node.setNodeKey("node-" + id);
            node.setName("节点" + id);
            node.setSort(sort);
            node.setStatus(status);
            node.setVersion(0);
            return node;
        }
    }
}
