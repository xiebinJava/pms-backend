package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
import com.brad.pms.common.enums.SystemStatus;
import com.brad.pms.common.enums.SystemVersionStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.page.PageResult;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.request.SystemPageQry;
import com.brad.pms.dto.request.SystemSaveCmd;
import com.brad.pms.dto.request.SystemStatusUpdateCmd;
import com.brad.pms.dto.request.SystemVersionPageQry;
import com.brad.pms.dto.request.SystemVersionSaveCmd;
import com.brad.pms.dto.request.SystemVersionStatusUpdateCmd;
import com.brad.pms.dto.response.SystemDTO;
import com.brad.pms.dto.response.SystemListDTO;
import com.brad.pms.dto.response.SystemVersionDetailDTO;
import com.brad.pms.dto.response.SystemVersionHistoryDTO;
import com.brad.pms.dto.response.SystemVersionListDTO;
import com.brad.pms.entity.SystemDO;
import com.brad.pms.entity.SystemVersionDO;
import com.brad.pms.entity.SystemVersionHistoryDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.SystemMapper;
import com.brad.pms.mapper.SystemVersionHistoryMapper;
import com.brad.pms.mapper.SystemVersionMapper;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import com.brad.pms.entity.ProjectNodeIterationPlanDO;
import com.brad.pms.security.AuthorizationService;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SystemVersionManagementService {
    private final SystemMapper systemMapper;
    private final SystemVersionMapper versionMapper;
    private final SystemVersionHistoryMapper historyMapper;
    private final ProjectNodeIterationPlanMapper iterationPlanMapper;
    private final UserService userService;
    private final AuthorizationService authorizationService;
    private final OperationLogService operationLogService;

    public PageResult<SystemListDTO> pageSystems(SystemPageQry qry) {
        authorizationService.require(PermissionCode.SYSTEM_VERSION_READ);
        SystemPageQry query = qry == null ? new SystemPageQry() : qry;
        String status = normalizeSystemStatusFilter(query.getStatus());
        boolean hasKeyword = StringUtils.hasText(query.getKeyword());
        LambdaQueryWrapper<SystemDO> wrapper = new LambdaQueryWrapper<SystemDO>()
                .like(hasKeyword, SystemDO::getName, query.getKeyword())
                .eq(status != null, SystemDO::getStatus, status)
                .orderByDesc(SystemDO::getUpdatedAt)
                .orderByDesc(SystemDO::getId);
        IPage<SystemDO> result = systemMapper.selectPage(
                new Page<>(query.getCurrPage(), query.getPageSize()), wrapper);
        List<SystemDO> systems = safe(result == null ? null : result.getRecords());
        Map<Long, UserDO> users = loadUsers(systems.stream()
                .flatMap(system -> java.util.stream.Stream.of(system.getOwnerId(), system.getCreatedBy()))
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        List<SystemListDTO> rows = systems.stream().map(system -> toSystemListDTO(system, users)).toList();
        long total = result == null ? 0 : result.getTotal();
        long page = result == null ? query.getCurrPage() : result.getCurrent();
        long size = result == null ? query.getPageSize() : result.getSize();
        return PageResult.of(total, page, size, rows);
    }

    @Transactional
    public Long createSystem(SystemSaveCmd cmd) {
        authorizationService.require(PermissionCode.SYSTEM_VERSION_WRITE);
        validateSystemSave(cmd);
        if (cmd.getOwnerId() != null) userService.requireActiveUser(cmd.getOwnerId());

        SystemDO system = new SystemDO();
        system.setName(normalizeRequired(cmd.getName(), "系统名称不能为空"));
        system.setDescription(trimToNull(cmd.getDescription()));
        system.setOwnerId(cmd.getOwnerId());
        system.setStatus(SystemStatus.ACTIVE.name());
        system.setVersion(0);
        system.setCreatedBy(UserContext.userIdOrNull());
        try {
            if (systemMapper.insert(system) != 1 || system.getId() == null) {
                throw BusinessException.conflict("系统创建失败，请重试");
            }
        } catch (DataIntegrityViolationException failure) {
            throw BusinessException.conflict("系统创建失败，请重试");
        }
        operationLogService.record(AuditEvent.success(AuditAction.SYSTEM_CREATED.name(),
                AuditResourceType.SYSTEM.name(), system.getId(), null, null, null, systemSnapshot(system)));
        return system.getId();
    }

    @Transactional
    public SystemDTO updateSystem(Long id, SystemSaveCmd cmd) {
        authorizationService.require(PermissionCode.SYSTEM_VERSION_WRITE);
        validateSystemSave(cmd);
        SystemDO system = requireSystemForUpdate(id);
        requireVersion(system.getVersion(), cmd.getVersion(), "系统");
        if (cmd.getOwnerId() != null && !Objects.equals(cmd.getOwnerId(), system.getOwnerId())) {
            userService.requireActiveUser(cmd.getOwnerId());
        }

        Map<String, Object> before = systemSnapshot(system);
        system.setName(normalizeRequired(cmd.getName(), "系统名称不能为空"));
        system.setDescription(trimToNull(cmd.getDescription()));
        system.setOwnerId(cmd.getOwnerId());
        try {
            if (systemMapper.updateById(system) != 1) {
                throw BusinessException.conflict("系统已被其他人修改，请刷新后重试");
            }
        } catch (DataIntegrityViolationException failure) {
            throw BusinessException.conflict("系统更新失败，请重试");
        }
        operationLogService.record(AuditEvent.success(AuditAction.SYSTEM_UPDATED.name(),
                AuditResourceType.SYSTEM.name(), id, null, null, before, systemSnapshot(system)));
        return toSystemDTO(system, loadUsers(userIds(system.getOwnerId(), system.getCreatedBy())));
    }

    @Transactional
    public SystemDTO changeSystemStatus(Long id, SystemStatusUpdateCmd cmd) {
        authorizationService.require(PermissionCode.SYSTEM_VERSION_MANAGE);
        validateStatusCommand(cmd == null ? null : cmd.getStatus(), cmd == null ? null : cmd.getVersion(),
                cmd == null ? null : cmd.getReason(), "系统");
        SystemStatus target = requireSystemStatus(cmd.getStatus());
        SystemDO system = requireSystemForUpdate(id);
        requireVersion(system.getVersion(), cmd.getVersion(), "系统");
        SystemStatus current = requireSystemStatus(system.getStatus());
        if (current == target) throw BusinessException.error("系统状态未发生变化");

        Map<String, Object> before = systemSnapshot(system);
        system.setStatus(target.name());
        if (systemMapper.updateById(system) != 1) {
            throw BusinessException.conflict("系统已被其他人修改，请刷新后重试");
        }
        operationLogService.record(AuditEvent.success(AuditAction.SYSTEM_STATUS_CHANGED.name(),
                AuditResourceType.SYSTEM.name(), id, null, cmd.getReason().trim(), before, systemSnapshot(system)));
        return toSystemDTO(system, loadUsers(userIds(system.getOwnerId(), system.getCreatedBy())));
    }

    public PageResult<SystemVersionListDTO> pageVersions(SystemVersionPageQry qry) {
        authorizationService.require(PermissionCode.SYSTEM_VERSION_READ);
        SystemVersionPageQry query = qry == null ? new SystemVersionPageQry() : qry;
        String status = normalizeVersionStatusFilter(query.getStatus());
        boolean hasKeyword = StringUtils.hasText(query.getKeyword());
        LambdaQueryWrapper<SystemVersionDO> wrapper = new LambdaQueryWrapper<SystemVersionDO>()
                .and(hasKeyword, condition -> condition.like(SystemVersionDO::getVersionNo, query.getKeyword())
                        .or().like(SystemVersionDO::getVersionName, query.getKeyword()))
                .eq(query.getSystemId() != null, SystemVersionDO::getSystemId, query.getSystemId())
                .eq(status != null, SystemVersionDO::getStatus, status)
                .orderByDesc(SystemVersionDO::getUpdatedAt)
                .orderByDesc(SystemVersionDO::getId);
        IPage<SystemVersionDO> result = versionMapper.selectPage(
                new Page<>(query.getCurrPage(), query.getPageSize()), wrapper);
        List<SystemVersionDO> versions = safe(result == null ? null : result.getRecords());
        Map<Long, SystemDO> systems = loadSystems(versions.stream()
                .map(SystemVersionDO::getSystemId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Set<Long> userIds = new HashSet<>();
        versions.forEach(version -> {
            if (version.getOwnerId() != null) userIds.add(version.getOwnerId());
            if (version.getCreatedBy() != null) userIds.add(version.getCreatedBy());
        });
        Map<Long, UserDO> users = loadUsers(userIds);
        List<SystemVersionListDTO> rows = versions.stream()
                .map(version -> toVersionListDTO(version, systems.get(version.getSystemId()), users)).toList();
        long total = result == null ? 0 : result.getTotal();
        long page = result == null ? query.getCurrPage() : result.getCurrent();
        long size = result == null ? query.getPageSize() : result.getSize();
        return PageResult.of(total, page, size, rows);
    }

    public SystemVersionDetailDTO detailVersion(Long id) {
        authorizationService.require(PermissionCode.SYSTEM_VERSION_READ);
        SystemVersionDO version = requireVersion(id);
        SystemDO system = systemMapper.selectById(version.getSystemId());
        if (system == null) throw BusinessException.notFound("所属系统不存在");
        Set<Long> userIds = new HashSet<>();
        if (version.getOwnerId() != null) userIds.add(version.getOwnerId());
        if (version.getCreatedBy() != null) userIds.add(version.getCreatedBy());
        return toVersionDetailDTO(version, system, loadUsers(userIds));
    }

    @Transactional
    public Long createVersion(SystemVersionSaveCmd cmd) {
        authorizationService.require(PermissionCode.SYSTEM_VERSION_WRITE);
        validateVersionSave(cmd);
        SystemDO system = systemMapper.selectByIdForUpdate(cmd.getSystemId());
        if (system == null) throw BusinessException.notFound("所属系统不存在");
        if (requireSystemStatus(system.getStatus()) != SystemStatus.ACTIVE) {
            throw BusinessException.error("系统已停用，不能创建新版本");
        }
        String versionNo = normalizeRequired(cmd.getVersionNo(), "版本号不能为空");
        if (versionMapper.findBySystemIdAndVersionNo(system.getId(), versionNo) != null) {
            throw BusinessException.conflict("同一系统内版本号已存在");
        }
        if (cmd.getOwnerId() != null) userService.requireActiveUser(cmd.getOwnerId());

        SystemVersionDO version = new SystemVersionDO();
        version.setSystemId(system.getId());
        version.setVersionNo(versionNo);
        version.setVersionName(normalizeRequired(cmd.getVersionName(), "版本名称不能为空"));
        version.setStatus(SystemVersionStatus.PLANNED.name());
        version.setPlannedReleaseDate(cmd.getPlannedReleaseDate());
        version.setReleaseNotes(trimToNull(cmd.getReleaseNotes()));
        version.setOwnerId(cmd.getOwnerId());
        version.setVersion(0);
        version.setCreatedBy(UserContext.userIdOrNull());
        try {
            if (versionMapper.insert(version) != 1 || version.getId() == null) {
                throw BusinessException.conflict("系统版本创建失败，请重试");
            }
        } catch (DataIntegrityViolationException duplicate) {
            throw BusinessException.conflict("同一系统内版本号已存在");
        }
        appendHistory(version, "CREATED", null, version.getStatus(), null);
        operationLogService.record(AuditEvent.success(AuditAction.SYSTEM_VERSION_CREATED.name(),
                AuditResourceType.SYSTEM_VERSION.name(), version.getId(), null, null, null,
                versionSnapshot(version)));
        return version.getId();
    }

    @Transactional
    public SystemVersionDetailDTO updateVersion(Long id, SystemVersionSaveCmd cmd) {
        authorizationService.require(PermissionCode.SYSTEM_VERSION_WRITE);
        validateVersionSave(cmd);
        SystemVersionDO version = requireVersionForUpdate(id);
        requireVersion(version.getVersion(), cmd.getVersion(), "系统版本");
        SystemVersionStatus currentStatus = requireVersionStatus(version.getStatus());
        if (currentStatus == SystemVersionStatus.RELEASED || currentStatus == SystemVersionStatus.ARCHIVED) {
            throw BusinessException.error("已发布或已归档的系统版本不可编辑");
        }
        if (!Objects.equals(version.getSystemId(), cmd.getSystemId())) {
            throw BusinessException.error("系统版本所属系统不可更改");
        }
        String versionNo = normalizeRequired(cmd.getVersionNo(), "版本号不能为空");
        SystemVersionDO sameVersion = versionMapper.findBySystemIdAndVersionNo(version.getSystemId(), versionNo);
        if (sameVersion != null && !Objects.equals(sameVersion.getId(), id)) {
            throw BusinessException.conflict("同一系统内版本号已存在");
        }
        if (cmd.getOwnerId() != null && !Objects.equals(cmd.getOwnerId(), version.getOwnerId())) {
            userService.requireActiveUser(cmd.getOwnerId());
        }

        Map<String, Object> before = versionSnapshot(version);
        version.setVersionNo(versionNo);
        version.setVersionName(normalizeRequired(cmd.getVersionName(), "版本名称不能为空"));
        version.setPlannedReleaseDate(cmd.getPlannedReleaseDate());
        version.setReleaseNotes(trimToNull(cmd.getReleaseNotes()));
        version.setOwnerId(cmd.getOwnerId());
        try {
            if (versionMapper.updateById(version) != 1) {
                throw BusinessException.conflict("系统版本已被其他人修改，请刷新后重试");
            }
        } catch (DataIntegrityViolationException duplicate) {
            throw BusinessException.conflict("同一系统内版本号已存在");
        }
        appendHistory(version, "UPDATED", null, null, null);
        operationLogService.record(AuditEvent.success(AuditAction.SYSTEM_VERSION_UPDATED.name(),
                AuditResourceType.SYSTEM_VERSION.name(), id, null, null, before, versionSnapshot(version)));
        SystemDO system = systemMapper.selectById(version.getSystemId());
        if (system == null) throw BusinessException.notFound("所属系统不存在");
        return toVersionDetailDTO(version, system, loadUsers(userIds(version.getOwnerId(), version.getCreatedBy())));
    }

    @Transactional
    public SystemVersionDetailDTO changeVersionStatus(Long id, SystemVersionStatusUpdateCmd cmd) {
        authorizationService.require(PermissionCode.SYSTEM_VERSION_MANAGE);
        validateStatusCommand(cmd == null ? null : cmd.getStatus(), cmd == null ? null : cmd.getVersion(),
                cmd == null ? null : cmd.getReason(), "系统版本");
        SystemVersionStatus target = requireVersionStatus(cmd.getStatus());
        SystemVersionDO version = requireVersionForUpdate(id);
        requireVersion(version.getVersion(), cmd.getVersion(), "系统版本");
        SystemVersionStatus current = requireVersionStatus(version.getStatus());
        if (!current.canTransitionTo(target)) {
            throw BusinessException.error("系统版本状态不允许从 " + current.name() + " 变更为 " + target.name());
        }

        Map<String, Object> before = versionSnapshot(version);
        version.setStatus(target.name());
        if (target == SystemVersionStatus.RELEASED) {
            version.setReleasedAt(LocalDateTime.now());
        } else if (current != SystemVersionStatus.RELEASED) {
            version.setReleasedAt(null);
        }
        if (versionMapper.updateById(version) != 1) {
            throw BusinessException.conflict("系统版本已被其他人修改，请刷新后重试");
        }
        String reason = cmd.getReason().trim();
        appendHistory(version, "STATUS_CHANGED", current.name(), target.name(), reason);
        operationLogService.record(AuditEvent.success(AuditAction.SYSTEM_VERSION_STATUS_CHANGED.name(),
                AuditResourceType.SYSTEM_VERSION.name(), id, null, reason, before, versionSnapshot(version)));
        SystemDO system = systemMapper.selectById(version.getSystemId());
        if (system == null) throw BusinessException.notFound("所属系统不存在");
        return toVersionDetailDTO(version, system, loadUsers(userIds(version.getOwnerId(), version.getCreatedBy())));
    }

    @Transactional
    public void deleteVersion(Long id) {
        authorizationService.require(PermissionCode.SYSTEM_VERSION_WRITE);
        SystemVersionDO version = requireVersionForUpdate(id);
        long iterationReferences = iterationPlanMapper.selectCount(new LambdaQueryWrapper<ProjectNodeIterationPlanDO>()
                .eq(ProjectNodeIterationPlanDO::getSystemVersionId, id));
        if (iterationReferences > 0) {
            throw BusinessException.error("系统版本已被迭代计划使用，请先解除关联后再删除");
        }
        if (versionMapper.deleteById(version.getId()) != 1) {
            throw BusinessException.conflict("系统版本已被其他人修改，请刷新后重试");
        }
    }

    public List<SystemVersionHistoryDTO> history(Long versionId) {
        authorizationService.require(PermissionCode.SYSTEM_VERSION_READ);
        requireVersion(versionId);
        List<SystemVersionHistoryDO> rows = safe(historyMapper.selectList(new LambdaQueryWrapper<SystemVersionHistoryDO>()
                .eq(SystemVersionHistoryDO::getVersionId, versionId)
                .orderByDesc(SystemVersionHistoryDO::getCreatedAt)
                .orderByDesc(SystemVersionHistoryDO::getId)));
        if (rows.isEmpty()) return List.of();
        Map<Long, UserDO> users = loadUsers(rows.stream().map(SystemVersionHistoryDO::getOperatorId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        return rows.stream().map(row -> {
            SystemVersionHistoryDTO dto = new SystemVersionHistoryDTO();
            dto.setId(row.getId());
            dto.setVersionId(row.getVersionId());
            dto.setAction(row.getAction());
            dto.setFromStatus(row.getFromStatus());
            dto.setToStatus(row.getToStatus());
            dto.setReason(row.getReason());
            dto.setOperatorId(row.getOperatorId());
            dto.setOperatorName(Convertors.userDisplayName(users.get(row.getOperatorId())));
            dto.setCreatedAt(row.getCreatedAt());
            return dto;
        }).toList();
    }

    private SystemDO requireSystemForUpdate(Long id) {
        if (id == null) throw BusinessException.notFound("系统不存在");
        SystemDO system = systemMapper.selectByIdForUpdate(id);
        if (system == null) throw BusinessException.notFound("系统不存在");
        return system;
    }

    private SystemVersionDO requireVersion(Long id) {
        if (id == null) throw BusinessException.notFound("系统版本不存在");
        SystemVersionDO version = versionMapper.selectById(id);
        if (version == null) throw BusinessException.notFound("系统版本不存在");
        return version;
    }

    private SystemVersionDO requireVersionForUpdate(Long id) {
        if (id == null) throw BusinessException.notFound("系统版本不存在");
        SystemVersionDO version = versionMapper.selectByIdForUpdate(id);
        if (version == null) throw BusinessException.notFound("系统版本不存在");
        return version;
    }

    private void appendHistory(SystemVersionDO version, String action, String fromStatus,
                               String toStatus, String reason) {
        SystemVersionHistoryDO history = new SystemVersionHistoryDO();
        history.setVersionId(version.getId());
        history.setAction(action);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setReason(trimToNull(reason));
        history.setOperatorId(UserContext.userIdOrNull());
        history.setCreatedAt(LocalDateTime.now());
        if (historyMapper.insert(history) != 1) {
            throw BusinessException.conflict("系统版本历史记录失败，请重试");
        }
    }

    private SystemListDTO toSystemListDTO(SystemDO system, Map<Long, UserDO> users) {
        SystemListDTO dto = new SystemListDTO();
        setSystemFields(dto, system, users);
        return dto;
    }

    private SystemDTO toSystemDTO(SystemDO system, Map<Long, UserDO> users) {
        SystemDTO dto = new SystemDTO();
        setSystemFields(dto, system, users);
        return dto;
    }

    private void setSystemFields(SystemListDTO dto, SystemDO system, Map<Long, UserDO> users) {
        dto.setId(system.getId());
        dto.setName(system.getName());
        dto.setDescription(system.getDescription());
        dto.setOwnerId(system.getOwnerId());
        dto.setOwnerName(Convertors.userDisplayName(users.get(system.getOwnerId())));
        dto.setStatus(system.getStatus());
        dto.setVersion(system.getVersion());
        dto.setCreatedBy(system.getCreatedBy());
        dto.setCreatedByName(Convertors.userDisplayName(users.get(system.getCreatedBy())));
        dto.setCreatedAt(system.getCreatedAt());
        dto.setUpdatedAt(system.getUpdatedAt());
    }

    private void setSystemFields(SystemDTO dto, SystemDO system, Map<Long, UserDO> users) {
        dto.setId(system.getId());
        dto.setName(system.getName());
        dto.setDescription(system.getDescription());
        dto.setOwnerId(system.getOwnerId());
        dto.setOwnerName(Convertors.userDisplayName(users.get(system.getOwnerId())));
        dto.setStatus(system.getStatus());
        dto.setVersion(system.getVersion());
        dto.setCreatedBy(system.getCreatedBy());
        dto.setCreatedByName(Convertors.userDisplayName(users.get(system.getCreatedBy())));
        dto.setCreatedAt(system.getCreatedAt());
        dto.setUpdatedAt(system.getUpdatedAt());
    }

    private SystemVersionListDTO toVersionListDTO(SystemVersionDO version, SystemDO system,
                                                   Map<Long, UserDO> users) {
        SystemVersionListDTO dto = new SystemVersionListDTO();
        setVersionListFields(dto, version, system, users);
        return dto;
    }

    private SystemVersionDetailDTO toVersionDetailDTO(SystemVersionDO version, SystemDO system,
                                                      Map<Long, UserDO> users) {
        SystemVersionDetailDTO dto = new SystemVersionDetailDTO();
        dto.setId(version.getId());
        dto.setSystemId(version.getSystemId());
        dto.setSystemName(system.getName());
        dto.setVersionNo(version.getVersionNo());
        dto.setVersionName(version.getVersionName());
        dto.setStatus(version.getStatus());
        dto.setPlannedReleaseDate(version.getPlannedReleaseDate());
        dto.setReleasedAt(version.getReleasedAt());
        dto.setReleaseNotes(version.getReleaseNotes());
        dto.setOwnerId(version.getOwnerId());
        dto.setOwnerName(Convertors.userDisplayName(users.get(version.getOwnerId())));
        dto.setVersion(version.getVersion());
        dto.setCreatedBy(version.getCreatedBy());
        dto.setCreatedByName(Convertors.userDisplayName(users.get(version.getCreatedBy())));
        dto.setCreatedAt(version.getCreatedAt());
        dto.setUpdatedAt(version.getUpdatedAt());
        return dto;
    }

    private void setVersionListFields(SystemVersionListDTO dto, SystemVersionDO version,
                                      SystemDO system, Map<Long, UserDO> users) {
        dto.setId(version.getId());
        dto.setSystemId(version.getSystemId());
        if (system != null) {
            dto.setSystemName(system.getName());
        }
        dto.setVersionNo(version.getVersionNo());
        dto.setVersionName(version.getVersionName());
        dto.setStatus(version.getStatus());
        dto.setPlannedReleaseDate(version.getPlannedReleaseDate());
        dto.setReleasedAt(version.getReleasedAt());
        dto.setOwnerId(version.getOwnerId());
        dto.setOwnerName(Convertors.userDisplayName(users.get(version.getOwnerId())));
        dto.setVersion(version.getVersion());
        dto.setCreatedBy(version.getCreatedBy());
        dto.setCreatedByName(Convertors.userDisplayName(users.get(version.getCreatedBy())));
        dto.setCreatedAt(version.getCreatedAt());
        dto.setUpdatedAt(version.getUpdatedAt());
    }

    private Map<String, Object> systemSnapshot(SystemDO system) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", system.getId());
        snapshot.put("name", system.getName());
        snapshot.put("description", system.getDescription());
        snapshot.put("ownerId", system.getOwnerId());
        snapshot.put("status", system.getStatus());
        snapshot.put("version", system.getVersion());
        return snapshot;
    }

    private Map<String, Object> versionSnapshot(SystemVersionDO version) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", version.getId());
        snapshot.put("systemId", version.getSystemId());
        snapshot.put("versionNo", version.getVersionNo());
        snapshot.put("versionName", version.getVersionName());
        snapshot.put("status", version.getStatus());
        snapshot.put("plannedReleaseDate", version.getPlannedReleaseDate());
        snapshot.put("releasedAt", version.getReleasedAt());
        snapshot.put("releaseNotes", version.getReleaseNotes());
        snapshot.put("ownerId", version.getOwnerId());
        snapshot.put("version", version.getVersion());
        return snapshot;
    }

    private Map<Long, UserDO> loadUsers(Collection<Long> ids) {
        Set<Long> normalized = ids == null ? Set.of() : ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (normalized.isEmpty()) return new HashMap<>();
        List<UserDO> users = userService.listByIdsIncludingDeleted(normalized);
        if (users == null) return new HashMap<>();
        return users.stream().filter(Objects::nonNull).filter(user -> user.getId() != null)
                .collect(Collectors.toMap(UserDO::getId, Function.identity(), (left, right) -> left));
    }

    private Set<Long> userIds(Long... ids) {
        Set<Long> result = new HashSet<>();
        if (ids != null) {
            for (Long id : ids) if (id != null) result.add(id);
        }
        return result;
    }

    private Map<Long, SystemDO> loadSystems(Collection<Long> ids) {
        Set<Long> normalized = ids == null ? Set.of() : ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (normalized.isEmpty()) return new HashMap<>();
        List<SystemDO> systems = systemMapper.selectBatchIds(normalized);
        if (systems == null) return new HashMap<>();
        return systems.stream().filter(Objects::nonNull).filter(system -> system.getId() != null)
                .collect(Collectors.toMap(SystemDO::getId, Function.identity(), (left, right) -> left));
    }

    private void validateSystemSave(SystemSaveCmd cmd) {
        if (cmd == null) throw BusinessException.error("系统参数不能为空");
        if (!StringUtils.hasText(cmd.getName())) throw BusinessException.error("系统名称不能为空");
        if (cmd.getName().trim().length() > 200) throw BusinessException.error("系统名称不能超过 200 个字符");
        if (cmd.getDescription() != null && cmd.getDescription().length() > 5000) {
            throw BusinessException.error("系统描述不能超过 5000 个字符");
        }
    }

    private void validateVersionSave(SystemVersionSaveCmd cmd) {
        if (cmd == null) throw BusinessException.error("系统版本参数不能为空");
        if (cmd.getSystemId() == null) throw BusinessException.error("所属系统不能为空");
        if (!StringUtils.hasText(cmd.getVersionNo())) throw BusinessException.error("版本号不能为空");
        if (!StringUtils.hasText(cmd.getVersionName())) throw BusinessException.error("版本名称不能为空");
        if (cmd.getVersionNo().trim().length() > 64) throw BusinessException.error("版本号不能超过 64 个字符");
        if (cmd.getVersionName().trim().length() > 200) throw BusinessException.error("版本名称不能超过 200 个字符");
        if (cmd.getReleaseNotes() != null && cmd.getReleaseNotes().length() > 10000) {
            throw BusinessException.error("发布说明不能超过 10000 个字符");
        }
    }

    private void validateStatusCommand(String status, Integer version, String reason, String resourceName) {
        if (!StringUtils.hasText(status)) throw BusinessException.error(resourceName + "状态不能为空");
        if (version == null) throw BusinessException.conflict(resourceName + "已被其他人修改，请刷新后重试");
        if (!StringUtils.hasText(reason)) throw BusinessException.error("原因不能为空");
        if (reason.trim().length() > 500) throw BusinessException.error("原因不能超过 500 个字符");
    }

    private String normalizeSystemStatusFilter(String status) {
        if (!StringUtils.hasText(status)) return null;
        return requireSystemStatus(status).name();
    }

    private String normalizeVersionStatusFilter(String status) {
        if (!StringUtils.hasText(status)) return null;
        return requireVersionStatus(status).name();
    }

    private SystemStatus requireSystemStatus(String value) {
        SystemStatus status = SystemStatus.parse(value);
        if (status == null) throw BusinessException.error("系统状态不合法");
        return status;
    }

    private SystemVersionStatus requireVersionStatus(String value) {
        SystemVersionStatus status = SystemVersionStatus.parse(value);
        if (status == null) throw BusinessException.error("系统版本状态不合法");
        return status;
    }

    private void requireVersion(Integer current, Integer expected, String resourceName) {
        if (!Objects.equals(current, expected)) {
            throw BusinessException.conflict(resourceName + "已被其他人修改，请刷新后重试");
        }
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) throw BusinessException.error(message);
        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
