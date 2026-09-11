package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.common.enums.ProjectStatus;
import com.brad.pms.common.enums.TaskStatus;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.dto.response.WorkbenchActivityDTO;
import com.brad.pms.dto.response.WorkbenchDTO;
import com.brad.pms.dto.response.WorkbenchSummaryDTO;
import com.brad.pms.dto.response.WorkbenchTaskDTO;
import com.brad.pms.entity.ProjectCommentDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectMemberDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectCommentMapper;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectMemberMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.text.Collator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkbenchService {

    static final int TASK_LIMIT = 8;
    static final int PROJECT_LIMIT = 6;
    static final int ACTIVITY_LIMIT = 5;
    static final int ACTIVITY_LOOKBACK = 20;
    static final int DUE_SOON_DAYS = 7;

    private final ProjectService projectService;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final ProjectTaskMapper taskMapper;
    private final ProjectCommentMapper commentMapper;
    private final UserService userService;

    public WorkbenchDTO load() {
        Long userId = UserContext.userId();
        Set<Long> candidateIds = new LinkedHashSet<>();
        memberMapper.selectList(new LambdaQueryWrapper<ProjectMemberDO>()
                        .eq(ProjectMemberDO::getUserId, userId)).stream()
                .map(ProjectMemberDO::getProjectId)
                .filter(Objects::nonNull)
                .forEach(candidateIds::add);

        List<ProjectTaskDO> assigned = taskMapper.selectList(new LambdaQueryWrapper<ProjectTaskDO>()
                .eq(ProjectTaskDO::getAssigneeId, userId));
        assigned.stream().map(ProjectTaskDO::getProjectId).filter(Objects::nonNull).forEach(candidateIds::add);

        projectMapper.selectList(new LambdaQueryWrapper<ProjectDO>()
                        .and(wrapper -> wrapper.eq(ProjectDO::getOwnerId, userId)
                                .or().eq(ProjectDO::getCreatedBy, userId)
                                .or().eq(ProjectDO::getProjectManagerId, userId))
                        .ne(ProjectDO::getStatus, ProjectStatus.DELETED.getCode())).stream()
                .map(ProjectDO::getId)
                .forEach(candidateIds::add);

        List<ProjectDTO> projects = projectService.listReadableByIds(candidateIds);
        Map<Long, ProjectDTO> projectsById = projects.stream()
                .filter(project -> project.getId() != null)
                .collect(Collectors.toMap(ProjectDTO::getId, Function.identity(), (left, right) -> left));
        Set<Long> readableIds = projectsById.keySet();

        List<ProjectTaskDO> myTasks = assigned.stream()
                .filter(task -> readableIds.contains(task.getProjectId()))
                .collect(Collectors.toList());

        WorkbenchDTO dto = new WorkbenchDTO();
        dto.setSummary(summarize(myTasks, projects.size(), LocalDate.now()));
        dto.setTasks(toTaskItems(myTasks, projectsById).stream().limit(TASK_LIMIT).collect(Collectors.toList()));
        dto.setProjects(projects.stream().limit(PROJECT_LIMIT).collect(Collectors.toList()));
        dto.setActivities(loadActivities(readableIds, projectsById));
        return dto;
    }

    static WorkbenchSummaryDTO summarize(List<ProjectTaskDO> myTasks, int participatingProjectCount, LocalDate today) {
        WorkbenchSummaryDTO summary = new WorkbenchSummaryDTO();
        summary.setParticipatingProjectCount(participatingProjectCount);
        LocalDate dueLimit = today.plusDays(DUE_SOON_DAYS);
        int pending = 0;
        int inProgress = 0;
        int dueSoon = 0;
        for (ProjectTaskDO task : myTasks) {
            int status = task.getStatus() == null ? TaskStatus.TODO.getCode() : task.getStatus();
            if (status == TaskStatus.TODO.getCode()) pending++;
            if (status == TaskStatus.DOING.getCode()) inProgress++;
            if (status != TaskStatus.DONE.getCode() && task.getDueDate() != null
                    && !task.getDueDate().isBefore(today) && !task.getDueDate().isAfter(dueLimit)) {
                dueSoon++;
            }
        }
        summary.setPendingTaskCount(pending);
        summary.setInProgressTaskCount(inProgress);
        summary.setDueSoonTaskCount(dueSoon);
        return summary;
    }

    static List<WorkbenchTaskDTO> toTaskItems(List<ProjectTaskDO> tasks, Map<Long, ProjectDTO> projectsById) {
        return tasks.stream()
                .sorted(taskOrder())
                .map(task -> {
                    WorkbenchTaskDTO item = new WorkbenchTaskDTO();
                    BeanUtils.copyProperties(Convertors.toTask(task, null), item);
                    ProjectDTO project = projectsById.get(task.getProjectId());
                    item.setProjectName(project == null || project.getName() == null ? "未命名项目" : project.getName());
                    item.setProjectCode(project == null || project.getCode() == null ? "" : project.getCode());
                    return item;
                })
                .collect(Collectors.toList());
    }

    private List<WorkbenchActivityDTO> loadActivities(Set<Long> projectIds, Map<Long, ProjectDTO> projectsById) {
        if (projectIds.isEmpty()) return List.of();
        List<ProjectCommentDO> comments = commentMapper.selectList(new LambdaQueryWrapper<ProjectCommentDO>()
                .in(ProjectCommentDO::getProjectId, projectIds)
                .orderByDesc(ProjectCommentDO::getCreatedAt)
                .last("LIMIT " + ACTIVITY_LOOKBACK));
        if (comments.isEmpty()) return List.of();
        Set<Long> userIds = comments.stream().map(ProjectCommentDO::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, UserDO> users = userService.listByIds(new ArrayList<>(userIds)).stream()
                .collect(Collectors.toMap(UserDO::getId, Function.identity(), (left, right) -> left));
        return comments.stream()
                .sorted(Comparator.comparing(ProjectCommentDO::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(ACTIVITY_LIMIT)
                .map(comment -> {
                    WorkbenchActivityDTO item = new WorkbenchActivityDTO();
                    item.setId(comment.getId());
                    item.setProjectId(comment.getProjectId());
                    ProjectDTO project = projectsById.get(comment.getProjectId());
                    item.setProjectName(project == null || project.getName() == null ? "未命名项目" : project.getName());
                    String actorName = Convertors.userDisplayName(users.get(comment.getUserId()));
                    item.setActorName(actorName != null ? actorName : "用户 " + comment.getUserId());
                    item.setContent(comment.getContent());
                    item.setCreatedAt(comment.getCreatedAt());
                    return item;
                })
                .collect(Collectors.toList());
    }

    private static Comparator<ProjectTaskDO> taskOrder() {
        Collator titleOrder = Collator.getInstance(Locale.CHINA);
        return Comparator
                .comparing((ProjectTaskDO task) -> task.getStatus() == null ? TaskStatus.TODO.getCode() : task.getStatus())
                .thenComparing(ProjectTaskDO::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing((ProjectTaskDO task) -> task.getPriority() == null ? 0 : task.getPriority(), Comparator.reverseOrder())
                .thenComparing(task -> task.getTitle() == null ? "" : task.getTitle(), titleOrder);
    }
}
