package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO;
import com.brad.pms.dto.response.NodeTaskSummaryDTO;
import com.brad.pms.entity.DevelopmentItemTaskDO;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import com.brad.pms.entity.DevelopmentItemWorkflowNodeDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.WorkflowTemplateVersionDO;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** detailIfInitialized is the AI readonly detail entry: it never creates workflows. */
@ExtendWith(MockitoExtension.class)
class DevelopmentItemWorkflowServiceReadOnlyTest {

    @Mock DevelopmentItemWorkflowMapper workflowMapperParam;
    @Mock DevelopmentItemWorkflowNodeMapper nodeMapperParam;
    @Mock DevelopmentItemTaskMapper taskMapperParam;
    @Mock ProjectNodeDevelopmentTopicMapper topicMapperParam;
    @Mock ProjectNodeDevelopmentStoryMapper storyMapperParam;
    @Mock RequirementMapper requirementMapperParam;
    @Mock ProjectNodeMapper projectNodeMapperParam;
    @Mock ProjectNodeIterationPlanMapper iterationPlanMapperParam;
    @Mock WorkflowTemplateVersionMapper templateVersionMapperParam;
    @Mock ProjectPermissionService permissionServiceParam;
    @Mock UserService userServiceParam;
    @Mock WorkflowTemplateService workflowTemplateServiceParam;
    @Mock WorkflowComponentBindingService workflowComponentBindingServiceParam;
    @Mock ProjectMemberAssignmentService assignmentServiceParam;

    @Test
    void detailIfInitializedReturnsExistingWorkflowWithoutCreatingIt() {
        Fixture fixture = new Fixture();
        fixture.workflow.setItemType("STORY");
        fixture.workflow.setItemId(8L);

        DevelopmentItemWorkflowDetailDTO detail = fixture.service.detailIfInitialized(DevelopmentItemType.STORY, 8L);

        assertThat(detail.getWorkflowConfigured()).isTrue();
        assertThat(detail.getWorkflowId()).isEqualTo(31L);
        verify(fixture.workflowMapper, never()).insert(any(DevelopmentItemWorkflowDO.class));
        verify(fixture.nodeMapper, never()).insert(any(DevelopmentItemWorkflowNodeDO.class));
    }

    @Test
    void detailIfInitializedKeepsUnconfiguredItemsUnconfigured() {
        Fixture fixture = new Fixture();
        fixture.story.setTopicId(null);
        fixture.story.setProjectId(null);
        fixture.story.setNodeId(null);
        fixture.workflow.setItemType("STORY");
        fixture.workflow.setItemId(99L);
        when(fixture.workflowMapper.selectByItem("STORY", 8L)).thenReturn(null);

        DevelopmentItemWorkflowDetailDTO detail = fixture.service.detailIfInitialized(DevelopmentItemType.STORY, 8L);

        assertThat(detail.getWorkflowConfigured()).isFalse();
        assertThat(detail.getWorkflowStatus()).isEqualTo("NOT_CONFIGURED");
        assertThat(detail.getNodes()).isEmpty();
        assertThat(detail.getWorkflowVersion()).isNull();
        assertThat(detail.getWorkflowId()).isNull();
        verify(fixture.workflowMapper, never()).insert(any(DevelopmentItemWorkflowDO.class));
    }

