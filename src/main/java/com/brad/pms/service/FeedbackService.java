package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.brad.pms.common.enums.FeedbackPriority;
import com.brad.pms.common.enums.FeedbackStatus;
import com.brad.pms.common.enums.FeedbackType;
import com.brad.pms.common.enums.ProjectStatus;
import com.brad.pms.common.enums.UserStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.page.PageResult;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.request.FeedbackCreateCmd;
import com.brad.pms.dto.request.FeedbackPageQry;
import com.brad.pms.dto.request.FeedbackReopenCmd;
import com.brad.pms.dto.request.FeedbackUpdateCmd;
import com.brad.pms.dto.response.FeedbackHistoryDTO;
import com.brad.pms.dto.response.FeedbackAssigneeDTO;
import com.brad.pms.dto.response.FeedbackTicketDTO;
import com.brad.pms.entity.FeedbackHistoryDO;
import com.brad.pms.entity.FeedbackTicketDO;
import com.brad.pms.entity.ProjectMemberDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.entity.UserPositionDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.FeedbackHistoryMapper;
import com.brad.pms.mapper.FeedbackTicketMapper;
import com.brad.pms.mapper.ProjectMemberMapper;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.mapper.UserPositionMapper;
import com.brad.pms.mapper.UserMapper;
import com.brad.pms.security.AuthorizationService;
import com.brad.pms.security.DataScopeResolver;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final DateTimeFormatter TICKET_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final FeedbackTicketMapper ticketMapper;
    private final FeedbackHistoryMapper historyMapper;
    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectTaskMapper taskMapper;
    private final ProjectNodeMapper nodeMapper;
    private final UserPositionMapper userPositionMapper;
    private final DataScopeResolver dataScopeResolver;
    private final ProjectPermissionService projectPermissionService;
    private final AuthorizationService authorizationService;
    private final OperationLogService operationLogService;

    @Transactional
    public FeedbackTicketDTO create(FeedbackCreateCmd cmd) {
        Long reporterId = UserContext.userId();
        FeedbackType type = requireType(cmd.getFeedbackType());
        FeedbackPriority priority = parsePriority(cmd.getPriority(), FeedbackPriority.NORMAL);
        String clientRequestId = trimToNull(cmd.getClientRequestId());
        if (clientRequestId != null) {
            FeedbackTicketDO existing = ticketMapper.findByReporterAndClientRequestId(reporterId, clientRequestId);
            if (existing != null) return detail(existing.getId());
        }

        validateContext(cmd.getProjectId(), cmd.getTaskId(), cmd.getNodeId());
        FeedbackTicketDO ticket = new FeedbackTicketDO();
        ticket.setTicketNo(newTicketNo());
        ticket.setTitle(cmd.getTitle().trim());
        ticket.setContent(cmd.getContent().trim());
        ticket.setFeedbackType(type.name());
        ticket.setPriority(priority.name());
        ticket.setStatus(FeedbackStatus.PENDING_TRIAGE.name());
        ticket.setProjectId(cmd.getProjectId());
        ticket.setTaskId(cmd.getTaskId());
        ticket.setNodeId(cmd.getNodeId());
        ticket.setContextModule(trimToNull(cmd.getContextModule()));
        ticket.setSourceUrl(trimToNull(cmd.getSourceUrl()));
        ticket.setReporterId(reporterId);
        ticket.setClientRequestId(clientRequestId);
        ticket.setVersion(0);
        ticket.setDeleted(false);
        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setUpdatedAt(ticket.getCreatedAt());
        try {
            ticketMapper.insert(ticket);
        } catch (DuplicateKeyException duplicateKey) {
            // A concurrent retry can race the pre-insert lookup. The unique
            // reporter/request index is the final idempotency guard.
            if (clientRequestId != null) {
                FeedbackTicketDO existing = ticketMapper.findByReporterAndClientRequestId(reporterId, clientRequestId);
                if (existing != null) return detail(existing.getId());
            }
            throw duplicateKey;
        }

        appendHistory(ticket, "CREATED", null, ticket.getStatus(), null, ticket.getPriority(), null,
                null, null);
        operationLogService.record("FEEDBACK_CREATED", "FEEDBACK_TICKET", ticket.getId(), null, auditSnapshot(ticket));
        return detail(ticket.getId());
    }

    public PageResult<FeedbackTicketDTO> page(FeedbackPageQry qry) {
        Long currentUserId = UserContext.userId();
        boolean hasKeyword = StringUtils.hasText(qry.getKeyword());
        LambdaQueryWrapper<FeedbackTicketDO> wrapper = new LambdaQueryWrapper<FeedbackTicketDO>()
                .and(hasKeyword, query -> query.like(FeedbackTicketDO::getTicketNo, qry.getKeyword())
                        .or().like(FeedbackTicketDO::getTitle, qry.getKeyword())
                        .or().like(FeedbackTicketDO::getContent, qry.getKeyword()))
                .eq(StringUtils.hasText(qry.getFeedbackType()), FeedbackTicketDO::getFeedbackType,
                        normalize(qry.getFeedbackType()))
                .eq(StringUtils.hasText(qry.getPriority()), FeedbackTicketDO::getPriority,
                        normalize(qry.getPriority()))
                .eq(StringUtils.hasText(qry.getStatus()), FeedbackTicketDO::getStatus,
                        normalize(qry.getStatus()))
                .orderByDesc(FeedbackTicketDO::getCreatedAt);
        applyVisibilityScope(wrapper, currentUserId);

        IPage<FeedbackTicketDO> page = ticketMapper.selectPage(
                new Page<>(qry.getCurrPage(), qry.getPageSize()), wrapper);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), enrich(page.getRecords(), false));
    }

    public FeedbackTicketDTO detail(Long id) {
        FeedbackTicketDO ticket = requireVisible(id);
        return enrich(List.of(ticket), true).get(0);
    }

    /**
     * Returns only active users that the current feedback manager may assign.
     * Keeping this endpoint in the feedback module avoids requiring a manager
     * to also hold the unrelated administration-user read permission.
     */
    public List<FeedbackAssigneeDTO> assignees() {
        authorizationService.require(PermissionCode.FEEDBACK_MANAGE);
        Long currentUserId = UserContext.userId();
        FeedbackVisibility visibility = resolveManagerVisibility(currentUserId);
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getStatus, UserStatus.ACTIVE.name())
                .orderByAsc(UserDO::getNameZh, UserDO::getUsername, UserDO::getEmail);
        if (!visibility.allCompany()) {
            Set<Long> visibleUserIds = new LinkedHashSet<>(visibility.reporterIds());
            if (!visibility.projectIds().isEmpty()) {
                safeList(projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMemberDO>()
                                .in(ProjectMemberDO::getProjectId, visibility.projectIds())))
                        .stream().map(ProjectMemberDO::getUserId).filter(Objects::nonNull).forEach(visibleUserIds::add);
            }
            if (visibleUserIds.isEmpty()) return List.of();
            wrapper.in(UserDO::getId, visibleUserIds);
        }
        return safeList(userMapper.selectList(wrapper)).stream().map(user -> {
            FeedbackAssigneeDTO dto = new FeedbackAssigneeDTO();
            dto.setId(user.getId());
            dto.setDisplayName(Convertors.userDisplayName(user));
            dto.setEmail(user.getEmail());
            return dto;
        }).toList();
    }

    @Transactional
    public FeedbackTicketDTO update(Long id, FeedbackUpdateCmd cmd) {
        authorizationService.require(PermissionCode.FEEDBACK_MANAGE);
        FeedbackTicketDO ticket = ticketMapper.selectForUpdate(id);
        if (ticket == null) throw BusinessException.error("反馈不存在");
        requireManagerVisible(ticket);
        checkVersion(ticket, cmd.getVersion());

        String fromStatus = ticket.getStatus();
        String fromPriority = ticket.getPriority();
        Long fromAssignee = ticket.getAssigneeId();
        String nextStatus = StringUtils.hasText(cmd.getStatus()) ? requireStatus(cmd.getStatus()).name() : fromStatus;
        String nextPriority = StringUtils.hasText(cmd.getPriority())
                ? requirePriority(cmd.getPriority()).name() : fromPriority;
        // A missing assignee field means "keep the current assignee". V1 does
        // not expose unassignment as an implicit side effect of editing status.
        Long nextAssignee = cmd.getAssigneeId() == null ? fromAssignee : cmd.getAssigneeId();
        if (!Objects.equals(fromStatus, nextStatus)) {
            if (!FeedbackStatus.canTransition(fromStatus, nextStatus)) {
                throw BusinessException.error("反馈状态不允许从 " + fromStatus + " 变更为 " + nextStatus);
            }
        }
        if ((FeedbackStatus.ASSIGNED.name().equals(nextStatus) || FeedbackStatus.IN_PROGRESS.name().equals(nextStatus))
                && nextAssignee == null) {
            throw BusinessException.error("已分派或处理中反馈必须指定负责人");
        }
        if (nextAssignee != null) requireActiveUser(nextAssignee);
        if (requiresResolutionNote(nextStatus) && !StringUtils.hasText(cmd.getResolutionNote())
                && !StringUtils.hasText(ticket.getResolutionNote())) {
            throw BusinessException.error("该处理结果必须填写处理说明");
        }

        Map<String, Object> beforeAudit = auditSnapshot(ticket);
        ticket.setStatus(nextStatus);
        ticket.setPriority(nextPriority);
        ticket.setAssigneeId(nextAssignee);
        if (cmd.getResolutionNote() != null) ticket.setResolutionNote(trimToNull(cmd.getResolutionNote()));
        boolean wasClosed = FeedbackStatus.CLOSED.name().equals(fromStatus);
        boolean isClosed = FeedbackStatus.CLOSED.name().equals(nextStatus);
        if (isClosed && !wasClosed) ticket.setClosedAt(LocalDateTime.now());
        else if (!isClosed) ticket.setClosedAt(null);
        int updated = ticketMapper.updateById(ticket);
        if (updated == 0) throw BusinessException.conflict("反馈已被其他人更新，请刷新后重试");
        appendHistory(ticket, "UPDATED", fromStatus, nextStatus, fromPriority, nextPriority,
                fromAssignee, nextAssignee, ticket.getResolutionNote());
        operationLogService.record("FEEDBACK_UPDATED", "FEEDBACK_TICKET", ticket.getId(), beforeAudit, auditSnapshot(ticket));
        return detail(id);
    }

    @Transactional
    public FeedbackTicketDTO reopen(Long id, FeedbackReopenCmd cmd) {
        FeedbackTicketDO ticket = ticketMapper.selectForUpdate(id);
        if (ticket == null) throw BusinessException.error("反馈不存在");
        if (!isVisibleToCurrentUser(ticket)) throw BusinessException.forbidden("无权重开此反馈");
        checkVersion(ticket, cmd.getVersion());
        if (!FeedbackStatus.canTransition(ticket.getStatus(), FeedbackStatus.IN_PROGRESS.name())) {
            throw BusinessException.error("当前状态不能重开");
        }
        if (ticket.getAssigneeId() == null) {
            throw BusinessException.error("重新打开反馈前必须指定负责人");
        }
        requireActiveUser(ticket.getAssigneeId());
        String fromStatus = ticket.getStatus();
        Map<String, Object> beforeAudit = auditSnapshot(ticket);
        ticket.setStatus(FeedbackStatus.IN_PROGRESS.name());
        ticket.setClosedAt(null);
        ticket.setResolutionNote(trimToNull(cmd.getNote()));
        int updated = ticketMapper.updateById(ticket);
        if (updated == 0) throw BusinessException.conflict("反馈已被其他人更新，请刷新后重试");
        appendHistory(ticket, "REOPENED", fromStatus, ticket.getStatus(), ticket.getPriority(), ticket.getPriority(),
                ticket.getAssigneeId(), ticket.getAssigneeId(), ticket.getResolutionNote());
        operationLogService.record("FEEDBACK_REOPENED", "FEEDBACK_TICKET", ticket.getId(),
                beforeAudit, auditSnapshot(ticket));
        return detail(id);
    }

    private FeedbackTicketDO requireVisible(Long id) {
        FeedbackTicketDO ticket = ticketMapper.selectById(id);
        if (ticket == null) throw BusinessException.error("反馈不存在");
        if (!isVisibleToCurrentUser(ticket)) {
            throw BusinessException.forbidden("无权查看此反馈");
        }
        return ticket;
    }

    private boolean canManage() {
        return authorizationService.has(PermissionCode.FEEDBACK_MANAGE);
    }

    /**
     * Applies the same reporter/project/member data boundary to list queries
     * that detail and write operations use. The allowed project and reporter
     * IDs are resolved before building the feedback query, so pagination never
     * leaks an out-of-scope total or shifts records between pages.
     */
    private void applyVisibilityScope(LambdaQueryWrapper<FeedbackTicketDO> wrapper, Long currentUserId) {
        if (currentUserId == null) {
            wrapper.eq(FeedbackTicketDO::getId, -1L);
            return;
        }
        if (!canManage()) {
            wrapper.eq(FeedbackTicketDO::getReporterId, currentUserId);
            return;
        }

        FeedbackVisibility visibility = resolveManagerVisibility(currentUserId);
        if (visibility.allCompany()) return;
        wrapper.and(scope -> {
            boolean hasProjectScope = !visibility.projectIds().isEmpty();
            boolean hasReporterScope = !visibility.reporterIds().isEmpty();
            if (hasProjectScope) scope.in(FeedbackTicketDO::getProjectId, visibility.projectIds());
            if (hasReporterScope) {
                if (hasProjectScope) scope.or();
                scope.isNull(FeedbackTicketDO::getProjectId)
                        .in(FeedbackTicketDO::getReporterId, visibility.reporterIds());
            }
            if (!hasProjectScope && !hasReporterScope) scope.eq(FeedbackTicketDO::getId, -1L);
        });
    }

    private boolean isVisibleToCurrentUser(FeedbackTicketDO ticket) {
        Long currentUserId = UserContext.userIdOrNull();
        if (currentUserId == null || ticket == null) return false;
        // A reporter always retains access to their own submission, even if a
        // manager's organization assignment changes after the submission.
        if (Objects.equals(ticket.getReporterId(), currentUserId)) return true;
        if (!canManage()) return false;
        FeedbackVisibility visibility = resolveManagerVisibility(currentUserId);
        if (visibility.allCompany()) return true;
        if (ticket.getProjectId() != null) return visibility.projectIds().contains(ticket.getProjectId());
        return visibility.reporterIds().contains(ticket.getReporterId());
    }

    private void requireManagerVisible(FeedbackTicketDO ticket) {
        if (!isVisibleToCurrentUser(ticket)) throw BusinessException.forbidden("无权处理此反馈");
    }

    private FeedbackVisibility resolveManagerVisibility(Long currentUserId) {
        LoginUser current = UserContext.get();
        if (current == null || UserContext.isAdministrator()
                || dataScopeResolver.hasAllCompanyScope(current, PermissionCode.FEEDBACK_MANAGE)) {
            return FeedbackVisibility.ALL;
        }

        List<Long> allowedOrgIds = safeList(dataScopeResolver.resolveOrgUnitIds(current, PermissionCode.FEEDBACK_MANAGE));
        Set<Long> projectIds = new LinkedHashSet<>();
        if (!allowedOrgIds.isEmpty()) {
            List<ProjectDO> scopedProjects = safeList(projectMapper.selectList(new LambdaQueryWrapper<ProjectDO>()
                    .select(ProjectDO::getId)
                    .in(ProjectDO::getOrgUnitId, allowedOrgIds)
                    .ne(ProjectDO::getStatus, ProjectStatus.DELETED.getCode())));
            scopedProjects.stream().map(ProjectDO::getId).filter(Objects::nonNull).forEach(projectIds::add);
        }
        List<ProjectMemberDO> memberships = safeList(projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMemberDO>()
                .eq(ProjectMemberDO::getUserId, currentUserId)));
        memberships.stream().map(ProjectMemberDO::getProjectId).filter(Objects::nonNull).forEach(projectIds::add);

        Set<Long> reporterIds = new LinkedHashSet<>();
        // Managers can always find their own projectless submissions.
        reporterIds.add(currentUserId);
        if (!allowedOrgIds.isEmpty()) {
            List<UserPositionDO> scopedPositions = safeList(userPositionMapper.selectList(new LambdaQueryWrapper<UserPositionDO>()
                    .in(UserPositionDO::getOrgUnitId, allowedOrgIds)
                    .eq(UserPositionDO::getStatus, UserStatus.ACTIVE.name())));
            scopedPositions.stream().map(UserPositionDO::getUserId).filter(Objects::nonNull).forEach(reporterIds::add);
        }
        return new FeedbackVisibility(false, projectIds, reporterIds);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private Map<String, Object> auditSnapshot(FeedbackTicketDO ticket) {
        Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("ticketNo", ticket.getTicketNo());
        snapshot.put("feedbackType", ticket.getFeedbackType());
        snapshot.put("status", ticket.getStatus());
        snapshot.put("priority", ticket.getPriority());
        snapshot.put("projectId", ticket.getProjectId());
        snapshot.put("taskId", ticket.getTaskId());
        snapshot.put("nodeId", ticket.getNodeId());
        snapshot.put("reporterId", ticket.getReporterId());
        snapshot.put("assigneeId", ticket.getAssigneeId());
        snapshot.put("resolutionNotePresent", StringUtils.hasText(ticket.getResolutionNote()));
        snapshot.put("closedAt", ticket.getClosedAt());
        snapshot.put("version", ticket.getVersion());
        return snapshot;
    }

    private record FeedbackVisibility(boolean allCompany, Set<Long> projectIds, Set<Long> reporterIds) {
        private static final FeedbackVisibility ALL = new FeedbackVisibility(true, Set.of(), Set.of());
    }

    private void checkVersion(FeedbackTicketDO ticket, Integer expected) {
        if (!Objects.equals(ticket.getVersion(), expected)) {
            throw BusinessException.conflict("反馈已被其他人更新，请刷新后重试");
        }
    }

    private void validateContext(Long projectId, Long taskId, Long nodeId) {
        if (projectId == null && (taskId != null || nodeId != null)) {
            throw BusinessException.error("任务或节点关联必须同时选择项目");
        }
        if (projectId == null) return;
        projectPermissionService.requireProject(projectId);
        ProjectTaskDO task = null;
        if (taskId != null) {
            task = taskMapper.selectById(taskId);
            if (task == null || !Objects.equals(task.getProjectId(), projectId)) {
                throw BusinessException.error("任务不属于关联项目");
            }
        }
        if (nodeId != null) {
            ProjectNodeDO node = projectPermissionService.requireNode(projectId, nodeId);
            if (taskId != null) {
                if (task == null || !Objects.equals(task.getNodeId(), node.getId())) {
                    throw BusinessException.error("节点与任务的项目关系不一致");
                }
            }
        }
    }

    private void requireActiveUser(Long userId) {
        UserDO assignee = userMapper.selectById(userId);
        if (assignee == null || !UserStatus.ACTIVE.name().equalsIgnoreCase(assignee.getStatus())) {
            throw BusinessException.error("反馈负责人不存在或已停用");
        }
    }

    private List<FeedbackTicketDTO> enrich(Collection<FeedbackTicketDO> tickets, boolean withHistory) {
        if (tickets == null || tickets.isEmpty()) return List.of();
        Set<Long> userIds = new HashSet<>();
        Set<Long> projectIds = new HashSet<>();
        for (FeedbackTicketDO ticket : tickets) {
            if (ticket.getReporterId() != null) userIds.add(ticket.getReporterId());
            if (ticket.getAssigneeId() != null) userIds.add(ticket.getAssigneeId());
            if (ticket.getProjectId() != null) projectIds.add(ticket.getProjectId());
        }
        Map<Long, List<FeedbackHistoryDO>> historyByTicket = new HashMap<>();
        if (withHistory) {
            List<Long> ids = tickets.stream().map(FeedbackTicketDO::getId).toList();
            if (!ids.isEmpty()) {
                historyMapper.selectList(new LambdaQueryWrapper<FeedbackHistoryDO>()
                                .in(FeedbackHistoryDO::getTicketId, ids)
                                .orderByAsc(FeedbackHistoryDO::getCreatedAt)
                                .orderByAsc(FeedbackHistoryDO::getId))
                        .forEach(history -> historyByTicket.computeIfAbsent(history.getTicketId(), ignored -> new ArrayList<>()).add(history));
            }
        }
        historyByTicket.values().stream().flatMap(Collection::stream).map(FeedbackHistoryDO::getOperatorId)
                .filter(Objects::nonNull).forEach(userIds::add);
        Map<Long, UserDO> users = userIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserDO::getId, Function.identity()));
        Map<Long, ProjectDO> projects = projectIds.isEmpty() ? Map.of() : projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(ProjectDO::getId, Function.identity()));
        return tickets.stream().map(ticket -> toDto(ticket, historyByTicket.getOrDefault(ticket.getId(), List.of()), users, projects))
                .toList();
    }

    private FeedbackTicketDTO toDto(FeedbackTicketDO ticket, List<FeedbackHistoryDO> histories,
                                    Map<Long, UserDO> users, Map<Long, ProjectDO> projects) {
        FeedbackTicketDTO dto = new FeedbackTicketDTO();
        dto.setId(ticket.getId());
        dto.setTicketNo(ticket.getTicketNo());
        dto.setTitle(ticket.getTitle());
        dto.setContent(ticket.getContent());
        dto.setFeedbackType(ticket.getFeedbackType());
        dto.setPriority(ticket.getPriority());
        dto.setStatus(ticket.getStatus());
        dto.setProjectId(ticket.getProjectId());
        ProjectDO project = lookup(projects, ticket.getProjectId());
        dto.setProjectName(project == null ? null : project.getName());
        dto.setTaskId(ticket.getTaskId());
        dto.setNodeId(ticket.getNodeId());
        dto.setContextModule(ticket.getContextModule());
        dto.setSourceUrl(ticket.getSourceUrl());
        dto.setReporterId(ticket.getReporterId());
        dto.setReporterName(Convertors.userDisplayName(lookup(users, ticket.getReporterId())));
        dto.setAssigneeId(ticket.getAssigneeId());
        dto.setAssigneeName(Convertors.userDisplayName(lookup(users, ticket.getAssigneeId())));
        dto.setResolutionNote(ticket.getResolutionNote());
        dto.setClientRequestId(ticket.getClientRequestId());
        dto.setVersion(ticket.getVersion());
        dto.setCreatedAt(ticket.getCreatedAt());
        dto.setUpdatedAt(ticket.getUpdatedAt());
        dto.setClosedAt(ticket.getClosedAt());
        List<FeedbackHistoryDTO> history = histories.stream().map(item -> {
            FeedbackHistoryDTO historyDto = new FeedbackHistoryDTO();
            historyDto.setId(item.getId());
            historyDto.setTicketId(item.getTicketId());
            historyDto.setAction(item.getAction());
            historyDto.setFromStatus(item.getFromStatus());
            historyDto.setToStatus(item.getToStatus());
            historyDto.setFromPriority(item.getFromPriority());
            historyDto.setToPriority(item.getToPriority());
            historyDto.setFromAssigneeId(item.getFromAssigneeId());
            historyDto.setToAssigneeId(item.getToAssigneeId());
            historyDto.setNote(item.getNote());
            historyDto.setOperatorId(item.getOperatorId());
            historyDto.setOperatorName(Convertors.userDisplayName(lookup(users, item.getOperatorId())));
            historyDto.setRequestId(item.getRequestId());
            historyDto.setCreatedAt(item.getCreatedAt());
            return historyDto;
        }).toList();
        dto.setHistory(history);
        return dto;
    }

    private <T> T lookup(Map<Long, T> values, Long id) {
        return id == null ? null : values.get(id);
    }

    private void appendHistory(FeedbackTicketDO ticket, String action, String fromStatus, String toStatus,
                               String fromPriority, String toPriority, Long fromAssignee, Long toAssignee,
                               String note) {
        FeedbackHistoryDO history = new FeedbackHistoryDO();
        history.setTicketId(ticket.getId());
        history.setAction(action);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setFromPriority(fromPriority);
        history.setToPriority(toPriority);
        history.setFromAssigneeId(fromAssignee);
        history.setToAssigneeId(toAssignee);
        history.setNote(note);
        history.setOperatorId(UserContext.userIdOrNull());
        history.setRequestId(MDC.get("requestId"));
        history.setCreatedAt(LocalDateTime.now());
        historyMapper.insert(history);
    }

    private FeedbackType requireType(String value) {
        try {
            return FeedbackType.valueOf(normalize(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw BusinessException.error("反馈类型不合法");
        }
    }

    private FeedbackStatus requireStatus(String value) {
        FeedbackStatus status = FeedbackStatus.parse(value);
        if (status == null) throw BusinessException.error("反馈状态不合法");
        return status;
    }

    private FeedbackPriority requirePriority(String value) {
        try {
            return FeedbackPriority.valueOf(normalize(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw BusinessException.error("反馈优先级不合法");
        }
    }

    private FeedbackPriority parsePriority(String value, FeedbackPriority fallback) {
        if (!StringUtils.hasText(value)) return fallback;
        return requirePriority(value);
    }

    private boolean requiresResolutionNote(String status) {
        return Set.of(FeedbackStatus.RESOLVED.name(), FeedbackStatus.CLOSED.name(), FeedbackStatus.REJECTED.name(),
                FeedbackStatus.DUPLICATE.name(), FeedbackStatus.UNREPRODUCIBLE.name()).contains(status);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String newTicketNo() {
        return "FB-" + LocalDate.now().format(TICKET_DATE) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
