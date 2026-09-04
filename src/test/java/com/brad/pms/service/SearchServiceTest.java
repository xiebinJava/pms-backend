package com.brad.pms.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.brad.pms.dto.response.SearchResultDTO;
import com.brad.pms.entity.ProjectCommentDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectMilestoneDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.mapper.ProjectCommentMapper;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectMilestoneMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock ProjectService projectService;
    @Mock ProjectMapper projectMapper;
    @Mock ProjectTaskMapper taskMapper;
    @Mock ProjectMilestoneMapper milestoneMapper;
    @Mock ProjectCommentMapper commentMapper;

    @InjectMocks SearchService searchService;

    @BeforeEach
    void init() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        TableInfoHelper.initTableInfo(assistant, ProjectDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectTaskDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectMilestoneDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectCommentDO.class);
    }

    @Test
    void shortOrBlankQueriesReturnEmptyWithoutTouchingMappers() {
        SearchResultDTO empty = searchService.search(" a", 8);
        assertThat(empty.getProjects()).isEmpty();
        assertThat(empty.getTasks()).isEmpty();
        assertThat(empty.getMilestones()).isEmpty();
        assertThat(empty.getComments()).isEmpty();
        verify(projectService, never()).listReadableIds();
    }

    @Test
    void emptyReadableScopeReturnsEmptyGroups() {
        when(projectService.listReadableIds()).thenReturn(List.of());
        SearchResultDTO empty = searchService.search("接口", 8);
        assertThat(empty.getProjects()).isEmpty();
        verify(projectMapper, never()).selectList(any());
    }

    @Test
    void searchAssemblesScopedHits() {
        when(projectService.listReadableIds()).thenReturn(List.of(9L));
        ProjectDO project = new ProjectDO();
        project.setId(9L);
        project.setName("研发门户");
        project.setCode("PRJ-000009");
        when(projectMapper.selectList(any())).thenReturn(List.of(project));
        ProjectTaskDO task = new ProjectTaskDO();
        task.setId(3L);
        task.setProjectId(9L);
        task.setTitle("补齐接口文档");
        task.setDescription("先对齐路径");
        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        ProjectMilestoneDO milestone = new ProjectMilestoneDO();
        milestone.setId(4L);
        milestone.setProjectId(9L);
        milestone.setTitle("接口冻结");
        milestone.setDueDate(LocalDate.of(2026, 9, 20));
        when(milestoneMapper.selectList(any())).thenReturn(List.of(milestone));
        ProjectCommentDO comment = new ProjectCommentDO();
        comment.setId(5L);
        comment.setProjectId(9L);
        comment.setTaskId(3L);
        comment.setContent("接口路径已确认");
        when(commentMapper.selectList(any())).thenReturn(List.of(comment));

        SearchResultDTO result = searchService.search("接口", 8);

        assertThat(result.getProjects()).extracting(hit -> hit.getTitle()).containsExactly("研发门户");
        assertThat(result.getTasks()).extracting(hit -> hit.getTaskId()).containsExactly(3L);
        assertThat(result.getMilestones()).extracting(hit -> hit.getMilestoneId()).containsExactly(4L);
        assertThat(result.getMilestones().get(0).getSnippet()).isEqualTo("2026-09-20");
        assertThat(result.getComments()).extracting(hit -> hit.getSnippet()).containsExactly("接口路径已确认");
        assertThat(result.getTasks().get(0).getProjectName()).isEqualTo("研发门户");
    }

    @Test
    void companyWideReadDoesNotMaterializeEveryProjectId() {
        when(projectService.hasAllCompanyProjectRead()).thenReturn(true);
        when(projectMapper.selectList(any())).thenReturn(List.of());
        when(taskMapper.selectList(any())).thenReturn(List.of());
        when(milestoneMapper.selectList(any())).thenReturn(List.of());
        when(commentMapper.selectList(any())).thenReturn(List.of());

        SearchResultDTO result = searchService.search("接口", 8);

        assertThat(result.getProjects()).isEmpty();
        verify(projectService, never()).listReadableIds();
        verify(projectMapper).selectList(any());
    }
}