    @Test
    void detailIfInitializedRejectsAWorkflowThatBelongsToAnotherItem() {
        Fixture fixture = new Fixture();
        fixture.workflow.setItemType("STORY");
        fixture.workflow.setItemId(8L);
        // A workflow recorded against a different project/target is a binding integrity error.
        fixture.workflow.setProjectId(6L);

        assertThatThrownBy(() -> fixture.service.detailIfInitialized(DevelopmentItemType.STORY, 8L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不存在");
    }

    @Test
    void detailExposesWorkflowVersionSeparatelyFromTemplateVersion() {
        Fixture fixture = new Fixture();
        fixture.workflow.setItemType("STORY");
        fixture.workflow.setItemId(8L);
        fixture.workflow.setVersion(5);

        DevelopmentItemWorkflowDetailDTO detail = fixture.service.detailIfInitialized(DevelopmentItemType.STORY, 8L);

        assertThat(detail.getWorkflowVersion()).isEqualTo(5);
        assertThat(detail.getTemplateVersionId()).isEqualTo(88L);
        assertThat(detail.getNodes().get(0).getVersion()).isZero();
    }

    @Test
    void detailCollectsFlatTaskSummariesPerNodeIncludingOrphanedSubtasks() {
        Fixture fixture = new Fixture();
        fixture.workflow.setItemType("STORY");
        fixture.workflow.setItemId(8L);
        fixture.workflow.setVersion(5);
        // Production mappers filter soft-deleted rows: the deleted parent never returns,
        // while its surviving children come back as flat rows and must still be counted.
        DevelopmentItemTaskDO liveChild = fixture.task(52L, 51L, 2);
        DevelopmentItemTaskDO openSubtask = fixture.task(53L, 51L, 0);
        fixture.tasks.add(liveChild);
        fixture.tasks.add(openSubtask);

        DevelopmentItemWorkflowDetailDTO detail = fixture.service.detailIfInitialized(DevelopmentItemType.STORY, 8L);

        assertThat(detail.getNodeTaskSummaries()).containsEntry(21L, new NodeTaskSummaryDTO(2, 1, 1));
        // The UI tree drops children whose parent row is missing; only the flat summary counts them.
        assertThat(detail.getNodes().get(0).getTasks()).isEmpty();
    }

    @Test
    void detailReportsNoTaskCountForUnconfiguredItems() {
        Fixture fixture = new Fixture();
        fixture.story.setTopicId(null);
        fixture.story.setProjectId(null);
        fixture.story.setNodeId(null);
        fixture.workflow.setItemType("STORY");
        fixture.workflow.setItemId(99L);
        when(fixture.workflowMapper.selectByItem("STORY", 8L)).thenReturn(null);

        DevelopmentItemWorkflowDetailDTO detail = fixture.service.detailIfInitialized(DevelopmentItemType.STORY, 8L);

        assertThat(detail.getNodeTaskSummaries()).isNullOrEmpty();
    }

    private static final class Fixture {
        final DevelopmentItemWorkflowMapper workflowMapper = mock(DevelopmentItemWorkflowMapper.class);
        final DevelopmentItemWorkflowNodeMapper nodeMapper = mock(DevelopmentItemWorkflowNodeMapper.class);
        final DevelopmentItemTaskMapper taskMapper = mock(DevelopmentItemTaskMapper.class);
        final ProjectNodeDevelopmentTopicMapper topicMapper = mock(ProjectNodeDevelopmentTopicMapper.class);
        final ProjectNodeDevelopmentStoryMapper storyMapper = mock(ProjectNodeDevelopmentStoryMapper.class);
        final RequirementMapper requirementMapper = mock(RequirementMapper.class);
        final ProjectNodeMapper projectNodeMapper = mock(ProjectNodeMapper.class);
        final ProjectNodeIterationPlanMapper iterationPlanMapper = mock(ProjectNodeIterationPlanMapper.class);
        final WorkflowTemplateVersionMapper templateVersionMapper = mock(WorkflowTemplateVersionMapper.class);
        final ProjectPermissionService permissionService = mock(ProjectPermissionService.class);
        final UserService userService = mock(UserService.class);
        final WorkflowTemplateService workflowTemplateService = mock(WorkflowTemplateService.class);
        final WorkflowComponentBindingService workflowComponentBindingService = mock(WorkflowComponentBindingService.class);
        final ProjectMemberAssignmentService assignmentService = mock(ProjectMemberAssignmentService.class);
        final DevelopmentItemWorkflowService service;
        final ProjectDO project = new ProjectDO();
        final ProjectNodeDO sourceNode = new ProjectNodeDO();
        final ProjectNodeDevelopmentTopicDO topic = new ProjectNodeDevelopmentTopicDO();
        final ProjectNodeDevelopmentStoryDO story = new ProjectNodeDevelopmentStoryDO();
        final DevelopmentItemWorkflowDO workflow = new DevelopmentItemWorkflowDO();
        final DevelopmentItemWorkflowNodeDO node = new DevelopmentItemWorkflowNodeDO();
        final List<DevelopmentItemTaskDO> tasks = new ArrayList<>();

        Fixture() {
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
            story.setId(8L);
            story.setTitle("故事");
            story.setTopicId(7L);
            story.setProjectId(5L);
            story.setNodeId(9L);
            story.setStatus("IN_PROGRESS");
            story.setProgress(20);

            workflow.setId(31L);
            workflow.setProjectId(5L);
            workflow.setSourceNodeId(9L);
            workflow.setTemplateVersionId(88L);
            workflow.setVersion(0);

            node.setId(21L);
            node.setWorkflowId(31L);
            node.setNodeKey("design");
            node.setName("设计");
            node.setSort(0);
            node.setStatus(1);
            node.setVersion(0);

            org.mockito.Mockito.lenient().when(permissionService.requireProjectReadable(5L)).thenReturn(project);
            when(storyMapper.selectById(8L)).thenReturn(story);
            org.mockito.Mockito.lenient().when(topicMapper.selectById(7L)).thenReturn(topic);
            org.mockito.Mockito.lenient().when(projectNodeMapper.selectById(9L)).thenReturn(sourceNode);
            org.mockito.Mockito.lenient().when(workflowMapper.selectByItem("STORY", 8L)).thenReturn(workflow);
            org.mockito.Mockito.lenient().when(nodeMapper.selectList(any())).thenReturn(List.of(node));
            org.mockito.Mockito.lenient().when(taskMapper.selectList(any()))
                    .thenAnswer(invocation -> List.copyOf(tasks));
            org.mockito.Mockito.lenient().when(templateVersionMapper.selectById(88L)).thenReturn(new WorkflowTemplateVersionDO());
            org.mockito.Mockito.lenient().when(userService.listByIdsIncludingDeleted(anyList())).thenReturn(List.of());
            org.mockito.Mockito.lenient().when(workflowTemplateService.getDefinitionForContext(88L))
                    .thenReturn(new com.brad.pms.workflow.WorkflowTemplateDefinition(1, List.of(
                            new com.brad.pms.workflow.WorkflowNodeDefinition("design", "设计", null, null, null,
                                    List.of(), List.of(), false, List.of(), null))));
            org.mockito.Mockito.lenient().when(workflowComponentBindingService.applyStoryBinding(any(), any()))
                    .thenAnswer(invocation -> invocation.getArgument(0));
        }

        DevelopmentItemTaskDO task(Long id, Long parentId, int status) {
            DevelopmentItemTaskDO task = new DevelopmentItemTaskDO();
            task.setId(id);
            task.setWorkflowId(31L);
            task.setNodeId(21L);
            task.setParentId(parentId);
            task.setTitle("task-" + id);
            task.setStatus(status);
            task.setVersion(0);
            return task;
        }
    }
}
