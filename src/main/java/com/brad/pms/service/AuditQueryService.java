package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.page.PageResult;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.response.AuditLogDTO;
import com.brad.pms.entity.OperationLogDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.OperationLogMapper;
import com.brad.pms.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditQueryService {
    private final OperationLogMapper operationLogMapper;
    private final UserService userService;
    private final ProjectMapper projectMapper;

    public PageResult<AuditLogDTO> page(Query query) {
        long safePage = Math.max(query.currPage(), 1);
        long safeSize = Math.min(Math.max(query.pageSize(), 1), 100);
        IPage<OperationLogDO> page = operationLogMapper.selectPage(new Page<>(safePage, safeSize), buildQuery(query));
        Map<Long, UserDO> users = loadUsers(page.getRecords());
        Map<Long, ProjectDO> projects = loadProjects(page.getRecords());
        List<AuditLogDTO> records = page.getRecords().stream()
                .map(row -> toDto(row, users.get(row.getOperatorId()), projects.get(row.getProjectId())))
                .collect(Collectors.toList());
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    public AuditLogDTO get(Long id) {
        OperationLogDO row = operationLogMapper.selectById(id);
        if (row == null) throw BusinessException.error("审计记录不存在");
        Map<Long, UserDO> users = loadUsers(List.of(row));
        Map<Long, ProjectDO> projects = loadProjects(List.of(row));
        return toDto(row, users.get(row.getOperatorId()), projects.get(row.getProjectId()));
    }

    static QueryWrapper<OperationLogDO> buildQuery(Query query) {
        return new QueryWrapper<OperationLogDO>()
                .eq(query.action() != null && !query.action().isBlank(), "action", trim(query.action()))
                .eq(query.resourceType() != null && !query.resourceType().isBlank(), "resource_type", trim(query.resourceType()))
                .eq(query.resourceId() != null, "resource_id", query.resourceId())
                .eq(query.operatorId() != null, "operator_id", query.operatorId())
                .eq(query.projectId() != null, "project_id", query.projectId())
                .eq(query.result() != null && !query.result().isBlank(), "result", trim(query.result()))
                .eq(query.requestId() != null && !query.requestId().isBlank(), "request_id", trim(query.requestId()))
                .ge(query.from() != null, "created_at", query.from())
                .lt(query.to() != null, "created_at", query.to())
                .orderByDesc("created_at").orderByDesc("id");
    }

    private Map<Long, UserDO> loadUsers(List<OperationLogDO> rows) {
        List<Long> ids = rows.stream().map(OperationLogDO::getOperatorId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) return Map.of();
        return userService.listByIds(ids).stream()
                .collect(Collectors.toMap(UserDO::getId, user -> user, (left, right) -> left));
    }

    private Map<Long, ProjectDO> loadProjects(List<OperationLogDO> rows) {
        List<Long> ids = rows.stream().map(OperationLogDO::getProjectId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return projectMapper.selectIncludingDeletedByIds(ids).stream()
                .collect(Collectors.toMap(ProjectDO::getId, project -> project, (left, right) -> left));
    }

    private AuditLogDTO toDto(OperationLogDO row, UserDO operator, ProjectDO project) {
        AuditLogDTO dto = new AuditLogDTO();
        dto.setId(row.getId());
        dto.setOperatorId(row.getOperatorId());
        dto.setOperatorDisplayName(operator == null ? null : Convertors.userDisplayName(operator));
        dto.setAction(row.getAction());
        dto.setResourceType(row.getResourceType());
        dto.setResourceId(row.getResourceId());
        dto.setProjectId(row.getProjectId());
        dto.setProjectName(project == null ? null : project.getName());
        dto.setBeforeJson(row.getBeforeJson());
        dto.setAfterJson(row.getAfterJson());
        dto.setReason(row.getReason());
        dto.setResult(row.getResult());
        dto.setRequestId(row.getRequestId());
        dto.setCreatedAt(row.getCreatedAt());
        return dto;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    public record Query(String action, String resourceType, Long resourceId, Long operatorId,
                        Long projectId, String result, String requestId,
                        LocalDateTime from, LocalDateTime to, long currPage, long pageSize) {
    }
}
