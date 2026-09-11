package com.brad.pms.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.dto.response.WorkbenchDTO;
import com.brad.pms.dto.response.WorkbenchSummaryDTO;
import com.brad.pms.entity.ProjectCommentDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectMemberDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectCommentMapper;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectMemberMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkbenchServiceTest {

    @Mock ProjectService projectService;
    @Mock ProjectMapper projectMapper;
    @Mock ProjectMemberMapper memberMapper;
    @Mock ProjectTaskMapper taskMapper;
    @Mock ProjectCommentMapper commentMapper;
    @Mock UserService userService;

    @InjectMocks WorkbenchService workbenchService;

    @BeforeEach
    void initMybatisLambdaCaches() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        TableInfoHelper.initTableInfo(assistant, ProjectDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectMemberDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectTaskDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectCommentDO.class);
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void summarizeCountsAllAssignedTasksAndDueSoonWindow() {
        List<ProjectTaskDO> tasks = List.of(
                task(1L, 10L, 0, 2, LocalDate.of(2026, 9, 3)),
                task(2L, 10L, 1, 3, LocalDate.of(2026, 9, 1)),
                task(3L, 10L, 2, 1, LocalDate.of(2026, 9, 2)),
                task(4L, 10L, 0, 1, LocalDate.of(2026, 8, 20))
        );

        WorkbenchSummaryDTO summary = WorkbenchService.summarize(tasks, 3, LocalDate.of(2026, 9, 1));

        assertThat(summary.getPendingTaskCount()).isEqualTo(2);
        assertThat(summary.getInProgressTaskCount()).isEqualTo(1);
        assertThat(summary.getDueSoonTaskCount()).isEqualTo(2);
        assertThat(summary.getParticipatingProjectCount()).isEqualTo(3);
    }

    @Test
    void taskItemsSortByStatusDueDatePriorityAndTitle() {
        ProjectDTO project = project(10L, "研发门户", "PRJ-000001");
        List<ProjectTaskDO> tasks = List.of(
                task(2L, 10L, 1, 3, LocalDate.of(2026, 8, 28), "完成联调"),
                task(1L, 10L, 0, 2, LocalDate.of(2026, 8, 29), "补齐接口文档")
        );

        assertThat(WorkbenchService.toTaskItems(tasks, Map.of(10L, project)))
                .extracting(item -> item.getId())
                .containsExactly(1L, 2L);
        assertThat(WorkbenchService.toTaskItems(tasks, Map.of(10L, project)).get(0).getProjectName())
                .isEqualTo("研发门户");
    }

    @Test
    void loadKeepsMembershipProjectsFiltersUnreadablesAndSlicesLists() {
        UserContext.set(new LoginUser(7L, "Alex.Zhang", "张伟", 0, "张伟", "张伟（Alex.Zhang）", 99L));
        when(memberMapper.selectList(any())).thenReturn(List.of(member(10L, 7L)));
        when(taskMapper.selectList(any())).thenReturn(List.of(
                task(1L, 10L, 0, 2, LocalDate.of(2026, 9, 3), "成员项目任务"),
                task(2L, 20L, 0, 2, LocalDate.of(2026, 9, 2), "范围外任务")
        ));
        when(projectMapper.selectList(any())).thenReturn(List.of(owned(30L)));
        when(projectService.listReadableByIds(any())).thenReturn(List.of(
                project(10L, "成员项目", "PRJ-10"),
                project(30L, "负责项目", "PRJ-30")
        ));
        when(commentMapper.selectList(any())).thenReturn(List.of(
                comment(5L, 10L, 8L, "先对齐接口", LocalDateTime.of(2026, 9, 1, 10, 0)),
                comment(4L, 10L, 7L, "已收到", LocalDateTime.of(2026, 8, 31, 10, 0))
        ));
        UserDO actor = new UserDO();
        actor.setId(8L);
        actor.setUsername("Alex.Zhang");
        actor.setNameZh("张伟");
        when(userService.listByIds(any())).thenReturn(List.of(actor));

        WorkbenchDTO dto = workbenchService.load();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> candidateCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(projectService).listReadableByIds(candidateCaptor.capture());
        assertThat(candidateCaptor.getValue()).containsExactlyInAnyOrder(10L, 20L, 30L);

        assertThat(dto.getSummary().getParticipatingProjectCount()).isEqualTo(2);
        assertThat(dto.getSummary().getPendingTaskCount()).isEqualTo(1);
        assertThat(dto.getProjects()).extracting(ProjectDTO::getId).containsExactly(10L, 30L);
        assertThat(dto.getTasks()).extracting(item -> item.getTitle()).containsExactly("成员项目任务");
        assertThat(dto.getActivities()).hasSize(2);
        assertThat(dto.getActivities().get(0).getActorName()).isEqualTo("张伟（Alex.Zhang）");
        assertThat(dto.getActivities().get(0).getProjectName()).isEqualTo("成员项目");
    }

    @Test
    void loadReturnsEmptyPayloadWhenUserHasNoProjects() {
        UserContext.set(new LoginUser(7L, "Alex.Zhang", "张伟"));
        when(memberMapper.selectList(any())).thenReturn(List.of());
        when(taskMapper.selectList(any())).thenReturn(List.of());
        when(projectMapper.selectList(any())).thenReturn(List.of());
        when(projectService.listReadableByIds(any())).thenReturn(List.of());

        WorkbenchDTO dto = workbenchService.load();

        assertThat(dto.getSummary().getParticipatingProjectCount()).isZero();
        assertThat(dto.getTasks()).isEmpty();
        assertThat(dto.getProjects()).isEmpty();
        assertThat(dto.getActivities()).isEmpty();
    }

    @Test
    void summaryUsesFullTaskSetEvenWhenDisplayListIsSliced() {
        UserContext.set(new LoginUser(7L, "Alex.Zhang", "张伟"));
        List<ProjectTaskDO> assigned = new ArrayList<>();
        List<ProjectDTO> projects = new ArrayList<>();
        for (long index = 1; index <= 10; index++) {
            assigned.add(task(index, index, 0, 1, LocalDate.of(2026, 9, 2), "任务" + index));
            projects.add(project(index, "项目" + index, "PRJ-" + index));
        }
        when(memberMapper.selectList(any())).thenReturn(List.of());
        when(taskMapper.selectList(any())).thenReturn(assigned);
        when(projectMapper.selectList(any())).thenReturn(List.of());
        when(projectService.listReadableByIds(any())).thenReturn(projects);
        when(commentMapper.selectList(any())).thenReturn(List.of());

        WorkbenchDTO dto = workbenchService.load();

        assertThat(dto.getSummary().getPendingTaskCount()).isEqualTo(10);
        assertThat(dto.getSummary().getParticipatingProjectCount()).isEqualTo(10);
        assertThat(dto.getTasks()).hasSize(8);
        assertThat(dto.getProjects()).hasSize(6);
    }

    private static ProjectMemberDO member(Long projectId, Long userId) {
        ProjectMemberDO member = new ProjectMemberDO();
        member.setProjectId(projectId);
        member.setUserId(userId);
        return member;
    }

    private static ProjectDO owned(Long id) {
        ProjectDO project = new ProjectDO();
        project.setId(id);
        project.setOwnerId(7L);
        return project;
    }

    private static ProjectDTO project(Long id, String name, String code) {
        ProjectDTO dto = new ProjectDTO();
        dto.setId(id);
        dto.setName(name);
        dto.setCode(code);
        return dto;
    }

    private static ProjectTaskDO task(Long id, Long projectId, int status, int priority, LocalDate dueDate) {
        return task(id, projectId, status, priority, dueDate, "任务" + id);
    }

    private static ProjectTaskDO task(Long id, Long projectId, int status, int priority, LocalDate dueDate, String title) {
        ProjectTaskDO task = new ProjectTaskDO();
        task.setId(id);
        task.setProjectId(projectId);
        task.setStatus(status);
        task.setPriority(priority);
        task.setDueDate(dueDate);
        task.setTitle(title);
        task.setAssigneeId(7L);
        return task;
    }

    private static ProjectCommentDO comment(Long id, Long projectId, Long userId, String content, LocalDateTime createdAt) {
        ProjectCommentDO comment = new ProjectCommentDO();
        comment.setId(id);
        comment.setProjectId(projectId);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setCreatedAt(createdAt);
        return comment;
    }
}
