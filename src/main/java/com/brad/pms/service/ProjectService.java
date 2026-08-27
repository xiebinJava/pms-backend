package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.enums.ProjectStatus;
import com.brad.pms.common.page.PageResult;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.request.ProjectCreateCmd;
import com.brad.pms.dto.request.ProjectPageQry;
import com.brad.pms.dto.request.ProjectUpdateCmd;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectLifecycleLogDO;
import com.brad.pms.entity.ProjectMemberDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectMemberMapper;
import com.brad.pms.mapper.ProjectLifecycleLogMapper;
import com.brad.pms.mapper.ProjectMilestoneMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.mapper.UserPositionMapper;
import com.brad.pms.security.UserContext;
import com.brad.pms.security.ProjectPermissionPolicy;
import com.brad.pms.security.DataScopeResolver;
import com.brad.pms.security.LoginUser;
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
    private final ProjectPermissionService permissionService;
    private final ProjectLifecycleLogMapper lifecycleLogMapper;
    private final UserPositionMapper userPositionMapper;
    private final DataScopeResolver dataScopeResolver;

    @Transactional
    public ProjectDTO create(ProjectCreateCmd cmd) {
        Long creatorId = UserContext.userId();
        ProjectDO project = new ProjectDO();
        project.setName(cmd.getName());
        project.setDescription(cmd.getDescription());
        // 新建项目统一从“进行中”开始，项目经理在首节点确认后再落库。
        project.setStatus(ProjectStatus.ACTIVE.getCode());
        project.setPriority(cmd.getPriority());
        // owner_id 为历史兼容字段，真实项目创建人统一取当前登录用户。
        project.setOwnerId(creatorId);
        project.setCreatedBy(creatorId);
        Long orgUnitId = cmd.getOrgUnitId();
        if (orgUnitId == null) {
            var primary = userPositionMapper.findActivePrimary(creatorId);
            orgUnitId = primary == null ? null : primary.getOrgUnitId();
        }
        project.setOrgUnitId(orgUnitId);
        project.setStartDate(cmd.getStartDate());
        project.setEndDate(cmd.getEndDate());
        project.setProgress(0);
        projectMapper.insert(project);

        project.setCode("PRJ-" + String.format("%06d", project.getId()));
        projectMapper.updateById(project);

        // 初始化项目管理节点（完成当前节点自动解锁下一个）
        nodeService.initDefault(project.getId());

        // 创建人自动成为项目成员，项目经理在首节点确认后再设置。
        memberService.add(project.getId(), creatorId, 0);
        return detail(project.getId());
    }

    @Transactional
    public ProjectDTO update(Long id, ProjectUpdateCmd cmd) {
        ProjectDO project = permissionService.requireManageableProject(id, "编辑项目");
        project.setName(cmd.getName());
        project.setDescription(cmd.getDescription());
        if (cmd.getPriority() != null) project.setPriority(cmd.getPriority());
        if (cmd.getOrgUnitId() != null) project.setOrgUnitId(cmd.getOrgUnitId());
        project.setStartDate(cmd.getStartDate());
        project.setEndDate(cmd.getEndDate());
        if (cmd.getMemberIds() != null) {
            memberService.replace(id, project.getCreatedBy() == null ? project.getOwnerId() : project.getCreatedBy(),
                    cmd.getMemberIds());
        }
        if (cmd.getProjectManagerId() != null) {
            permissionService.requireProjectMember(id, cmd.getProjectManagerId());
            project.setProjectManagerId(cmd.getProjectManagerId());
        }
        projectMapper.updateById(project);
        if (cmd.getFollowerIds() != null) {
            followerService.replace(id, cmd.getFollowerIds());
        }
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        ProjectDO project = permissionService.requireManageableProject(id, "删除项目");
        int fromStatus = ProjectStatus.normalize(project.getStatus());
        project.setStatus(ProjectStatus.DELETED.getCode());
        projectMapper.updateById(project);
        recordLifecycle(id, "DELETE", "删除项目", fromStatus, ProjectStatus.DELETED.getCode());
    }

    @Transactional
    public ProjectDTO terminate(Long id, String reason) {
        ProjectDO project = permissionService.requireProject(id);
        if (!ProjectPermissionPolicy.canTerminateProject(project, UserContext.userId(), UserContext.isAdministrator())) {
            throw BusinessException.forbidden("仅进行中的项目可以终止，且仅项目创建人或项目经理可以操作");
        }
        int fromStatus = ProjectStatus.normalize(project.getStatus());
        project.setStatus(ProjectStatus.TERMINATED.getCode());
        projectMapper.updateById(project);

        ProjectNodeDO current = nodeMapper.selectOne(new LambdaQueryWrapper<ProjectNodeDO>()
                .eq(ProjectNodeDO::getProjectId, id)
                .eq(ProjectNodeDO::getStatus, 1)
                .orderByAsc(ProjectNodeDO::getSort)
                .last("LIMIT 1"));
        if (current != null) {
            current.setStatus(3);
            nodeMapper.updateById(current);
        }
        recordLifecycle(id, "TERMINATE", reason, fromStatus, ProjectStatus.TERMINATED.getCode());
        return detail(id);
    }

    @Transactional
    public ProjectDTO restore(Long id, String reason) {
        ProjectDO project = permissionService.requireProject(id);
        Long userId = UserContext.userId();
        if (!Objects.equals(project.getStatus(), ProjectStatus.TERMINATED.getCode())
                || !ProjectPermissionPolicy.hasProjectControl(project, userId, UserContext.isAdministrator())) {
            throw BusinessException.forbidden("仅项目创建人或项目经理可以恢复已终止项目");
        }
        project.setStatus(ProjectStatus.ACTIVE.getCode());
        projectMapper.updateById(project);

        ProjectNodeDO terminated = nodeMapper.selectOne(new LambdaQueryWrapper<ProjectNodeDO>()
                .eq(ProjectNodeDO::getProjectId, id)
                .eq(ProjectNodeDO::getStatus, 3)
                .orderByAsc(ProjectNodeDO::getSort)
                .last("LIMIT 1"));
        if (terminated != null) {
            terminated.setStatus(1);
            nodeMapper.updateById(terminated);
        }
        recordLifecycle(id, "RESTORE", reason, ProjectStatus.TERMINATED.getCode(), ProjectStatus.ACTIVE.getCode());
        return detail(id);
    }

    public PageResult<ProjectDTO> page(ProjectPageQry qry) {
        LambdaQueryWrapper<ProjectDO> wrapper = new LambdaQueryWrapper<ProjectDO>()
                .like(StringUtils.hasText(qry.getKeyword()), ProjectDO::getName, qry.getKeyword())
                .orderByDesc(ProjectDO::getUpdatedAt);
        applyReadScope(wrapper);
        if (qry.getStatus() != null) {
            int status = ProjectStatus.normalize(qry.getStatus());
            if (status == ProjectStatus.ACTIVE.getCode()) {
                // 旧数据可能仍保存为 0，列表筛选“进行中”时一并纳入并由 enrich 统一返回 1。
                wrapper.in(ProjectDO::getStatus, 0, ProjectStatus.ACTIVE.getCode());
            } else {
                wrapper.eq(ProjectDO::getStatus, status);
            }
        }

        IPage<ProjectDO> page = projectMapper.selectPage(new Page<>(qry.getCurrPage(), qry.getPageSize()), wrapper);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), enrich(page.getRecords()));
    }

    public ProjectDTO detail(Long id) {
        ProjectDO project = requireProject(id);
        return enrich(Collections.singletonList(project)).get(0);
    }

    public Map<String, Object> stats() {
        LambdaQueryWrapper<ProjectDO> wrapper = new LambdaQueryWrapper<>();
        applyReadScope(wrapper);
        List<ProjectDO> all = projectMapper.selectList(wrapper);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", all.size());
        result.put("active", countByStatus(all, ProjectStatus.ACTIVE.getCode()));
        result.put("completed", countByStatus(all, ProjectStatus.COMPLETED.getCode()));
        result.put("terminated", countByStatus(all, ProjectStatus.TERMINATED.getCode()));
        result.put("deleted", countByStatus(all, ProjectStatus.DELETED.getCode()));
        double avgProgress = all.stream()
                .mapToInt(p -> p.getProgress() == null ? 0 : p.getProgress())
                .average().orElse(0);
        result.put("avgProgress", Math.round(avgProgress));
        return result;
    }

    /**
     * Keeps aggregate endpoints subject to exactly the same organization/member
     * visibility rules as the project list. This prevents a user with only a
     * narrow project:read scope from inferring company-wide project counts.
     */
    private void applyReadScope(LambdaQueryWrapper<ProjectDO> wrapper) {
        LoginUser current = UserContext.get();
        if (current == null || UserContext.isAdministrator()) return;

        List<Long> allowedOrgIds = dataScopeResolver.resolveOrgUnitIds(current, "project:read");
        boolean allCompany = dataScopeResolver.hasAllCompanyScope(current, "project:read");
        if (allCompany) return;

        List<Long> memberProjectIds = memberMapper.selectList(new LambdaQueryWrapper<ProjectMemberDO>()
                        .eq(ProjectMemberDO::getUserId, current.getId())).stream()
                .map(ProjectMemberDO::getProjectId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (allowedOrgIds.isEmpty() && memberProjectIds.isEmpty()) wrapper.eq(ProjectDO::getId, -1L);
        else if (!allowedOrgIds.isEmpty() && !memberProjectIds.isEmpty()) {
            wrapper.and(w -> w.in(ProjectDO::getOrgUnitId, allowedOrgIds).or().in(ProjectDO::getId, memberProjectIds));
        } else if (!allowedOrgIds.isEmpty()) wrapper.in(ProjectDO::getOrgUnitId, allowedOrgIds);
        else wrapper.in(ProjectDO::getId, memberProjectIds);
    }

    private long countByStatus(List<ProjectDO> list, int status) {
        return list.stream().filter(p -> ProjectStatus.normalize(p.getStatus()) == status).count();
    }

    private ProjectDO requireProject(Long id) {
        return permissionService.requireProject(id);
    }

    private List<ProjectDTO> enrich(List<ProjectDO> projects) {
        if (projects.isEmpty()) return Collections.emptyList();

        List<Long> projectIds = projects.stream().map(ProjectDO::getId).collect(Collectors.toList());
        Set<Long> userIds = projects.stream().map(ProjectDO::getOwnerId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        projects.stream().map(ProjectDO::getCreatedBy).filter(Objects::nonNull).forEach(userIds::add);
        projects.stream().map(ProjectDO::getProjectManagerId).filter(Objects::nonNull).forEach(userIds::add);
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
            p.setStatus(ProjectStatus.normalize(p.getStatus()));
            Long pid = p.getId();
            Map<Integer, Long> stats = taskCountMap.getOrDefault(pid, Collections.emptyMap());
            int total = stats.values().stream().mapToInt(Long::intValue).sum();
            int done = stats.getOrDefault(2, 0L).intValue();
            int progress = total == 0 ? (p.getProgress() == null ? 0 : p.getProgress())
                    : (int) Math.round(done * 100.0 / total);
            ProjectDTO dto = Convertors.toProject(p, userMap.get(p.getOwnerId()), userMap.get(p.getCreatedBy()),
                    userMap.get(p.getProjectManagerId()),
                    memberCountMap.getOrDefault(pid, 0L).intValue(), total, done);
            dto.setPermissions(permissionService.projectPermissions(p));
            return dto;
        }).collect(Collectors.toList());
    }

    private void recordLifecycle(Long projectId, String action, String reason, int fromStatus, int toStatus) {
        ProjectLifecycleLogDO log = new ProjectLifecycleLogDO();
        log.setProjectId(projectId);
        log.setAction(action);
        log.setReason(reason);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setOperatorId(UserContext.userId());
        lifecycleLogMapper.insert(log);
    }

    private Map<Long, UserDO> loadUsers(Set<Long> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        return userService.listByIds(new ArrayList<>(userIds)).stream()
                .collect(Collectors.toMap(UserDO::getId, u -> u));
    }
}
