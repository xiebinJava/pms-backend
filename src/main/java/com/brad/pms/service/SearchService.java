package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.dto.response.SearchHitDTO;
import com.brad.pms.dto.response.SearchResultDTO;
import com.brad.pms.entity.ProjectCommentDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.mapper.ProjectCommentMapper;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class SearchService {

    static final int DEFAULT_LIMIT = 8;
    static final int MAX_LIMIT = 20;
    static final int MIN_QUERY = 2;

    private final ProjectService projectService;
    private final ProjectMapper projectMapper;
    private final ProjectTaskMapper taskMapper;
    private final ProjectCommentMapper commentMapper;

    public SearchResultDTO search(String query, int limit) {
        SearchResultDTO result = new SearchResultDTO();
        String keyword = query == null ? "" : query.trim();
        if (keyword.length() < MIN_QUERY) return result;
        int size = NotificationService.clamp(limit, DEFAULT_LIMIT, MAX_LIMIT);
        List<Long> projectIds = projectService.listReadableIds();
        if (projectIds.isEmpty()) return result;

        List<ProjectDO> projects = projectMapper.selectList(new LambdaQueryWrapper<ProjectDO>()
                .in(ProjectDO::getId, projectIds)
                .and(wrapper -> wrapper.like(ProjectDO::getName, keyword).or().like(ProjectDO::getCode, keyword))
                .orderByDesc(ProjectDO::getUpdatedAt)
                .last("LIMIT " + size));
        List<ProjectTaskDO> tasks = taskMapper.selectList(new LambdaQueryWrapper<ProjectTaskDO>()
                .in(ProjectTaskDO::getProjectId, projectIds)
                .and(wrapper -> wrapper.like(ProjectTaskDO::getTitle, keyword)
                        .or().like(ProjectTaskDO::getDescription, keyword))
                .orderByDesc(ProjectTaskDO::getUpdatedAt)
                .last("LIMIT " + size));
        List<ProjectCommentDO> comments = commentMapper.selectList(new LambdaQueryWrapper<ProjectCommentDO>()
                .in(ProjectCommentDO::getProjectId, projectIds)
                .like(ProjectCommentDO::getContent, keyword)
                .orderByDesc(ProjectCommentDO::getCreatedAt)
                .last("LIMIT " + size));

        Map<Long, ProjectDO> names = projectNames(projectIds, projects, tasks, comments);
        result.setProjects(projects.stream().map(project -> {
            SearchHitDTO hit = new SearchHitDTO();
            hit.setId(project.getId());
            hit.setProjectId(project.getId());
            hit.setProjectName(project.getName());
            hit.setTitle(project.getName());
            hit.setSnippet(project.getCode());
            return hit;
        }).collect(Collectors.toList()));
        result.setTasks(tasks.stream().map(task -> {
            SearchHitDTO hit = new SearchHitDTO();
            hit.setId(task.getId());
            hit.setProjectId(task.getProjectId());
            hit.setProjectName(nameOf(names, task.getProjectId()));
            hit.setTitle(task.getTitle());
            hit.setSnippet(NotificationService.truncate(task.getDescription(), 80));
            hit.setTaskId(task.getId());
            return hit;
        }).collect(Collectors.toList()));
        result.setComments(comments.stream().map(comment -> {
            SearchHitDTO hit = new SearchHitDTO();
            hit.setId(comment.getId());
            hit.setProjectId(comment.getProjectId());
            hit.setProjectName(nameOf(names, comment.getProjectId()));
            hit.setTitle(nameOf(names, comment.getProjectId()));
            hit.setSnippet(NotificationService.truncate(comment.getContent(), 80));
            hit.setTaskId(comment.getTaskId());
            return hit;
        }).collect(Collectors.toList()));
        return result;
    }

    private Map<Long, ProjectDO> projectNames(List<Long> readableIds,
                                              List<ProjectDO> matchedProjects,
                                              List<ProjectTaskDO> tasks,
                                              List<ProjectCommentDO> comments) {
        List<Long> extra = Stream.concat(
                        tasks.stream().map(ProjectTaskDO::getProjectId),
                        comments.stream().map(ProjectCommentDO::getProjectId))
                .filter(Objects::nonNull)
                .distinct()
                .filter(id -> matchedProjects.stream().noneMatch(project -> Objects.equals(project.getId(), id)))
                .collect(Collectors.toList());
        Map<Long, ProjectDO> names = matchedProjects.stream()
                .filter(project -> project.getId() != null)
                .collect(Collectors.toMap(ProjectDO::getId, Function.identity(), (left, right) -> left));
        if (!extra.isEmpty()) {
            projectMapper.selectList(new LambdaQueryWrapper<ProjectDO>()
                            .in(ProjectDO::getId, extra)
                            .in(ProjectDO::getId, readableIds))
                    .forEach(project -> names.put(project.getId(), project));
        }
        return names;
    }

    private static String nameOf(Map<Long, ProjectDO> names, Long projectId) {
        ProjectDO project = names.get(projectId);
        if (project == null || !StringUtils.hasText(project.getName())) return "未命名项目";
        return project.getName();
    }
}
