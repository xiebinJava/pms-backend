package com.brad.pms.ai.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.enums.TaskStatus;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.service.ProjectService;
import com.brad.pms.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiTaskQueryServiceTest {

    @Mock ProjectTaskMapper taskMapper;
    @Mock ProjectService projectService;
    @Mock ProjectNodeMapper nodeMapper;
    @Mock UserService userService;

    @BeforeEach
    void initMybatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "ai-task-query-test");
        TableInfoHelper.initTableInfo(assistant, ProjectTaskDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectNodeDO.class);
    }

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void mineTodayOpenUsesAuthenticatedUserAndReturnsAuthoritativeMetadata() {
        UserContext.set(new LoginUser(7L, "alex", "张伟"));
        when(projectService.listReadableIds()).thenReturn(List.of(11L));
        ProjectTaskDO task = task(101L, 11L, 21L, 7L, LocalDate.now(), TaskStatus.TODO.getCode());
        when(taskMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> {
                    Page<ProjectTaskDO> page = invocation.getArgument(0);
                    page.setTotal(1);
                    page.setRecords(List.of(task));
                    return page;
                });
        when(projectService.listReadableByIds(anyCollection())).thenReturn(List.of(project(11L)));
        when(nodeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(node(21L, "需求澄清")));
        when(userService.listByIds(anyCollection())).thenReturn(List.of(user(7L, "张伟")));

        AiTaskQueryResult result = service().query(new AiTaskQueryRequest(
                "mine", "today", "open", null, null, 1, 50));

        assertThat(result.authoritative()).isTrue();
        assertThat(result.dataScope()).isEqualTo("task-query");
        assertThat(result.timezone()).isEqualTo("Asia/Shanghai");
        assertThat(result.asOfDate()).isEqualTo(LocalDate.now());
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.tasks()).singleElement().satisfies(item -> {
            assertThat(item.title()).isEqualTo("任务 101");
            assertThat(item.projectName()).isEqualTo("项目 11");
            assertThat(item.nodeName()).isEqualTo("需求澄清");
            assertThat(item.assigneeName()).contains("张伟");
            assertThat(item.statusLabel()).isEqualTo("待办");
        });

        ArgumentCaptor<LambdaQueryWrapper<ProjectTaskDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(taskMapper).selectPage(any(Page.class), captor.capture());
        assertThat(captor.getValue().getSqlSegment())
                .contains("assigneeId", "dueDate", "status");
    }

    @Test
    void projectScopeRejectsUnreadableProjectBeforeQueryingTasks() {
        UserContext.set(new LoginUser(7L, "alex", "张伟"));
        when(projectService.listReadableIds()).thenReturn(List.of(11L));

        assertThatThrownBy(() -> service().query(new AiTaskQueryRequest(
                "project", "any", "all", 99L, null, 1, 50)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权读取");
    }

    @Test
    void requestUserIdentityCannotBeProvidedToMineQuery() {
        UserContext.set(new LoginUser(7L, "alex", "张伟"));
        when(projectService.listReadableIds()).thenReturn(List.of(11L));
        when(taskMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service().query(new AiTaskQueryRequest("mine", "any", "all", null, null, 1, 50));

        ArgumentCaptor<LambdaQueryWrapper<ProjectTaskDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(taskMapper).selectPage(any(Page.class), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("assigneeId");
    }

    private AiTaskQueryService service() {
        return new AiTaskQueryService(taskMapper, projectService, nodeMapper, userService);
    }

    private static ProjectTaskDO task(Long id, Long projectId, Long nodeId, Long assigneeId,
                                      LocalDate dueDate, Integer status) {
        ProjectTaskDO task = new ProjectTaskDO();
        task.setId(id);
        task.setProjectId(projectId);
        task.setNodeId(nodeId);
        task.setAssigneeId(assigneeId);
        task.setTitle("任务 " + id);
        task.setDueDate(dueDate);
        task.setStatus(status);
        task.setPriority(3);
        task.setVersion(1);
        return task;
    }

    private static ProjectDTO project(Long id) {
        ProjectDTO project = new ProjectDTO();
        project.setId(id);
        project.setCode("PRJ-" + id);
        project.setName("项目 " + id);
        return project;
    }

    private static ProjectNodeDO node(Long id, String name) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(id);
        node.setName(name);
        node.setNodeKey("node-" + id);
        return node;
    }

    private static UserDO user(Long id, String name) {
        UserDO user = new UserDO();
        user.setId(id);
        user.setNameZh(name);
        user.setUsername("user-" + id);
        return user;
    }
}
