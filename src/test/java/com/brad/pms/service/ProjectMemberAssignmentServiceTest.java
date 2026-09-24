package com.brad.pms.service;

import com.brad.pms.common.enums.DevelopmentAssignmentType;
import com.brad.pms.entity.ProjectMemberDO;
import com.brad.pms.entity.ProjectMemberAssignmentRefDO;
import com.brad.pms.entity.ProjectMemberAutoManagedDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.mapper.DevelopmentItemTaskMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowNodeMapper;
import com.brad.pms.mapper.ProjectMemberAssignmentRefMapper;
import com.brad.pms.mapper.ProjectMemberAutoManagedMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.workflow.DevelopmentItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectMemberAssignmentServiceTest {

    @Mock ProjectMemberAssignmentRefMapper refMapper;
    @Mock ProjectMemberAutoManagedMapper autoManagedMapper;
    @Mock MemberService memberService;
    @Mock UserService userService;
    @Mock DevelopmentItemWorkflowMapper workflowMapper;
    @Mock DevelopmentItemWorkflowNodeMapper workflowNodeMapper;
    @Mock DevelopmentItemTaskMapper taskMapper;
    @Mock ProjectNodeDevelopmentTopicMapper topicMapper;
    @Mock ProjectNodeDevelopmentStoryMapper storyMapper;

    @Test
    void replacingAssignmentEnsuresMemberAndUpsertsStableReference() {
        ProjectMemberAssignmentService service = new ProjectMemberAssignmentService(
                refMapper, autoManagedMapper, memberService, userService,
                workflowMapper, workflowNodeMapper, taskMapper, topicMapper, storyMapper);
        MemberService.EnsureMemberResult ensured = new MemberService.EnsureMemberResult(
                member(1L, 88L), true);
        when(memberService.ensureMemberForAssignment(1L, 88L)).thenReturn(ensured);
        when(refMapper.selectByKey(1L, "TOPIC", 10L, "TOPIC_OWNER", 10L)).thenReturn(null);

        service.replaceAssignment(1L, DevelopmentItemType.TOPIC, 10L,
                DevelopmentAssignmentType.TOPIC_OWNER, 10L, 88L);

        verify(userService).requireActiveUser(88L);
        verify(memberService).ensureMemberForAssignment(1L, 88L);
        verify(autoManagedMapper).insertIgnore(1L, 88L);
        verify(refMapper).upsert(any(ProjectMemberAssignmentRefDO.class));
    }

    @Test
    void synchronizingSameItemCleansUpPreviousUserWhenAnAssignmentKeyChangesOwner() {
        ProjectMemberAssignmentService service = new ProjectMemberAssignmentService(
                refMapper, autoManagedMapper, memberService, userService,
                workflowMapper, workflowNodeMapper, taskMapper, topicMapper, storyMapper);
        ProjectNodeDevelopmentTopicDO topic = new ProjectNodeDevelopmentTopicDO();
        topic.setId(10L);
        topic.setOwnerId(99L);
        when(topicMapper.selectById(10L)).thenReturn(topic);
        when(workflowMapper.selectByItem("TOPIC", 10L)).thenReturn(null);

        ProjectMemberAssignmentRefDO previous = new ProjectMemberAssignmentRefDO();
        previous.setProjectId(1L);
        previous.setItemType("TOPIC");
        previous.setItemId(10L);
        previous.setAssignmentType("TOPIC_OWNER");
        previous.setAssignmentId(10L);
        previous.setUserId(88L);
        when(refMapper.selectByItemForUpdate(1L, "TOPIC", 10L)).thenReturn(java.util.List.of(previous));
        when(memberService.ensureMemberForAssignment(1L, 99L))
                .thenReturn(new MemberService.EnsureMemberResult(member(1L, 99L), true));
        when(refMapper.selectByProjectUserForUpdate(1L, 88L)).thenReturn(java.util.List.of());
        when(autoManagedMapper.selectForUpdate(1L, 88L)).thenReturn(new ProjectMemberAutoManagedDO());

        service.synchronizeItemAssignments(1L, 1L, DevelopmentItemType.TOPIC, 10L);

        verify(refMapper).upsert(argThat(ref -> ref.getUserId().equals(99L)));
        verify(refMapper).deleteByKey(1L, "TOPIC", 10L, "TOPIC_OWNER", 10L);
        verify(autoManagedMapper).delete(1L, 88L);
        verify(memberService).removeAutoManagedMember(1L, 88L);
    }

    private static ProjectMemberDO member(Long projectId, Long userId) {
        ProjectMemberDO member = new ProjectMemberDO();
        member.setId(1L);
        member.setProjectId(projectId);
        member.setUserId(userId);
        member.setRole(2);
        return member;
    }
}
