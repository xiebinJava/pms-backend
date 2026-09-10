package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.enums.ProjectLevel;
import com.brad.pms.common.enums.ProjectStatus;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
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
import com.brad.pms.entity.OrgUnitDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectMemberMapper;
import com.brad.pms.mapper.ProjectLifecycleLogMapper;
import com.brad.pms.mapper.ProjectMilestoneMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.mapper.OrgUnitMapper;
import com.brad.pms.mapper.UserPositionMapper;
import com.brad.pms.security.UserContext;
import com.brad.pms.security.ProjectPermissionPolicy;
import com.brad.pms.security.DataScopeResolver;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.PermissionCode;
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
    private final OrgUnitMapper orgUnitMapper;
    private final OperationLogService operationLogService;

    @Transactional
    public ProjectDTO create(ProjectCreateCmd cmd) {
        Long creatorId = UserContext.userId();
        ProjectDO project = new ProjectDO();
        project.setName(cmd.getName());
        project.setDescription(cmd.getDescription());
        // 新建项目统一从“进行中”开始，项目经理在首节点确认后再落库。
        project.setStatus(ProjectStatus.ACTIVE.getCode());
        project.setPriority(cmd.getPriority());
        project.setProjectLevel(requireProjectLevel(cmd.getProjectLevel()));
        // owner_id 为历史兼容字段，真实项目创建人统一取当前登录用户。
        project.setOwnerId(creatorId);
        project.setCreatedBy(creatorId);
        Long orgUnitId = permissionService.requireProjectCreateOrgUnit(cmd.getOrgUnitId());
        if (orgUnitId == null) {
            var primary = userPositionMapper.findActivePrimary(creatorId);
            orgUnitId = primary == null ? null : primary.getOrgUnitId();
        }
        project.setOrgUnitId(orgUnitId);
        project.setStartDate(cmd.getStartDate());
        project.setEndDate(cmd.getEndDate());
        project.setProgress(0);
        // MySQL enforces project.code as NOT NULL. Insert a unique,
        // internal placeholder first, then replace it with the human-readable
        // PRJ-xxxxxx code after the auto-increment ID has been assigned.
        project.setCode("TMP-" + UUID.randomUUID());
        projectMapper.insert(project);

        project.setCode("PRJ-" + String.format("%06d", project.getId()));
        projectMapper.updateById(project);

        // 初始化项目管理节点（完成当前节点自动解锁下一个）
        nodeService.initDefault(project.getId());

        // 创建人自动成为项目成员，项目经理在首节点确认后再设置。
        memberService.add(project.getId(), creatorId, 0);
        operationLogService.record(AuditEvent.success(
                AuditAction.PROJECT_CREATED.name(), AuditResourceType.PROJECT.name(), project.getId(), project.getId(),
                null, null, projectAuditSnapshot(project)));
        return detail(project.getId());
    }

    @Transactional
    public ProjectDTO update(Long id, ProjectUpdateCmd cmd) {
        ProjectDO project = permissionService.requireProjectWritable(id, "编辑项目");
        requireCurrentVersion(project.getVersion(), cmd.getVersion(), "项目");
        Map<String, Object> before = projectAuditSnapshot(project);
        Long previousProjectManagerId = project.getProjectManagerId();
        project.setName(cmd.getName());
        project.setDescription(cmd.getDescription());
        if (cmd.getPriority() != null) project.setPriority(cmd.getPriority());
        if (cmd.getProjectLevel() != null) project.setProjectLevel(requireProjectLevel(cmd.getProjectLevel()));
        if (cmd.getOrgUnitId() != null) {
            if (!permissionService.canWriteProjectOrg(project, cmd.getOrgUnitId())) {
                throw BusinessException.forbidden("项目组织不在当前用户的写入范围内");
            }
            project.setOrgUnitId(cmd.getOrgUnitId());
        }
        project.setStartDate(cmd.getStartDate());
        project.setEndDate(cmd.getEndDate());
        boolean managesProjectComposition = cmd.getMemberIds() != null
                || cmd.getFollowerIds() != null || cmd.getProjectManagerId() != null;
        if (managesProjectComposition) {
            permissionService.requireProjectManageable(id, "维护项目成员或项目经理");
        }
        if (cmd.getProjectManagerId() != null) {
            if (cmd.getMemberIds() == null) {
                permissionService.requireProjectMember(id, cmd.getProjectManagerId());
            } else if (!cmd.getMemberIds().contains(cmd.getProjectManagerId())) {
                throw BusinessException.error("项目经理必须是项目成员");
            }
            project.setProjectManagerId(cmd.getProjectManagerId());
        }
        if (projectMapper.updateById(project) != 1) {
            throw BusinessException.conflict("项目已被其他人修改，请刷新后重试");
        }
        if (cmd.getMemberIds() != null) {
            memberService.replace(id, project.getCreatedBy() == null ? project.getOwnerId() : project.getCreatedBy(),
                    cmd.getMemberIds());
        }
        Map<String, Object> after = projectAuditSnapshot(project);
        if (!before.equals(after)) {
            operationLogService.record(AuditEvent.success(
                    AuditAction.PROJECT_UPDATED.name(), AuditResourceType.PROJECT.name(), id, id,
                    null, before, after));
        }
        if (!Objects.equals(previousProjectManagerId, project.getProjectManagerId())) {
            operationLogService.record(AuditEvent.success(
                    AuditAction.PROJECT_MANAGER_CHANGED.name(), AuditResourceType.PROJECT.name(), id, id,
                    null, Map.of("projectManagerId", previousProjectManagerId == null ? "UNASSIGNED" : previousProjectManagerId),
                    Map.of("projectManagerId", project.getProjectManagerId() == null ? "UNASSIGNED" : project.getProjectManagerId())));
        }
        if (cmd.getFollowerIds() != null) {
            followerService.replace(id, cmd.getFollowerIds());
        }
        return detail(id);
    }

    private void requireCurrentVersion(Integer currentVersion, Integer requestedVersion, String resourceName) {
        if (!Objects.equals(currentVersion, requestedVersion)) {
            throw BusinessException.conflict(resourceName + "已被其他人修改，请刷新后重试");
        }
    }

    @Transactional
    public void delete(Long id) {
        ProjectDO project = permissionService.requireProjectManageable(id, "删除项目");
        int fromStatus = ProjectStatus.normalize(project.getStatus());
        projectMapper.softDeleteProject(id, ProjectStatus.DELETED.getCode());
        recordLifecycle(id, "DELETE", "删除项目", fromStatus, ProjectStatus.DELETED.getCode());
        operationLogService.record(AuditEvent.success(
                AuditAction.PROJECT_DELETED.name(), AuditResourceType.PROJECT.name(), id, id,
                "删除项目", Map.of("status", fromStatus), Map.of("status", ProjectStatus.DELETED.getCode())));
    }

    @Transactional
    public ProjectDTO terminate(Long id, String reason) {
        ProjectDO project = permissionService.requireProjectManageable(id, "终止项目");
        if (!ProjectPermissionPolicy.canTerminateProject(project, UserContext.userId(), UserContext.isAdministrator(),
                permissionService.canManageProject(project))) {
            throw BusinessException.forbidden("仅进行中的项目可以终止，且当前用户没有项目治理权限");
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
        operationLogService.record(AuditEvent.success(
                AuditAction.PROJECT_TERMINATED.name(), AuditResourceType.PROJECT.name(), id, id,
                reason, Map.of("status", fromStatus), Map.of("status", ProjectStatus.TERMINATED.getCode())));
        return detail(id);
    }

    @Transactional
    public ProjectDTO restore(Long id, String reason) {
        ProjectDO project = permissionService.requireProjectForRestore(id);
        int fromStatus = ProjectStatus.normalize(project.getStatus());
        project.setStatus(ProjectStatus.ACTIVE.getCode());
        project.setDeleted(false);
        projectMapper.restoreProject(id, ProjectStatus.ACTIVE.getCode());

        ProjectNodeDO terminated = nodeMapper.selectOne(new LambdaQueryWrapper<ProjectNodeDO>()
                .eq(ProjectNodeDO::getProjectId, id)
                .eq(ProjectNodeDO::getStatus, 3)
                .orderByAsc(ProjectNodeDO::getSort)
                .last("LIMIT 1"));
        if (terminated != null) {
            terminated.setStatus(1);
            nodeMapper.updateById(terminated);
        }
        recordLifecycle(id, "RESTORE", reason, fromStatus, ProjectStatus.ACTIVE.getCode());
        operationLogService.record(AuditEvent.success(
                AuditAction.PROJECT_RESTORED.name(), AuditResourceType.PROJECT.name(), id, id,
                reason, Map.of("status", fromStatus), Map.of("status", ProjectStatus.ACTIVE.getCode())));
        return detail(id);
    }

    public PageResult<ProjectDTO> page(ProjectPageQry qry) {
        LambdaQueryWrapper<ProjectDO> wrapper = new LambdaQueryWrapper<ProjectDO>()
                .like(StringUtils.hasText(qry.getKeyword()), ProjectDO::getName, qry.getKeyword())
                .ne(ProjectDO::getStatus, ProjectStatus.DELETED.getCode())
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

    /**
     * Returns readable, non-deleted projects for the given IDs using the same
     * data-scope rules as the project list. Used by the workbench aggregate.
     */
    public List<ProjectDTO> listReadableByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        LambdaQueryWrapper<ProjectDO> wrapper = new LambdaQueryWrapper<ProjectDO>()
                .in(ProjectDO::getId, ids)
                .ne(ProjectDO::getStatus, ProjectStatus.DELETED.getCode())
                .orderByDesc(ProjectDO::getUpdatedAt);
        applyReadScope(wrapper);
        return enrich(projectMapper.selectList(wrapper));
    }

    /**
     * IDs of non-deleted projects the current user can read. Search and inbox
     * use this so they never leak titles from outside the data scope.
     */
    public List<Long> listReadableIds() {
        LambdaQueryWrapper<ProjectDO> wrapper = new LambdaQueryWrapper<ProjectDO>()
                .select(ProjectDO::getId)
                .ne(ProjectDO::getStatus, ProjectStatus.DELETED.getCode());
        applyReadScope(wrapper);
        return projectMapper.selectList(wrapper).stream()
                .map(ProjectDO::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    public boolean hasAllCompanyProjectRead() {
        LoginUser current = UserContext.get();
        return current != null && (UserContext.isAdministrator()
                || dataScopeResolver.hasAllCompanyScope(current, PermissionCode.PROJECT_READ));
    }

    public Map<String, Object> stats() {
        LambdaQueryWrapper<ProjectDO> wrapper = new LambdaQueryWrapper<ProjectDO>()
                .ne(ProjectDO::getStatus, ProjectStatus.DELETED.getCode());
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

        boolean allCompany = dataScopeResolver.hasAllCompanyScope(current, PermissionCode.PROJECT_READ);
        if (allCompany) return;
        List<Long> allowedOrgIds = dataScopeResolver.resolveOrgUnitIds(current, PermissionCode.PROJECT_READ);

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
        Set<Long> projectOrgIds = projects.stream().map(ProjectDO::getOrgUnitId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, OrgUnitDO> orgUnitMap = loadProjectOrgUnits(projectOrgIds);
        orgUnitMap.values().stream().map(OrgUnitDO::getLeaderUserId)
                .filter(Objects::nonNull).forEach(userIds::add);
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

        // The project list and detail page share node completion as the single
        // source of truth for overall progress. Keep the stored value only as
        // a legacy fallback for projects that have no nodes yet.
        List<ProjectNodeDO> nodes = nodeMapper.selectList(
                new LambdaQueryWrapper<ProjectNodeDO>().in(ProjectNodeDO::getProjectId, projectIds));
        Map<Long, List<ProjectNodeDO>> nodesByProject = nodes.stream()
                .collect(Collectors.groupingBy(ProjectNodeDO::getProjectId));

        // 负责人信息
        Map<Long, UserDO> userMap = loadUsers(userIds);

        return projects.stream().map(p -> {
            p.setStatus(ProjectStatus.normalize(p.getStatus()));
            Long pid = p.getId();
            Map<Integer, Long> stats = taskCountMap.getOrDefault(pid, Collections.emptyMap());
            int total = stats.values().stream().mapToInt(Long::intValue).sum();
            int done = stats.getOrDefault(2, 0L).intValue();
            int progress = calculateNodeProgress(nodesByProject.get(pid), p.getProgress());
            ProjectDTO dto = Convertors.toProject(p, userMap.get(p.getOwnerId()), userMap.get(p.getCreatedBy()),
                    userMap.get(p.getProjectManagerId()),
                    memberCountMap.getOrDefault(pid, 0L).intValue(), total, done, progress);
            OrgUnitDO orgUnit = orgUnitMap.get(p.getOrgUnitId());
            if (orgUnit != null) {
                dto.setOrgUnitName(orgUnit.getName());
                dto.setOrgUnitPath(buildOrgUnitPath(orgUnit, orgUnitMap));
                dto.setOrgUnitLeaderName(Convertors.userDisplayName(userMap.get(orgUnit.getLeaderUserId())));
            }
            dto.setPermissions(permissionService.projectPermissions(p));
            return dto;
        }).collect(Collectors.toList());
    }

    static int calculateNodeProgress(List<ProjectNodeDO> nodes, Integer storedProgress) {
        if (nodes == null || nodes.isEmpty()) return storedProgress == null ? 0 : storedProgress;
        long completed = nodes.stream().filter(node -> Integer.valueOf(2).equals(node.getStatus())).count();
        return (int) Math.round(completed * 100.0 / nodes.size());
    }

    private Map<String, Object> projectAuditSnapshot(ProjectDO project) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", project.getName());
        snapshot.put("priority", project.getPriority());
        snapshot.put("projectLevel", project.getProjectLevel());
        snapshot.put("orgUnitId", project.getOrgUnitId());
        snapshot.put("startDate", project.getStartDate());
        snapshot.put("endDate", project.getEndDate());
        snapshot.put("status", project.getStatus());
        return snapshot;
    }

    private int requireProjectLevel(Integer projectLevel) {
        int value = projectLevel == null ? ProjectLevel.ROUTINE.getCode() : projectLevel;
        if (!ProjectLevel.isValid(value)) throw BusinessException.error("项目等级不合法");
        return value;
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

    /**
     * Loads the selected organization and the ancestors encoded in its materialized path.
     * This keeps project list/detail responses consistent without loading the whole tree.
     */
    private Map<Long, OrgUnitDO> loadProjectOrgUnits(Set<Long> projectOrgIds) {
        if (projectOrgIds.isEmpty()) return Collections.emptyMap();
        List<OrgUnitDO> direct = orgUnitMapper.selectList(new LambdaQueryWrapper<OrgUnitDO>()
                .in(OrgUnitDO::getId, projectOrgIds));
        if (direct == null || direct.isEmpty()) return Collections.emptyMap();

        Set<Long> allIds = new LinkedHashSet<>(projectOrgIds);
        direct.stream().map(OrgUnitDO::getPath).filter(StringUtils::hasText)
                .forEach(path -> allIds.addAll(parseOrgPath(path)));
        if (allIds.size() > projectOrgIds.size()) {
            List<OrgUnitDO> ancestors = orgUnitMapper.selectList(new LambdaQueryWrapper<OrgUnitDO>()
                    .in(OrgUnitDO::getId, allIds));
            if (ancestors != null) direct = ancestors;
        }
        return direct.stream().filter(Objects::nonNull).filter(org -> org.getId() != null)
                .collect(Collectors.toMap(OrgUnitDO::getId, org -> org, (left, right) -> left,
                        LinkedHashMap::new));
    }

    private String buildOrgUnitPath(OrgUnitDO orgUnit, Map<Long, OrgUnitDO> orgUnitMap) {
        List<String> names = parseOrgPath(orgUnit.getPath()).stream()
                .map(orgUnitMap::get)
                .filter(Objects::nonNull)
                .map(OrgUnitDO::getName)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        if (names.isEmpty() && StringUtils.hasText(orgUnit.getName())) return orgUnit.getName();
        if (!names.contains(orgUnit.getName()) && StringUtils.hasText(orgUnit.getName())) names.add(orgUnit.getName());
        return String.join(" / ", names);
    }

    private Set<Long> parseOrgPath(String path) {
        if (!StringUtils.hasText(path)) return Collections.emptySet();
        Set<Long> ids = new LinkedHashSet<>();
        for (String value : path.split("/")) {
            if (!StringUtils.hasText(value)) continue;
            try {
                ids.add(Long.valueOf(value));
            } catch (NumberFormatException ignored) {
                // Ignore malformed path fragments and fall back to the selected org name.
            }
        }
        return ids;
    }
}
