package com.brad.pms.service;

import com.brad.pms.dto.response.ProjectDTO;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.entity.ProjectNodeIterationPlanDO;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemVersionRelatedItemServiceTest {

    @Mock ProjectNodeIterationPlanMapper iterationPlanMapper;
    @Mock ProjectNodeDevelopmentStoryMapper storyMapper;
    @Mock ProjectNodeDevelopmentTopicMapper topicMapper;
    @Mock ProjectService projectService;
    @Mock UserService userService;

    @InjectMocks SystemVersionRelatedItemService service;

    @BeforeEach
    void initTableMetadata() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "system-version-related-test");
        TableInfoHelper.initTableInfo(assistant, ProjectNodeIterationPlanDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectNodeDevelopmentStoryDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectNodeDevelopmentTopicDO.class);
    }

    @Test
    void buildsProjectTopicStoryTreeAndDeduplicatesStoriesAcrossIterations() {
        ProjectNodeIterationPlanDO firstPlan = plan(101L, 10L, "十月迭代", LocalDate.of(2026, 10, 1));
        ProjectNodeIterationPlanDO secondPlan = plan(102L, 10L, "十一月迭代", LocalDate.of(2026, 11, 1));
        ProjectNodeIterationPlanDO independentPlan = plan(103L, null, "独立迭代", LocalDate.of(2026, 12, 1));
        ProjectNodeDevelopmentStoryDO repeatedStory = story(201L, 10L, 501L, 101L, "系统登录");
        ProjectNodeDevelopmentStoryDO sameStoryInAnotherIteration = story(201L, 10L, 501L, 102L, "系统登录");
        ProjectNodeDevelopmentStoryDO independentStory = story(202L, null, null, 103L, "独立故事");
        ProjectNodeDevelopmentTopicDO topic = new ProjectNodeDevelopmentTopicDO();
        topic.setId(501L);
        topic.setProjectId(10L);
        topic.setTitle("账号中心");
        topic.setDeleted(false);

        ProjectDTO project = new ProjectDTO();
        project.setId(10L);
        project.setCode("PRJ-001");
        project.setName("用户中心");
        project.setOwnerName("张三");

        when(iterationPlanMapper.selectList(any())).thenReturn(List.of(firstPlan, secondPlan, independentPlan));
        when(storyMapper.selectList(any())).thenReturn(List.of(repeatedStory, sameStoryInAnotherIteration, independentStory));
        when(topicMapper.selectList(any())).thenReturn(List.of(topic));
        when(projectService.listReadableIds()).thenReturn(List.of(10L));
        when(projectService.listReadableByIds(any())).thenReturn(List.of(project));
        var result = service.load(4L);

        assertThat(result.getProjects()).hasSize(1);
        assertThat(result.getProjects().get(0).getTopics()).hasSize(1);
        assertThat(result.getProjects().get(0).getTopics().get(0).getStories()).hasSize(1);
        var relatedStory = result.getProjects().get(0).getTopics().get(0).getStories().get(0);
        assertThat(relatedStory.getId()).isEqualTo(201L);
        assertThat(relatedStory.getSourceIterations()).extracting(item -> item.getName())
                .containsExactly("十月迭代", "十一月迭代");
        assertThat(result.getUnboundStories()).extracting(item -> item.getTitle())
                .containsExactly("独立故事");
    }

    @Test
    void returnsEmptyGroupsWhenVersionHasNoIterationPlans() {
        when(iterationPlanMapper.selectList(any())).thenReturn(List.of());

        var result = service.load(4L);

        assertThat(result.getProjects()).isEmpty();
        assertThat(result.getUnboundTopics()).isEmpty();
        assertThat(result.getUnboundStories()).isEmpty();
    }

    private static ProjectNodeIterationPlanDO plan(Long id, Long projectId, String name, LocalDate startDate) {
        ProjectNodeIterationPlanDO plan = new ProjectNodeIterationPlanDO();
        plan.setId(id);
        plan.setProjectId(projectId);
        plan.setSystemVersionId(4L);
        plan.setName(name);
        plan.setStatus("PLANNED");
        plan.setStartDate(startDate);
        plan.setDueDate(startDate.plusDays(14));
        return plan;
    }

    private static ProjectNodeDevelopmentStoryDO story(Long id, Long projectId, Long topicId,
                                                         Long iterationPlanId, String title) {
        ProjectNodeDevelopmentStoryDO story = new ProjectNodeDevelopmentStoryDO();
        story.setId(id);
        story.setProjectId(projectId);
        story.setTopicId(topicId);
        story.setIterationPlanId(iterationPlanId);
        story.setTitle(title);
        story.setStatus("IN_PROGRESS");
        story.setProgress(40);
        return story;
    }
}
