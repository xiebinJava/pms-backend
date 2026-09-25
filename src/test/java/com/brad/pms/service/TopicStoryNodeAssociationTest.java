package com.brad.pms.service;

import com.brad.pms.dto.response.DevelopmentTopicStoryDTO;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.mapper.DevelopmentItemTaskMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.workflow.DevelopmentItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopicStoryNodeAssociationTest {

    @Mock ProjectNodeDevelopmentStoryMapper storyMapper;
    @Mock DevelopmentTopicManagementService topicManagementService;
    @Mock DevelopmentItemWorkflowService developmentItemWorkflowService;
    @Mock DevelopmentItemWorkflowMapper workflowMapper;
    @Mock DevelopmentItemTaskMapper taskMapper;
    @Mock UserService userService;
    @Mock ProjectMemberAssignmentService assignmentService;
    @InjectMocks DevelopmentStoryManagementService service;

    @Test
    void topicStoryListExposesThePinnedTopicWorkflowNodeForNodeScopedRendering() {
        ProjectNodeDevelopmentTopicDO topic = topic(7L);
        ProjectNodeDevelopmentStoryDO story = story(11L, 7L, 42L);
        when(topicManagementService.requireReadableTopic(7L)).thenReturn(topic);
        when(storyMapper.selectByTopicId(7L)).thenReturn(List.of(story));

        List<DevelopmentTopicStoryDTO> result = service.listByTopic(7L);

        assertThat(result).singleElement()
                .extracting(DevelopmentTopicStoryDTO::getTopicWorkflowNodeId)
                .isEqualTo(42L);
    }

    @Test
    void creatingAStoryUsesTheTopicWorkflowMountNodeAutomatically() {
        ProjectNodeDevelopmentTopicDO topic = topic(7L);
        when(topicManagementService.requireWritableTopic(7L)).thenReturn(topic);
        when(developmentItemWorkflowService.resolveTopicStoryMountNodeId(7L)).thenReturn(42L);
        when(developmentItemWorkflowService.createIfDefaultExists(
                DevelopmentItemType.STORY, 11L, 100L, 200L)).thenReturn(workflow(11L));
        doAnswer(invocation -> {
            ProjectNodeDevelopmentStoryDO value = invocation.getArgument(0);
            value.setId(11L);
            return 1;
        }).when(storyMapper).insert(any(ProjectNodeDevelopmentStoryDO.class));

        var command = new com.brad.pms.dto.request.DevelopmentStorySaveCmd();
        command.setTopicId(7L);
        command.setTitle("故事");

        service.create(command);

        org.mockito.Mockito.verify(storyMapper).insert(org.mockito.ArgumentMatchers.<ProjectNodeDevelopmentStoryDO>argThat(
                value -> value.getTopicWorkflowNodeId().equals(42L)));
    }

    private static ProjectNodeDevelopmentTopicDO topic(Long id) {
        ProjectNodeDevelopmentTopicDO topic = new ProjectNodeDevelopmentTopicDO();
        topic.setId(id);
        topic.setProjectId(100L);
        topic.setNodeId(200L);
        topic.setTitle("专题");
        topic.setDeleted(false);
        return topic;
    }

    private static ProjectNodeDevelopmentStoryDO story(Long id, Long topicId, Long topicWorkflowNodeId) {
        ProjectNodeDevelopmentStoryDO story = new ProjectNodeDevelopmentStoryDO();
        story.setId(id);
        story.setTopicId(topicId);
        story.setTopicWorkflowNodeId(topicWorkflowNodeId);
        story.setTitle("故事");
        story.setStatus("NOT_STARTED");
        story.setProgress(0);
        return story;
    }

    private static DevelopmentItemWorkflowDO workflow(Long storyId) {
        DevelopmentItemWorkflowDO workflow = new DevelopmentItemWorkflowDO();
        workflow.setId(300L);
        workflow.setItemType("STORY");
        workflow.setItemId(storyId);
        workflow.setProjectId(100L);
        workflow.setSourceNodeId(200L);
        return workflow;
    }
}
