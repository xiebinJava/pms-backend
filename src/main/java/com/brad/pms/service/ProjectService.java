package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.page.PageResult;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.request.ProjectCreateCmd;
import com.brad.pms.dto.request.ProjectPageQry;
import com.brad.pms.dto.request.ProjectUpdateCmd;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectMemberDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectMemberMapper;
import com.brad.pms.mapper.ProjectMilestoneMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final ProjectTaskMapper taskMapper;
    private final ProjectMilestoneMapper milestoneMapper;
    private final ProjectNodeMapper nodeMapper;
    private final MemberService memberService;
    private final NodeService nodeService;
    private final UserService userService;
    private final FollowerService followerService;

    @Transactional
    public ProjectDTO create(ProjectCreateCmd cmd) {
        Long ownerId = cmd.getOwnerId() == null ? UserContext.userId() : cmd.getOwnerId();
        ProjectDO project = new ProjectDO();
        project.setName(cmd.getName());
        project.setDescription(cmd.getDescription());
        project.setStatus(cmd.getStatus());
        project.setPriority(cmd.getPriority());
        project.setOwnerId(ownerId);
        project.setStartDate(cmd.getStartDate());
        project.setEndDate(cmd.getEndDate());
        project.setProgress(0);
        projectMapper.insert(project);

        project.setCode("PRJ-" + String.format("%06d", project.getId()));
        projectMapper.updateById(project);

        // 初始化项目管理节点（完成当前节点自动解锁下一个）
        nodeService.initDefault(project.getId());

        // 负责人自动成为项目成员
        memberService.add(project.getId(), ownerId, 0);
        return detail(project.getId());
    }

    public ProjectDTO update(Long id, ProjectUpdateCmd cmd) {
        ProjectDO project = requireProject(id);
        project.setName(cmd.getName());
        project.setDescription(cmd.getDescription());
        if (cmd.getStatus() != null) project.setStatus(cmd.getStatus());
        if (cmd.getPriority() != null) project.setPriority(cmd.getPriority());
        if (cmd.getOwnerId() != null) project.setOwnerId(cmd.getOwnerId());
        project.setStartDate(cmd.getStartDate());
        project.setEndDate(cmd.getEndDate());
        projectMapper.updateById(project);
        if (cmd.getMemberIds() != null) {
            memberService.replace(id, project.getOwnerId(), cmd.getMemberIds());
        }
        if (cmd.getFollowerIds() != null) {
            followerService.replace(id, cmd.getFollowerIds());
        }
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        requireProject(id);
        projectMapper.deleteById(id);
        memberMapper.delete(new LambdaQueryWrapper<ProjectMemberDO>().eq(ProjectMemberDO::getProjectId, id));
        taskMapper.delete(new LambdaQueryWrapper<com.brad.pms.entity.ProjectTaskDO>().eq(com.brad.pms.entity.ProjectTaskDO::getProjectId, id));
        milestoneMapper.delete(new LambdaQueryWrapper<com.brad.pms.entity.ProjectMilestoneDO>().eq(com.brad.pms.entity.ProjectMilestoneDO::getProjectId, id));
        nodeMapper.delete(new LambdaQueryWrapper<com.brad.pms.entity.ProjectNodeDO>().eq(com.brad.pms.entity.ProjectNodeDO::getProjectId, id));
        followerService.replace(id, Collections.emptyList());
    }

    public PageResult<ProjectDTO> page(ProjectPageQry qry) {
        LambdaQueryWrapper<ProjectDO> wrapper = new LambdaQueryWrapper<ProjectDO>()
                .like(StringUtils.hasText(qry.getKeyword()), ProjectDO::getName, qry.getKeyword())
                .eq(qry.getStatus() != null, ProjectDO::getStatus, qry.getStatus())
                .orderByDesc(ProjectDO::getUpdatedAt);

        IPage<ProjectDO> page = projectMapper.selectPage(new Page<>(qry.getCurrPage(), qry.getPageSize()), wrapper);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), enrich(page.getRecords()));
    }

    public ProjectDTO detail(Long id) {
        ProjectDO project = requireProject(id);
        return enrich(Collections.singletonList(project)).get(0);
    }

    public Map<String, Object> stats() {
        List<ProjectDO> all = projectMapper.selectList(null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", all.size());
        result.put("planning", countByStatus(all, 0));
        result.put("active", countByStatus(all, 1));
        result.put("completed", countByStatus(all, 2));
        result.put("archived", countByStatus(all, 3));
        double avgProgress = all.stream()
                .mapToInt(p -> p.getProgress() == null ? 0 : p.getProgress())
                .average().orElse(0);
        result.put("avgProgress", Math.round(avgProgress));
        return result;
    }

    private long countByStatus(List<ProjectDO> list, int status) {
        return list.stream().filter(p -> p.getStatus() != null && p.getStatus() == status).count();
    }

    private ProjectDO requireProject(Long id) {
        ProjectDO project = projectMapper.selectById(id);
        if (project == null) {
            throw BusinessException.error("项目不存在");
        }
        return project;
    }

    private List<ProjectDTO> enrich(List<ProjectDO> projects) {
        if (projects.isEmpty()) return Collections.emptyList();

        List<Long> projectIds = projects.stream().map(ProjectDO::getId).collect(Collectors.toList());
        Set<Long> userIds = projects.stream().map(ProjectDO::getOwnerId).collect(Collectors.toSet());
        userIds.add(UserContext.userId());

        // 成员数
        List<ProjectMemberDO> members = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMemberDO>().in(ProjectMemberDO::getProjectId, projectIds));
        Map<Long, Long> memberCountMap = members.stream()
                .collect(Collectors.groupingBy(ProjectMemberDO::getProjectId, Collectors.counting()));

        // 任务统计（按状态分组）
        List<Map<String, Object>> taskStats = taskMapper.countByProjectIds(projectIds);
        Map<Long, Map<Integer, Long>> taskCountMap = new HashMap<>();
        for (Map<String, Object> row : taskStats) {
            Long pid = ((Number) row.get("project_id")).longValue();
            Integer status = ((Number) row.get("status")).intValue();
            Long count = ((Number) row.get("cnt")).longValue();
            taskCountMap.computeIfAbsent(pid, k -> new HashMap<>()).put(status, count);
        }

        // 负责人信息
        Map<Long, UserDO> userMap = loadUsers(userIds);

        return projects.stream().map(p -> {
            Long pid = p.getId();
            Map<Integer, Long> stats = taskCountMap.getOrDefault(pid, Collections.emptyMap());
            int total = stats.values().stream().mapToInt(Long::intValue).sum();
            int done = stats.getOrDefault(2, 0L).intValue();
            int progress = total == 0 ? (p.getProgress() == null ? 0 : p.getProgress())
                    : (int) Math.round(done * 100.0 / total);
            return Convertors.toProject(p, userMap.get(p.getOwnerId()),
                    memberCountMap.getOrDefault(pid, 0L).intValue(), total, done);
        }).collect(Collectors.toList());
    }

    private Map<Long, UserDO> loadUsers(Set<Long> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        return userService.listByIds(new ArrayList<>(userIds)).stream()
                .collect(Collectors.toMap(UserDO::getId, u -> u));
    }
}
