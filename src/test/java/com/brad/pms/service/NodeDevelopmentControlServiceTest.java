package com.brad.pms.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodeDevelopmentControlUpdateCmd;
import com.brad.pms.dto.request.NodeDevelopmentStoryCmd;
import com.brad.pms.dto.request.NodeDevelopmentTopicCmd;
import com.brad.pms.dto.response.NodeDevelopmentControlDTO;
import com.brad.pms.dto.response.NodeIterationPlanDTO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeDevelopmentBaselineDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.mapper.ProjectMemberMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentBaselineMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeDevelopmentControlServiceTest {

    @Mock ProjectNodeDevelopmentBaselineMapper baselineMapper;
    @Mock ProjectNodeDevelopmentTopicMapper topicMapper;
    @Mock ProjectNodeDevelopmentStoryMapper storyMapper;
    @Mock IterationPlanService iterationPlanService;
    @Mock ProjectMemberMapper memberMapper;
    @Mock ProjectPermissionService permissionService;
    @Mock UserService userService;
    @Mock OperationLogService operationLogService;

    @InjectMocks NodeDevelopmentControlService service;

    @BeforeEach
    void initTableInfo() {
        org.mockito.Mockito.lenient().when(iterationPlanService.listByProject(any())).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(iterationPlanService.listForDevelopment(any(), any())).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(iterationPlanService.confirmedPlanIds(any())).thenReturn(java.util.Set.of());
    }

    @Test
    void keepsHistoricalIterationPlanNameWhenCurrentBaselineMovedOn() {
        ProjectNodeDO node = node("develop");
        ProjectNodeDevelopmentTopicDO topic = topic(20L, "订单中心");
        ProjectNodeDevelopmentStoryDO story = story(20L, "统一订单状态", "IN_PROGRESS", 60);
        story.setId(30L);
        story.setIterationPlanId(77L);
        NodeIterationPlanDTO historicalPlan = new NodeIterationPlanDTO();
        historicalPlan.setId(77L);
        historicalPlan.setName("历史迭代计划");
        when(permissionService.requireProjectReadable(1L)).thenReturn(new ProjectDO());
        when(permissionService.requireNode(1L, 10L)).thenReturn(node);
        when(topicMapper.selectList(any())).thenReturn(List.of(topic));
        when(storyMapper.selectList(any())).thenReturn(List.of(story));
        when(iterationPlanService.listForDevelopment(eq(1L), argThat(ids -> ids.contains(77L))))
                .thenReturn(List.of(historicalPlan));

        NodeDevelopmentControlDTO result = service.get(1L, 10L);

        assertThat(result.getTopics().get(0).getStories().get(0).getIterationPlanName())
                .isEqualTo("历史迭代计划");
    }

    @Test
    void allowsAnExistingStoryToKeepItsHistoricalIterationPlan() {
        ProjectNodeDO node = node("develop");
        ProjectNodeDevelopmentTopicDO existingTopic = topic(20L, "订单中心");
        ProjectNodeDevelopmentStoryDO existingStory = story(20L, "统一订单状态", "IN_PROGRESS", 60);
        existingStory.setId(30L);
        existingStory.setIterationPlanId(77L);
        when(permissionService.requireManageableNode(1L, 10L, "保存开发测试与项目控制")).thenReturn(node);
        when(baselineMapper.selectOne(any())).thenReturn(baseline());
        when(baselineMapper.updateById(any(ProjectNodeDevelopmentBaselineDO.class))).thenReturn(1);
        when(topicMapper.selectList(any())).thenReturn(List.of(existingTopic));
        when(storyMapper.selectList(any())).thenReturn(List.of(existingStory));
        when(topicMapper.updateById(any(ProjectNodeDevelopmentTopicDO.class))).thenReturn(1);
        when(storyMapper.updateById(any(ProjectNodeDevelopmentStoryDO.class))).thenReturn(1);

        NodeDevelopmentControlUpdateCmd cmd = new NodeDevelopmentControlUpdateCmd();
        cmd.setVersion(2);
        NodeDevelopmentTopicCmd topic = new NodeDevelopmentTopicCmd();
        topic.setId(20L);
        topic.setTitle("订单中心");
        NodeDevelopmentStoryCmd story = new NodeDevelopmentStoryCmd();
        story.setId(30L);
        story.setTitle("统一订单状态");
        story.setStatus("IN_PROGRESS");
        story.setProgress(60);
        story.setStoryPoints(5);
        story.setIterationPlanId(77L);
        topic.setStories(List.of(story));
        cmd.setTopics(List.of(topic));

        service.save(1L, 10L, cmd);

        verify(storyMapper).updateById(argThat((ProjectNodeDevelopmentStoryDO row) -> row.getId().equals(30L)
                && row.getIterationPlanId().equals(77L)));
    }

    @Test
    void derivesSummaryFromPersistedStories() {
        ProjectNodeDO node = node("develop");
        ProjectNodeDevelopmentTopicDO topic = topic(20L, "订单中心");
        when(permissionService.requireProjectReadable(1L)).thenReturn(new ProjectDO());
        when(permissionService.requireNode(1L, 10L)).thenReturn(node);
        when(baselineMapper.selectOne(any())).thenReturn(baseline());
        when(topicMapper.selectList(any())).thenReturn(List.of(topic));
        when(storyMapper.selectList(any())).thenReturn(List.of(
                story(20L, "统一订单状态", "IN_PROGRESS", 60),
                story(20L, "异常提醒", "BLOCKED", 40),
                story(20L, "订单查询", "DONE", 100)));

        NodeDevelopmentControlDTO result = service.get(1L, 10L);

        assertThat(result.getSummary().getTopicCount()).isEqualTo(1);
        assertThat(result.getSummary().getStoryCount()).isEqualTo(3);
        assertThat(result.getSummary().getCompletedStoryCount()).isEqualTo(1);
        assertThat(result.getSummary().getBlockedStoryCount()).isEqualTo(1);
        assertThat(result.getSummary().getProgress()).isEqualTo(67);
        assertThat(result.getTopics().get(0).getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(result.getTopics().get(0).getBlocker()).isEqualTo("需要确认");
    }

    @Test
    void rejectsNonDevelopmentNodes() {
        when(permissionService.requireProjectReadable(1L)).thenReturn(new ProjectDO());
        when(permissionService.requireNode(1L, 10L)).thenReturn(node("plan"));

        assertThatThrownBy(() -> service.get(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("开发测试与项目控制");
    }

    @Test
    void rejectsInvalidStoryProgressBeforeWriting() {
        ProjectNodeDO node = node("develop");
        when(permissionService.requireManageableNode(1L, 10L, "保存开发测试与项目控制")).thenReturn(node);
        NodeDevelopmentControlUpdateCmd cmd = new NodeDevelopmentControlUpdateCmd();
        NodeDevelopmentTopicCmd topic = new NodeDevelopmentTopicCmd();
        topic.setTitle("订单中心");
        NodeDevelopmentStoryCmd story = new NodeDevelopmentStoryCmd();
        story.setTitle("统一订单状态");
        story.setProgress(101);
        topic.setStories(List.of(story));
        cmd.setTopics(List.of(topic));

        assertThatThrownBy(() -> service.save(1L, 10L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("0 到 100");
    }

    @Test
    void rejectsStoryOwnerOutsideProjectBeforeWriting() {
        ProjectNodeDO node = node("develop");
        when(permissionService.requireManageableNode(1L, 10L, "保存开发测试与项目控制")).thenReturn(node);
        when(memberMapper.selectCount(any())).thenReturn(0L);

        NodeDevelopmentControlUpdateCmd cmd = new NodeDevelopmentControlUpdateCmd();
        NodeDevelopmentTopicCmd topic = new NodeDevelopmentTopicCmd();
        topic.setTitle("订单中心");
        NodeDevelopmentStoryCmd story = new NodeDevelopmentStoryCmd();
        story.setTitle("统一订单状态");
        story.setOwnerId(99L);
        topic.setStories(List.of(story));
        cmd.setTopics(List.of(topic));

        assertThatThrownBy(() -> service.save(1L, 10L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目成员");
    }

    @Test
    void rejectsStoryIterationPlanOutsideConfirmedPlanBeforeWriting() {
        ProjectNodeDO node = node("develop");
        when(permissionService.requireManageableNode(1L, 10L, "保存开发测试与项目控制")).thenReturn(node);

        NodeDevelopmentControlUpdateCmd cmd = new NodeDevelopmentControlUpdateCmd();
        NodeDevelopmentTopicCmd topic = new NodeDevelopmentTopicCmd();
        topic.setTitle("订单中心");
        NodeDevelopmentStoryCmd story = new NodeDevelopmentStoryCmd();
        story.setTitle("统一订单状态");
        story.setIterationPlanId(999L);
        topic.setStories(List.of(story));
        cmd.setTopics(List.of(topic));

        assertThatThrownBy(() -> service.save(1L, 10L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已确认的计划基线");
    }

    @Test
    void rejectsMissingVersionWhenDevelopmentBaselineAlreadyExists() {
        ProjectNodeDO node = node("develop");
        when(permissionService.requireManageableNode(1L, 10L, "保存开发测试与项目控制")).thenReturn(node);
        when(baselineMapper.selectOne(any())).thenReturn(baseline());

        NodeDevelopmentControlUpdateCmd cmd = new NodeDevelopmentControlUpdateCmd();
        cmd.setTopics(List.of());

        assertThatThrownBy(() -> service.save(1L, 10L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("版本号");
        verify(baselineMapper, never()).updateById(any(ProjectNodeDevelopmentBaselineDO.class));
    }

    @Test
    void preservesExistingTopicAndStoryIdsWhenSaving() {
        ProjectNodeDO node = node("develop");
        ProjectNodeDevelopmentTopicDO existingTopic = topic(20L, "订单中心");
        ProjectNodeDevelopmentStoryDO existingStory = story(20L, "统一订单状态", "IN_PROGRESS", 60);
        existingStory.setId(30L);
        when(permissionService.requireManageableNode(1L, 10L, "保存开发测试与项目控制")).thenReturn(node);
        when(baselineMapper.selectOne(any())).thenReturn(baseline());
        when(baselineMapper.updateById(any(ProjectNodeDevelopmentBaselineDO.class))).thenReturn(1);
        when(topicMapper.selectList(any())).thenReturn(List.of(existingTopic));
        when(storyMapper.selectList(any())).thenReturn(List.of(existingStory));
        when(topicMapper.updateById(any(ProjectNodeDevelopmentTopicDO.class))).thenReturn(1);
        when(storyMapper.updateById(any(ProjectNodeDevelopmentStoryDO.class))).thenReturn(1);

        NodeDevelopmentControlUpdateCmd cmd = new NodeDevelopmentControlUpdateCmd();
        cmd.setVersion(2);
        NodeDevelopmentTopicCmd topic = new NodeDevelopmentTopicCmd();
        topic.setId(20L);
        topic.setTitle("订单中心");
        NodeDevelopmentStoryCmd story = new NodeDevelopmentStoryCmd();
        story.setId(30L);
        story.setTitle("统一订单状态");
        story.setStatus("IN_PROGRESS");
        story.setProgress(60);
        story.setStoryPoints(5);
        story.setStartDate(LocalDate.of(2026, 9, 15));
        story.setDueDate(LocalDate.of(2026, 9, 18));
        topic.setStories(List.of(story));
        cmd.setTopics(List.of(topic));

        service.save(1L, 10L, cmd);

        verify(topicMapper, never()).delete(any());
        verify(storyMapper, never()).delete(any());
        verify(topicMapper).updateById(argThat((ProjectNodeDevelopmentTopicDO row) -> row.getId().equals(20L)));
        verify(storyMapper).updateById(argThat((ProjectNodeDevelopmentStoryDO row) -> row.getId().equals(30L)
                && LocalDate.of(2026, 9, 15).equals(row.getStartDate())
                && LocalDate.of(2026, 9, 18).equals(row.getDueDate())));
    }

    @Test
    void normalizesCompletedStoryProgressToFullCompletionBeforePersisting() {
        ProjectNodeDO node = node("develop");
        when(permissionService.requireManageableNode(1L, 10L, "保存开发测试与项目控制")).thenReturn(node);
        when(baselineMapper.selectOne(any())).thenReturn(baseline());
        when(baselineMapper.updateById(any(ProjectNodeDevelopmentBaselineDO.class))).thenReturn(1);
        when(topicMapper.selectList(any())).thenReturn(List.of());
        when(storyMapper.selectList(any())).thenReturn(List.of());
        when(topicMapper.insert(any(ProjectNodeDevelopmentTopicDO.class))).thenReturn(1);
        when(storyMapper.insert(any(ProjectNodeDevelopmentStoryDO.class))).thenReturn(1);

        NodeDevelopmentControlUpdateCmd cmd = new NodeDevelopmentControlUpdateCmd();
        cmd.setVersion(2);
        NodeDevelopmentTopicCmd topic = new NodeDevelopmentTopicCmd();
        topic.setTitle("订单中心");
        NodeDevelopmentStoryCmd story = new NodeDevelopmentStoryCmd();
        story.setTitle("统一订单状态");
        story.setStatus("DONE");
        story.setProgress(0);
        topic.setStories(List.of(story));
        cmd.setTopics(List.of(topic));

        service.save(1L, 10L, cmd);

        verify(storyMapper).insert(argThat((ProjectNodeDevelopmentStoryDO row) -> row.getProgress().equals(100)));
    }

    @Test
    void rejectsStoryScheduleWhenStartDateIsAfterEndDate() {
        ProjectNodeDO node = node("develop");
        when(permissionService.requireManageableNode(1L, 10L, "保存开发测试与项目控制")).thenReturn(node);
        NodeDevelopmentControlUpdateCmd cmd = new NodeDevelopmentControlUpdateCmd();
        NodeDevelopmentTopicCmd topic = new NodeDevelopmentTopicCmd();
        topic.setTitle("订单中心");
        NodeDevelopmentStoryCmd story = new NodeDevelopmentStoryCmd();
        story.setTitle("统一订单状态");
        story.setStartDate(LocalDate.of(2026, 9, 20));
        story.setDueDate(LocalDate.of(2026, 9, 18));
        topic.setStories(List.of(story));
        cmd.setTopics(List.of(topic));

        assertThatThrownBy(() -> service.save(1L, 10L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("开始日期不能晚于结束日期");
    }

    @Test
    void completionRequiresAtLeastOneDevelopmentStory() {
        ProjectNodeDO node = node("develop");
        when(permissionService.requireProjectReadable(1L)).thenReturn(new ProjectDO());
        when(permissionService.requireNode(1L, 10L)).thenReturn(node);
        when(topicMapper.selectList(any())).thenReturn(List.of());
        when(storyMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.requireCompleted(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少建立一个专题");
    }

    @Test
    void completionRequiresAllStoriesDone() {
        ProjectNodeDO node = node("develop");
        ProjectNodeDevelopmentTopicDO topic = topic(20L, "订单中心");
        topic.setMilestoneId(301L);
        topic.setTestStatus("PASSED");
        when(permissionService.requireProjectReadable(1L)).thenReturn(new ProjectDO());
        when(permissionService.requireNode(1L, 10L)).thenReturn(node);
        when(topicMapper.selectList(any())).thenReturn(List.of(topic));
        when(storyMapper.selectList(any())).thenReturn(List.of(story(20L, "统一订单状态", "TESTING", 80)));

        assertThatThrownBy(() -> service.requireCompleted(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("全部故事");
    }

    @Test
    void completionIgnoresLegacyTopicTestStatus() {
        ProjectNodeDO node = node("develop");
        ProjectNodeDevelopmentTopicDO topic = topic(20L, "订单中心");
        topic.setMilestoneId(301L);
        topic.setTestStatus("TESTING");
        when(permissionService.requireProjectReadable(1L)).thenReturn(new ProjectDO());
        when(permissionService.requireNode(1L, 10L)).thenReturn(node);
        when(topicMapper.selectList(any())).thenReturn(List.of(topic));
        when(storyMapper.selectList(any())).thenReturn(List.of(story(20L, "统一订单状态", "DONE", 100)));

        service.requireCompleted(1L, 10L);
    }

    @Test
    void completionPassesWhenStoriesAreDoneAndTopicTestsPassed() {
        ProjectNodeDO node = node("develop");
        ProjectNodeDevelopmentTopicDO topic = topic(20L, "订单中心");
        topic.setMilestoneId(301L);
        topic.setTestStatus("PASSED");
        when(permissionService.requireProjectReadable(1L)).thenReturn(new ProjectDO());
        when(permissionService.requireNode(1L, 10L)).thenReturn(node);
        when(topicMapper.selectList(any())).thenReturn(List.of(topic));
        when(storyMapper.selectList(any())).thenReturn(List.of(story(20L, "统一订单状态", "DONE", 100)));

        service.requireCompleted(1L, 10L);
    }

    private ProjectNodeDO node(String key) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setNodeKey(key);
        node.setStatus(1);
        return node;
    }

    private ProjectNodeDevelopmentBaselineDO baseline() {
        ProjectNodeDevelopmentBaselineDO baseline = new ProjectNodeDevelopmentBaselineDO();
        baseline.setVersion(2);
        return baseline;
    }

    private ProjectNodeDevelopmentTopicDO topic(Long id, String title) {
        ProjectNodeDevelopmentTopicDO topic = new ProjectNodeDevelopmentTopicDO();
        topic.setId(id);
        topic.setProjectId(1L);
        topic.setNodeId(10L);
        topic.setTitle(title);
        topic.setTestStatus("TESTING");
        topic.setSort(0);
        return topic;
    }

    private ProjectNodeDevelopmentStoryDO story(Long topicId, String title, String status, int progress) {
        ProjectNodeDevelopmentStoryDO story = new ProjectNodeDevelopmentStoryDO();
        story.setTopicId(topicId);
        story.setTitle(title);
        story.setStatus(status);
        story.setProgress(progress);
        story.setStoryPoints(5);
        story.setBlocker("BLOCKED".equals(status) ? "需要确认" : null);
        return story;
    }

}
