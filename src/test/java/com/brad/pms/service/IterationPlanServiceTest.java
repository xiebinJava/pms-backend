package com.brad.pms.service;

import com.brad.pms.dto.request.IterationPlanPageQry;
import com.brad.pms.dto.response.IterationPlanDetailDTO;
import com.brad.pms.dto.response.IterationPlanListDTO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.entity.ProjectNodeIterationPlanDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectNodePlanBaselineMapper;
import com.brad.pms.mapper.ProjectNodeSolutionDecisionMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.dto.response.ProjectDTO;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IterationPlanServiceTest {

    @Mock ProjectNodeIterationPlanMapper iterationPlanMapper;
    @Mock ProjectNodePlanBaselineMapper baselineMapper;
    @Mock ProjectNodeSolutionDecisionMapper decisionMapper;
    @Mock ProjectPermissionService permissionService;
    @Mock UserService userService;
    @Mock ProjectService projectService;
    @Mock ProjectNodeMapper nodeMapper;
    @Mock ProjectNodeDevelopmentTopicMapper topicMapper;
    @Mock ProjectNodeDevelopmentStoryMapper storyMapper;
    @Mock ProjectTaskMapper taskMapper;

    @InjectMocks IterationPlanService service;

    @BeforeEach
    void initTableMetadata() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        TableInfoHelper.initTableInfo(assistant, ProjectNodeIterationPlanDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectNodeDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectNodeDevelopmentStoryDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectNodeDevelopmentTopicDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectTaskDO.class);
    }

    @Test
    void pageAggregatesExplicitStoriesAndTasksWithoutResolvingAWorkflow() {
        ProjectNodeIterationPlanDO plan = plan();
        ProjectDTO project = project();
        ProjectNodeDevelopmentStoryDO story = story();
        ProjectTaskDO task = task(31L, null, 0);
        when(projectService.listReadableIds()).thenReturn(List.of(9L));
        when(iterationPlanMapper.selectList(any())).thenReturn(List.of(plan));
        when(projectService.listReadableByIds(any())).thenReturn(List.of(project));
        when(nodeMapper.selectList(any())).thenReturn(List.of(node()));
        when(storyMapper.selectList(any())).thenReturn(List.of(story));
        when(topicMapper.selectList(any())).thenReturn(List.of(topic()));
        when(taskMapper.selectList(any())).thenReturn(List.of(task));

        IterationPlanPageQry query = new IterationPlanPageQry();
        query.setPageSize(20);
        IterationPlanListDTO result = service.page(query).getList().get(0);

        assertThat(result.getProjectName()).isEqualTo("订单项目");
        assertThat(result.getNodeName()).isEqualTo("开发与测试");
        assertThat(result.getStoryCount()).isEqualTo(1);
        assertThat(result.getCompletedStoryCount()).isEqualTo(1);
        assertThat(result.getTaskCount()).isEqualTo(1);
        assertThat(result.getCompletedTaskCount()).isZero();
        assertThat(result.getProgress()).isEqualTo(100);
    }

    @Test
    void detailReturnsOnlyExplicitIterationMembersAndKeepsTaskHierarchy() {
        ProjectNodeIterationPlanDO plan = plan();
        ProjectNodeDevelopmentStoryDO story = story();
        ProjectTaskDO parent = task(31L, null, 0);
        ProjectTaskDO child = task(32L, 31L, 2);
        when(iterationPlanMapper.selectById(21L)).thenReturn(plan);
        when(permissionService.requireProjectReadable(9L)).thenReturn(projectDO());
        when(projectService.listReadableByIds(any())).thenReturn(List.of(project()));
        when(nodeMapper.selectList(any())).thenReturn(List.of(node()));
        when(storyMapper.selectList(any())).thenReturn(List.of(story));
        when(topicMapper.selectList(any())).thenReturn(List.of(topic()));
        when(taskMapper.selectList(any())).thenReturn(List.of(parent, child));

        IterationPlanDetailDTO result = service.detail(21L);

        assertThat(result.getPlan().getId()).isEqualTo(21L);
        assertThat(result.getStories()).extracting(item -> item.getId()).containsExactly(41L);
        assertThat(result.getTasks()).extracting(item -> item.getId()).containsExactly(31L, 32L);
        assertThat(result.getTasks().get(1).getParentId()).isEqualTo(31L);
    }

    private static ProjectNodeIterationPlanDO plan() {
        ProjectNodeIterationPlanDO plan = new ProjectNodeIterationPlanDO();
        plan.setId(21L);
        plan.setProjectId(9L);
        plan.setNodeId(3L);
        plan.setName("订单一期");
        plan.setStatus("IN_PROGRESS");
        plan.setSort(1);
        return plan;
    }

    private static ProjectDTO project() {
        ProjectDTO project = new ProjectDTO();
        project.setId(9L);
        project.setCode("PRJ-000009");
        project.setName("订单项目");
        return project;
    }

    private static ProjectDO projectDO() {
        ProjectDO project = new ProjectDO();
        project.setId(9L);
        project.setStatus(1);
        return project;
    }

    private static ProjectNodeDO node() {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(3L);
        node.setProjectId(9L);
        node.setName("开发与测试");
        return node;
    }

    private static ProjectNodeDevelopmentTopicDO topic() {
        ProjectNodeDevelopmentTopicDO topic = new ProjectNodeDevelopmentTopicDO();
        topic.setId(4L);
        topic.setTitle("订单专题");
        return topic;
    }

    private static ProjectNodeDevelopmentStoryDO story() {
        ProjectNodeDevelopmentStoryDO story = new ProjectNodeDevelopmentStoryDO();
        story.setId(41L);
        story.setProjectId(9L);
        story.setNodeId(3L);
        story.setTopicId(4L);
        story.setIterationPlanId(21L);
        story.setTitle("订单查询");
        story.setStatus("DONE");
        story.setProgress(0);
        return story;
    }

    private static ProjectTaskDO task(Long id, Long parentId, int status) {
        ProjectTaskDO task = new ProjectTaskDO();
        task.setId(id);
        task.setProjectId(9L);
        task.setNodeId(3L);
        task.setIterationPlanId(21L);
        task.setParentId(parentId);
        task.setTitle("任务" + id);
        task.setStatus(status);
        task.setPriority(1);
        return task;
    }
}
