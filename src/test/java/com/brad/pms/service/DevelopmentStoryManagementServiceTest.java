package com.brad.pms.service;

import com.brad.pms.dto.request.DevelopmentStorySaveCmd;
import com.brad.pms.dto.response.DevelopmentTopicStoryDTO;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.DevelopmentItemTaskMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.workflow.DevelopmentItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevelopmentStoryManagementServiceTest {

    @Mock ProjectNodeDevelopmentStoryMapper storyMapper;
    @Mock DevelopmentTopicManagementService topicManagementService;
    @Mock DevelopmentItemWorkflowService developmentItemWorkflowService;
    @Mock DevelopmentItemWorkflowMapper workflowMapper;
    @Mock DevelopmentItemTaskMapper taskMapper;
    @Mock UserService userService;
    @Mock ProjectMemberAssignmentService assignmentService;
    @InjectMocks DevelopmentStoryManagementService service;

    @Test
    void createsIndependentStoryWithCompanyWideOwnerValidation() {
        when(developmentItemWorkflowService.createIfDefaultExists(
                DevelopmentItemType.STORY, 30L, null, null)).thenReturn(workflow(300L, null, null));
        doAnswer(invocation -> {
            ProjectNodeDevelopmentStoryDO story = invocation.getArgument(0);
            story.setId(30L);
            return 1;
        }).when(storyMapper).insert(any(ProjectNodeDevelopmentStoryDO.class));

        DevelopmentStorySaveCmd cmd = command("独立故事", null, 88L);
        Long id = service.create(cmd);

        assertThat(id).isEqualTo(30L);
        verify(userService).requireActiveUser(88L);
        verify(assignmentService).replaceAssignment(null, DevelopmentItemType.STORY, 30L,
                com.brad.pms.common.enums.DevelopmentAssignmentType.STORY_OWNER, 30L, 88L);
        verify(storyMapper).insert(org.mockito.ArgumentMatchers.<ProjectNodeDevelopmentStoryDO>argThat(story ->
                story.getId().equals(30L)
                        && story.getTopicId() == null
                        && story.getProjectId() == null
                        && story.getNodeId() == null
                        && story.getStatus().equals("IN_PROGRESS")
                        && story.getProgress().equals(40)));
    }

    @Test
    void rebindsStoryToIndependentContextAndRebuildsWorkflowContext() {
        ProjectNodeDevelopmentStoryDO story = story(30L, 10L, 1L, 11L, 88L);
        ProjectNodeDevelopmentTopicDO oldTopic = topic(10L, 1L, 11L);
        DevelopmentItemWorkflowDO workflow = workflow(300L, 1L, 11L);
        when(storyMapper.selectByIdForUpdate(30L)).thenReturn(story);
        when(topicManagementService.requireWritableTopic(10L)).thenReturn(oldTopic);
        when(workflowMapper.selectForUpdate("STORY", 30L)).thenReturn(workflow);
        when(taskMapper.selectByWorkflowIdsForUpdate(List.of(300L))).thenReturn(List.of());
        when(storyMapper.updateById(story)).thenReturn(1);
        when(workflowMapper.updateById(workflow)).thenReturn(1);
        when(developmentItemWorkflowService.createIfDefaultExists(
                DevelopmentItemType.STORY, 30L, null, null)).thenReturn(workflow(300L, null, null));

        service.update(30L, command("独立故事", null, null));

        assertThat(story.getTopicId()).isNull();
        assertThat(story.getProjectId()).isNull();
        assertThat(story.getNodeId()).isNull();
        assertThat(story.getIterationPlanId()).isNull();
        assertThat(workflow.getProjectId()).isNull();
        assertThat(workflow.getSourceNodeId()).isNull();
        verify(assignmentService).synchronizeItemAssignments(1L, null, DevelopmentItemType.STORY, 30L);
        var order = inOrder(workflowMapper, developmentItemWorkflowService);
        order.verify(workflowMapper).updateById(workflow);
        order.verify(developmentItemWorkflowService).createIfDefaultExists(
                DevelopmentItemType.STORY, 30L, null, null);
    }

    @Test
    void changingOwnerOnTheSameContextReplacesTheStableAssignment() {
        ProjectNodeDevelopmentStoryDO story = story(30L, null, null, null, 88L);
        when(storyMapper.selectByIdForUpdate(30L)).thenReturn(story);
        when(developmentItemWorkflowService.createIfDefaultExists(
                DevelopmentItemType.STORY, 30L, null, null)).thenReturn(workflow(300L, null, null));
        when(storyMapper.updateById(story)).thenReturn(1);

        service.update(30L, command("独立故事", null, 99L));

        verify(userService).requireActiveUser(99L);
        verify(assignmentService).replaceAssignment(null, DevelopmentItemType.STORY, 30L,
                com.brad.pms.common.enums.DevelopmentAssignmentType.STORY_OWNER, 30L, 99L);
    }

    @Test
    void listsStoryWithHistoricalOwnerDisplay() {
        ProjectNodeDevelopmentTopicDO topic = topic(10L, 1L, 11L);
        ProjectNodeDevelopmentStoryDO story = story(30L, 1L, 11L, 10L, 88L);
        story.setTitle("历史故事");
        story.setStatus("DONE");
        story.setProgress(20);
        when(topicManagementService.requireReadableTopic(10L)).thenReturn(topic);
        when(storyMapper.selectByTopicId(10L)).thenReturn(List.of(story));
        UserDO historical = new UserDO();
        historical.setId(88L);
        historical.setNameZh("历史账号");
        historical.setUsername("former.user");
        historical.setDeleted(true);
        when(userService.listByIdsIncludingDeleted(List.of(88L))).thenReturn(List.of(historical));

        List<DevelopmentTopicStoryDTO> result = service.listByTopic(10L);

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.getOwnerId()).isEqualTo(88L);
            assertThat(dto.getOwnerName()).contains("历史账号").contains("former.user");
            assertThat(dto.getProgress()).isEqualTo(100);
        });
        verify(userService).listByIdsIncludingDeleted(List.of(88L));
    }

    private static DevelopmentStorySaveCmd command(String title, Long topicId, Long ownerId) {
        DevelopmentStorySaveCmd cmd = new DevelopmentStorySaveCmd();
        cmd.setTitle(title);
        cmd.setTopicId(topicId);
        cmd.setOwnerId(ownerId);
        cmd.setStatus("IN_PROGRESS");
        cmd.setProgress(40);
        cmd.setStoryPoints(5);
        cmd.setStartDate(LocalDate.of(2026, 9, 24));
        cmd.setDueDate(LocalDate.of(2026, 9, 25));
        return cmd;
    }

    private static ProjectNodeDevelopmentTopicDO topic(Long id, Long projectId, Long nodeId) {
        ProjectNodeDevelopmentTopicDO topic = new ProjectNodeDevelopmentTopicDO();
        topic.setId(id);
        topic.setProjectId(projectId);
        topic.setNodeId(nodeId);
        topic.setTitle("专题");
        topic.setDeleted(false);
        return topic;
    }

    private static ProjectNodeDevelopmentStoryDO story(Long id, Long topicId, Long projectId,
                                                        Long nodeId, Long ownerId) {
        ProjectNodeDevelopmentStoryDO story = new ProjectNodeDevelopmentStoryDO();
        story.setId(id);
        story.setTopicId(topicId);
        story.setProjectId(projectId);
        story.setNodeId(nodeId);
        story.setOwnerId(ownerId);
        story.setTitle("故事");
        story.setStatus("IN_PROGRESS");
        story.setProgress(40);
        story.setStoryPoints(5);
        return story;
    }

    private static DevelopmentItemWorkflowDO workflow(Long id, Long projectId, Long nodeId) {
        DevelopmentItemWorkflowDO workflow = new DevelopmentItemWorkflowDO();
        workflow.setId(id);
        workflow.setItemType("STORY");
        workflow.setItemId(30L);
        workflow.setProjectId(projectId);
        workflow.setSourceNodeId(nodeId);
        return workflow;
    }
}
