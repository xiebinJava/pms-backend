package com.brad.pms.service;

import com.brad.pms.common.enums.SystemVersionStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodeIterationPlanCmd;
import com.brad.pms.entity.SystemDO;
import com.brad.pms.entity.SystemVersionDO;
import com.brad.pms.mapper.SystemMapper;
import com.brad.pms.mapper.SystemVersionMapper;
import com.brad.pms.security.AuthorizationService;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates and resolves the optional system version reference on an iteration plan. */
@Service
@RequiredArgsConstructor
public class SystemVersionReferenceService {

    private static final Set<SystemVersionStatus> BINDABLE_STATUSES = Set.of(
            SystemVersionStatus.PLANNED,
            SystemVersionStatus.DEVELOPING);

    private final SystemVersionMapper versionMapper;
    private final SystemMapper systemMapper;
    private final AuthorizationService authorizationService;

    /** Returns the requested value, or preserves the current value for old/partial requests. */
    public Long resolveForSave(NodeIterationPlanCmd cmd, Long currentVersionId) {
        return cmd != null && cmd.isSystemVersionIdSpecified()
                ? cmd.getSystemVersionId() : currentVersionId;
    }

    public void validateForSave(Long requestedVersionId, Long currentVersionId) {
        validateForSave(List.of(new Binding(requestedVersionId, currentVersionId)));
    }

    /** Ownership is checked even for unchanged terminal versions. */
    public void validateSystem(Long versionId, Long systemId) {
        if (versionId == null) return;
        SystemVersionDO version = versionMapper.selectByIdForUpdate(versionId);
        if (version == null) throw BusinessException.notFound("系统版本不存在");
        if (systemId == null || !Objects.equals(version.getSystemId(), systemId)) {
            throw BusinessException.conflict("系统版本必须属于迭代的所属系统");
        }
    }

    /** Batch validates only actual replacements; unchanged terminal references are intentionally allowed. */
    public void validateForSave(Collection<Binding> bindings) {
        if (bindings == null || bindings.isEmpty()) return;
        Set<Long> changedVersionIds = bindings.stream()
                .filter(Objects::nonNull)
                .filter(binding -> binding.requestedVersionId() != null
                        && !Objects.equals(binding.requestedVersionId(), binding.currentVersionId()))
                .map(Binding::requestedVersionId)
                .collect(Collectors.toSet());
        if (changedVersionIds.isEmpty()) return;

        if (UserContext.get() != null) {
            authorizationService.require(PermissionCode.SYSTEM_VERSION_READ);
        }
        List<SystemVersionDO> versions = versionMapper.selectBatchByIdsForUpdate(changedVersionIds);
        Map<Long, SystemVersionDO> versionsById = (versions == null ? List.<SystemVersionDO>of() : versions).stream()
                .filter(Objects::nonNull)
                .filter(version -> version.getId() != null)
                .collect(Collectors.toMap(SystemVersionDO::getId, version -> version,
                        (left, right) -> left));
        for (Long versionId : changedVersionIds) {
            SystemVersionDO version = versionsById.get(versionId);
            if (version == null) throw BusinessException.notFound("系统版本不存在");
            SystemVersionStatus status = SystemVersionStatus.parse(version.getStatus());
            if (status == null) throw BusinessException.error("系统版本状态不合法");
            if (!BINDABLE_STATUSES.contains(status)) {
                throw BusinessException.error("系统版本当前为 " + status.name() + "，不能绑定迭代计划");
            }
        }
    }

    /** Loads version and system labels in two batch queries for plan aggregates. */
    public Map<Long, Reference> load(Collection<Long> versionIds) {
        Set<Long> ids = versionIds == null ? Set.of() : versionIds.stream()
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        List<SystemVersionDO> versions = versionMapper.selectBatchIds(ids);
        if (versions == null || versions.isEmpty()) return Map.of();
        Set<Long> systemIds = versions.stream().map(SystemVersionDO::getSystemId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<SystemDO> systems = systemIds.isEmpty() ? List.of() : systemMapper.selectBatchIds(systemIds);
        Map<Long, SystemDO> loadedSystems = new HashMap<>();
        if (systems != null) {
            systems.stream().filter(Objects::nonNull).filter(system -> system.getId() != null)
                    .forEach(system -> loadedSystems.put(system.getId(), system));
        }
        Map<Long, Reference> result = new LinkedHashMap<>();
        for (SystemVersionDO version : versions) {
            if (version == null || version.getId() == null) continue;
            SystemDO system = loadedSystems.get(version.getSystemId());
            result.put(version.getId(), new Reference(version.getId(), version.getVersionNo(),
                    version.getVersionName(), system == null ? null : system.getName()));
        }
        return result;
    }

    public record Binding(Long requestedVersionId, Long currentVersionId) { }

    public record Reference(Long id, String versionNo, String versionName, String systemName) { }
}
