package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import com.brad.pms.dto.request.DevelopmentItemPageQry;
import com.brad.pms.dto.response.DevelopmentStoryListDTO;
import com.brad.pms.dto.response.DevelopmentTopicListDTO;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import com.brad.pms.entity.DevelopmentItemWorkflowNodeDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.entity.ProjectNodeIterationPlanDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowNodeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevelopmentItemServiceTest {

    @Mock ProjectService projectService;
    @Mock ProjectMapper projectMapper;
    @Mock ProjectPermissionService permissionService;
    @Mock ProjectNodeMapper nodeMapper;
    @Mock ProjectNodeDevelopmentTopicMapper topicMapper;
    @Mock ProjectNodeDevelopmentStoryMapper storyMapper;
    @Mock ProjectNodeIterationPlanMapper iterationPlanMapper;
    @Mock UserService userService;
    @Mock DevelopmentItemWorkflowMapper workflowMapper;
    @Mock DevelopmentItemWorkflowNodeMapper workflowNodeMapper;
    @Mock WorkflowTemplateService workflowTemplateService;
    @Mock RequirementExecutionTargetReadService requirementTargetReadService;

    @InjectMocks DevelopmentItemService service;

    @BeforeEach
    void initTableInfo() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        TableInfoHelper.initTableInfo(assistant, ProjectNodeDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectNodeDevelopmentTopicDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectNodeDevelopmentStoryDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectNodeIterationPlanDO.class);
        TableInfoHelper.initTableInfo(assistant, DevelopmentItemWorkflowDO.class);
        TableInfoHelper.initTableInfo(assistant, DevelopmentItemWorkflowNodeDO.class);
    }

    @Test
    void listsTopicsWithProjectNodeOwnerAndStorySummary() {
        ProjectDTO project = project(7L, "PMS 重构");
        ProjectNodeDO node = node(70L, 7L, "开发测试与项目控制", 4);
        ProjectNodeDevelopmentTopicDO topic = topic(701L, 7L, 70L, "登录改造", 9L);
        ProjectNodeDevelopmentStoryDO done = story(801L, 7L, 70L, 701L, "接入统一认证", 9L, "DONE", 100);
        ProjectNodeDevelopmentStoryDO blocked = story(802L, 7L, 70L, 701L, "补齐异常分支", null, "BLOCKED", 30);
        blocked.setBlocker("等待接口联调");
        stubData(List.of(project), List.of(node), List.of(topic), List.of(done, blocked), List.of());

        DevelopmentItemPageQry query = new DevelopmentItemPageQry();
        query.setPageSize(10);

        DevelopmentTopicListDTO result = service.pageTopics(query).getList().get(0);

        assertThat(result.getTitle()).isEqualTo("登录改造");
        assertThat(result.getProjectName()).isEqualTo("PMS 重构");
        assertThat(result.getNodeName()).isEqualTo("开发测试与项目控制");
        assertThat(result.getOwnerName()).isEqualTo("张伟（alex.zhang）");
        assertThat(result.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(result.getProgress()).isZero();
        assertThat(result.getDevelopmentProgress()).isEqualTo(65);
        assertThat(result.getStoryCount()).isEqualTo(2);
        assertThat(result.getCompletedStoryCount()).isEqualTo(1);
        assertThat(result.getBlockedStoryCount()).isEqualTo(1);
    }

    @Test
    void listsStoriesWithFiltersAndIterationPlanName() {
        ProjectDTO project = project(7L, "PMS 重构");
        ProjectNodeDO node = node(70L, 7L, "开发测试与项目控制", 4);
        ProjectNodeDevelopmentTopicDO topic = topic(701L, 7L, 70L, "登录改造", null);
        ProjectNodeDevelopmentStoryDO story = story(801L, 7L, 70L, 701L, "接入统一认证", 9L, "IN_PROGRESS", 60);
        story.setIterationPlanId(901L);
        story.setStartDate(LocalDate.of(2026, 9, 22));
        story.setDueDate(LocalDate.of(2026, 9, 30));
        ProjectNodeIterationPlanDO plan = new ProjectNodeIterationPlanDO();
        plan.setId(901L);
        plan.setProjectId(7L);
        plan.setName("第一个迭代");
        stubData(List.of(project), List.of(node), List.of(topic), List.of(story), List.of(plan));

        DevelopmentItemPageQry query = new DevelopmentItemPageQry();
        query.setKeyword("统一认证");
        query.setStatus("IN_PROGRESS");
        query.setOwnerId(9L);

        DevelopmentStoryListDTO result = service.pageStories(query).getList().get(0);

        assertThat(result.getTitle()).isEqualTo("接入统一认证");
        assertThat(result.getTopicTitle()).isEqualTo("登录改造");
        assertThat(result.getIterationPlanName()).isEqualTo("第一个迭代");
        assertThat(result.getProjectId()).isEqualTo(7L);
        assertThat(result.getNodeId()).isEqualTo(70L);
        assertThat(result.getOwnerName()).isEqualTo("张伟（alex.zhang）");
        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void doesNotListDataFromProjectsOutsideReadableScope() {
        when(projectService.listReadableIds()).thenReturn(List.of(7L));
        DevelopmentItemPageQry query = new DevelopmentItemPageQry();
        query.setProjectId(8L);

        assertThat(service.pageTopics(query).getList()).isEmpty();
        assertThat(service.pageStories(query).getList()).isEmpty();
    }

    @Test
    void activeTopicAndStoryPagesFilterDeletedTopicsAndTheirStories() {
        ProjectDTO project = project(7L, "PMS 重构");
        ProjectNodeDO node = node(70L, 7L, "开发测试与项目控制", 4);
        ProjectNodeDevelopmentTopicDO activeTopic = topic(701L, 7L, 70L, "有效专题", null);
        ProjectNodeDevelopmentTopicDO deletedTopic = topic(702L, 7L, 70L, "已删除专题", null);
        deletedTopic.setDeleted(true);
        ProjectNodeDevelopmentStoryDO story = story(801L, 7L, 70L, 701L, "有效故事", null, "NOT_STARTED", 0);
        when(projectService.listReadableIds()).thenReturn(List.of(7L));
        when(projectService.listReadableByIds(any())).thenReturn(List.of(project));
        when(nodeMapper.selectList(any())).thenReturn(List.of(node));
        when(topicMapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<ProjectNodeDevelopmentTopicDO> query = invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("deleted");
            return List.of(activeTopic);
        });
        when(storyMapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<ProjectNodeDevelopmentStoryDO> query = invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("topicId IN");
            assertThat(query.getParamNameValuePairs().values()).contains(701L).doesNotContain(702L);
            return List.of(story);
        });
        when(iterationPlanMapper.selectList(any())).thenReturn(List.of());

        DevelopmentItemPageQry query = new DevelopmentItemPageQry();

        assertThat(service.pageTopics(query).getList()).extracting(DevelopmentTopicListDTO::getId).containsExactly(701L);
        assertThat(service.pageStories(query).getList()).extracting(DevelopmentStoryListDTO::getId).containsExactly(801L);
    }

    @Test
    void listsIndependentTopicsAndStoriesWithoutReadableProjects() {
        ProjectNodeDevelopmentTopicDO topic = topic(701L, null, null, "独立专题", null);
        ProjectNodeDevelopmentStoryDO topicStory = story(801L, null, null, 701L, "专题故事", null, "IN_PROGRESS", 20);
        ProjectNodeDevelopmentStoryDO independentStory = story(802L, null, null, null, "独立故事", null, "NOT_STARTED", 0);
        when(projectService.listReadableIds()).thenReturn(List.of());
        when(topicMapper.selectUnbound(false)).thenReturn(List.of(topic));
        when(storyMapper.selectList(any())).thenReturn(List.of(topicStory));
        when(storyMapper.selectIndependent()).thenReturn(List.of(independentStory));
        when(workflowMapper.selectList(any())).thenReturn(List.of());
        when(workflowTemplateService.resolveDefaultForProcessType(any())).thenReturn(null);

        DevelopmentItemPageQry query = new DevelopmentItemPageQry();

        assertThat(service.pageTopics(query).getList()).extracting(DevelopmentTopicListDTO::getId)
                .containsExactly(701L);
        assertThat(service.pageStories(query).getList()).extracting(DevelopmentStoryListDTO::getId)
                .containsExactly(802L, 801L);
        assertThat(service.pageTopics(query).getList().get(0).getProjectId()).isNull();
        assertThat(service.pageStories(query).getList()).allSatisfy(item -> assertThat(item.getProjectId()).isNull());
    }

    @Test
    void deletedTopicScopeCanDisplayTopicsFromManageableClosedProjects() {
        ProjectNodeDevelopmentTopicDO deletedTopic = topic(702L, 7L, 70L, "已删除专题", null);
        deletedTopic.setDeleted(true);
        ProjectDO closedProject = new ProjectDO();
        closedProject.setId(7L);
        closedProject.setCode("PRJ-000007");
        closedProject.setName("已归档项目");
        closedProject.setStatus(4);
        ProjectNodeDO node = node(70L, 7L, "开发测试与项目控制", 4);
        ProjectNodeDevelopmentStoryDO preservedStory = story(801L, 7L, 70L, 702L, "保留的故事", null, "IN_PROGRESS", 40);
        when(topicMapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<ProjectNodeDevelopmentTopicDO> query = invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("deleted");
            return List.of(deletedTopic);
        });
        when(projectMapper.selectIncludingDeletedByIds(any())).thenReturn(List.of(closedProject));
        when(permissionService.canManageProject(closedProject)).thenReturn(true);
        when(nodeMapper.selectList(any())).thenReturn(List.of(node));
        when(storyMapper.selectList(any())).thenReturn(List.of(preservedStory));
        when(iterationPlanMapper.selectList(any())).thenReturn(List.of());

        DevelopmentItemPageQry query = new DevelopmentItemPageQry();
        query.setDeleted(true);

        DevelopmentTopicListDTO result = service.pageTopics(query).getList().get(0);

        assertThat(result.getTitle()).isEqualTo("已删除专题");
        assertThat(result.getProjectName()).isEqualTo("已归档项目");
        assertThat(result.getStoryCount()).isEqualTo(1);
    }

    private void stubData(List<ProjectDTO> projects,
                          List<ProjectNodeDO> nodes,
                          List<ProjectNodeDevelopmentTopicDO> topics,
                          List<ProjectNodeDevelopmentStoryDO> stories,
                          List<ProjectNodeIterationPlanDO> plans) {
        when(projectService.listReadableIds()).thenReturn(projects.stream().map(ProjectDTO::getId).toList());
        when(projectService.listReadableByIds(any())).thenReturn(projects);
        when(nodeMapper.selectList(any())).thenReturn(nodes);
        when(topicMapper.selectList(any())).thenReturn(topics);
        when(storyMapper.selectList(any())).thenReturn(stories);
        when(iterationPlanMapper.selectList(any())).thenReturn(plans);
        UserDO owner = new UserDO();
        owner.setId(9L);
        owner.setNameZh("张伟");
        owner.setUsername("alex.zhang");
        when(userService.listByIdsIncludingDeleted(any())).thenReturn(List.of(owner));
    }

    private ProjectDTO project(Long id, String name) {
        ProjectDTO project = new ProjectDTO();
        project.setId(id);
        project.setCode("PRJ-000007");
        project.setName(name);
        return project;
    }

    private ProjectNodeDO node(Long id, Long projectId, String name, int sort) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(id);
        node.setProjectId(projectId);
        node.setName(name);
        node.setNodeKey("development-control");
        node.setSort(sort);
        return node;
    }

    private ProjectNodeDevelopmentTopicDO topic(Long id, Long projectId, Long nodeId, String title, Long ownerId) {
        ProjectNodeDevelopmentTopicDO topic = new ProjectNodeDevelopmentTopicDO();
        topic.setId(id);
        topic.setProjectId(projectId);
        topic.setNodeId(nodeId);
        topic.setTitle(title);
        topic.setOwnerId(ownerId);
        topic.setSort(1);
        return topic;
    }

    private ProjectNodeDevelopmentStoryDO story(Long id, Long projectId, Long nodeId, Long topicId,
                                                 String title, Long ownerId, String status, int progress) {
        ProjectNodeDevelopmentStoryDO story = new ProjectNodeDevelopmentStoryDO();
        story.setId(id);
        story.setProjectId(projectId);
        story.setNodeId(nodeId);
        story.setTopicId(topicId);
        story.setTitle(title);
        story.setOwnerId(ownerId);
        story.setStatus(status);
        story.setProgress(progress);
        story.setStoryPoints(5);
        story.setSort(1);
        return story;
    }
}
