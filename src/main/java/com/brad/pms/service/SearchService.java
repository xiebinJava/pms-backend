package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.dto.response.SearchHitDTO;
import com.brad.pms.dto.response.SearchResultDTO;
import com.brad.pms.entity.ProjectCommentDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectMilestoneDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.mapper.ProjectCommentMapper;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectMilestoneMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.common.enums.ProjectStatus;
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
    private final ProjectMilestoneMapper milestoneMapper;
    private final ProjectCommentMapper commentMapper;

    public SearchResultDTO search(String query, int limit) {
        SearchResultDTO result = new SearchResultDTO();
        String keyword = query == null ? "" : query.trim();
        if (keyword.length() < MIN_QUERY) return result;
        int size = NotificationService.clamp(limit, DEFAULT_LIMIT, MAX_LIMIT);
        boolean allCompany = projectService.hasAllCompanyProjectRead();
        List<Long> projectIds = allCompany ? List.of() : projectService.listReadableIds();
        if (!allCompany && projectIds.isEmpty()) return result;

        LambdaQueryWrapper<ProjectDO> projectQuery = new LambdaQueryWrapper<ProjectDO>()
                .ne(ProjectDO::getStatus, ProjectStatus.DELETED.getCode())
                .and(wrapper -> wrapper.like(ProjectDO::getName, keyword).or().like(ProjectDO::getCode, keyword))
                .orderByDesc(ProjectDO::getUpdatedAt)
                .last("LIMIT " + size);
        if (!allCompany) projectQuery.in(ProjectDO::getId, projectIds);
        List<ProjectDO> projects = projectMapper.selectList(projectQuery);

        LambdaQueryWrapper<ProjectTaskDO> taskQuery = new LambdaQueryWrapper<ProjectTaskDO>()
                .and(wrapper -> wrapper.like(ProjectTaskDO::getTitle, keyword)
                        .or().like(ProjectTaskDO::getDescription, keyword))
                .orderByDesc(ProjectTaskDO::getUpdatedAt)
                .last("LIMIT " + size);
        LambdaQueryWrapper<ProjectMilestoneDO> milestoneQuery = new LambdaQueryWrapper<ProjectMilestoneDO>()
                .and(wrapper -> wrapper.like(ProjectMilestoneDO::getTitle, keyword)
                        .or().like(ProjectMilestoneDO::getDescription, keyword))
                .orderByDesc(ProjectMilestoneDO::getUpdatedAt)
                .last("LIMIT " + size);
        LambdaQueryWrapper<ProjectCommentDO> commentQuery = new LambdaQueryWrapper<ProjectCommentDO>()
                .like(ProjectCommentDO::getContent, keyword)
                .orderByDesc(ProjectCommentDO::getCreatedAt)
                .last("LIMIT " + size);
        if (allCompany) {
            addVisibleProjectPredicate(taskQuery, "project_task.project_id");
            addVisibleProjectPredicate(milestoneQuery, "project_milestone.project_id");
            addVisibleProjectPredicate(commentQuery, "project_comment.project_id");
        } else {
            taskQuery.in(ProjectTaskDO::getProjectId, projectIds);
            milestoneQuery.in(ProjectMilestoneDO::getProjectId, projectIds);
            commentQuery.in(ProjectCommentDO::getProjectId, projectIds);
        }
        List<ProjectTaskDO> tasks = taskMapper.selectList(taskQuery);
        List<ProjectMilestoneDO> milestones = milestoneMapper.selectList(milestoneQuery);
        List<ProjectCommentDO> comments = commentMapper.selectList(commentQuery);

        Map<Long, ProjectDO> names = projectNames(projectIds, allCompany, projects, tasks, milestones, comments);
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
        result.setMilestones(milestones.stream().map(milestone -> {
            SearchHitDTO hit = new SearchHitDTO();
            hit.setId(milestone.getId());
            hit.setProjectId(milestone.getProjectId());
            hit.setProjectName(nameOf(names, milestone.getProjectId()));
            hit.setTitle(milestone.getTitle());
            hit.setSnippet(milestone.getDueDate() != null
                    ? milestone.getDueDate().toString()
                    : NotificationService.truncate(milestone.getDescription(), 80));
            hit.setMilestoneId(milestone.getId());
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

    private Map<Long, ProjectDO> projectNames(List<Long> readableIds, boolean allCompany,
                                              List<ProjectDO> matchedProjects,
                                              List<ProjectTaskDO> tasks,
                                              List<ProjectMilestoneDO> milestones,
                                              List<ProjectCommentDO> comments) {
        List<Long> extra = Stream.of(
                        tasks.stream().map(ProjectTaskDO::getProjectId),
                        milestones.stream().map(ProjectMilestoneDO::getProjectId),
                        comments.stream().map(ProjectCommentDO::getProjectId))
                .flatMap(stream -> stream)
                .filter(Objects::nonNull)
                .distinct()
                .filter(id -> matchedProjects.stream().noneMatch(project -> Objects.equals(project.getId(), id)))
                .collect(Collectors.toList());
        Map<Long, ProjectDO> names = matchedProjects.stream()
                .filter(project -> project.getId() != null)
                .collect(Collectors.toMap(ProjectDO::getId, Function.identity(), (left, right) -> left));
        if (!extra.isEmpty()) {
            LambdaQueryWrapper<ProjectDO> query = new LambdaQueryWrapper<ProjectDO>()
                    .in(ProjectDO::getId, extra)
                    .ne(ProjectDO::getStatus, ProjectStatus.DELETED.getCode());
            if (!allCompany) query.in(ProjectDO::getId, readableIds);
            projectMapper.selectList(query)
                    .forEach(project -> names.put(project.getId(), project));
        }
        return names;
    }

    private void addVisibleProjectPredicate(LambdaQueryWrapper<?> query, String projectIdColumn) {
        query.apply("EXISTS (SELECT 1 FROM project p WHERE p.id = " + projectIdColumn
                + " AND p.deleted = FALSE AND p.status <> {0})", ProjectStatus.DELETED.getCode());
    }

    private static String nameOf(Map<Long, ProjectDO> names, Long projectId) {
        ProjectDO project = names.get(projectId);
        if (project == null || !StringUtils.hasText(project.getName())) return "未命名项目";
        return project.getName();
    }
}
