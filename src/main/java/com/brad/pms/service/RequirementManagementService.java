package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
import com.brad.pms.common.enums.RequirementExecutionTargetType;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.page.PageResult;
import com.brad.pms.dto.request.RequirementPageQry;
import com.brad.pms.dto.request.RequirementSaveCmd;
import com.brad.pms.dto.response.RequirementListDTO;
import com.brad.pms.entity.RequirementDO;
import com.brad.pms.mapper.RequirementMapper;
import com.brad.pms.security.UserContext;
import com.brad.pms.workflow.DevelopmentItemType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RequirementManagementService {
    private final RequirementMapper requirementMapper;
    private final DevelopmentItemWorkflowService workflowService;
    private final RequirementExecutionTargetReadService targetReadService;
    private final UserService userService;
    private final OperationLogService operationLogService;

    @Transactional
    public Long create(RequirementSaveCmd cmd) {
        validate(cmd);
        if (cmd.getOwnerId() != null) userService.requireActiveUser(cmd.getOwnerId());
        RequirementDO requirement = new RequirementDO();
        requirement.setTitle(cmd.getTitle().trim());
        requirement.setDescription(trimToNull(cmd.getDescription()));
        requirement.setPriority(cmd.getPriority() == null ? 1 : cmd.getPriority());
        requirement.setOwnerId(cmd.getOwnerId());
        requirement.setStatus("ACTIVE");
        requirement.setDeleted(false);
        requirement.setVersion(0);
        requirement.setCreatedBy(UserContext.userIdOrNull());
        if (requirementMapper.insert(requirement) != 1 || requirement.getId() == null) {
            throw BusinessException.conflict("需求创建失败，请重试");
        }
        if (cmd.getTemplateVersionId() == null) {
            workflowService.createIfDefaultExists(DevelopmentItemType.REQUIREMENT, requirement.getId(), null, null);
        } else {
            workflowService.createWithTemplate(DevelopmentItemType.REQUIREMENT, requirement.getId(), null, null,
                    cmd.getTemplateVersionId());
        }
        operationLogService.record(AuditEvent.success(AuditAction.REQUIREMENT_CREATED.name(),
                AuditResourceType.REQUIREMENT.name(), requirement.getId(), null, null, null, snapshot(requirement)));
        return requirement.getId();
    }

    @Transactional
    public void update(Long id, RequirementSaveCmd cmd) {
        if (id == null) throw BusinessException.notFound("需求不存在");
        validate(cmd);
        RequirementDO requirement = requirementMapper.selectByIdForUpdate(id);
        if (requirement == null || Boolean.TRUE.equals(requirement.getDeleted())) throw BusinessException.notFound("需求不存在");
        requireVersion(requirement, cmd.getVersion());
        if (cmd.getOwnerId() != null && !Objects.equals(cmd.getOwnerId(), requirement.getOwnerId())) {
            userService.requireActiveUser(cmd.getOwnerId());
        }
        requirement.setTitle(cmd.getTitle().trim());
        requirement.setDescription(trimToNull(cmd.getDescription()));
        if (cmd.getPriority() != null) requirement.setPriority(cmd.getPriority());
        requirement.setOwnerId(cmd.getOwnerId());
        if (requirementMapper.updateById(requirement) != 1) {
            throw BusinessException.conflict("需求已被其他人修改，请刷新后重试");
        }
        operationLogService.record(AuditEvent.success(AuditAction.REQUIREMENT_UPDATED.name(),
                AuditResourceType.REQUIREMENT.name(), id, null, null, null, snapshot(requirement)));
    }

    @Transactional
    public void delete(Long id) {
        RequirementDO requirement = requireForMutation(id, false);
        requirement.setDeleted(true);
        if (requirementMapper.updateById(requirement) != 1) {
            throw BusinessException.conflict("需求已被其他人修改，请刷新后重试");
        }
        operationLogService.record(AuditEvent.success(AuditAction.REQUIREMENT_DELETED.name(),
                AuditResourceType.REQUIREMENT.name(), id, null, null, null, snapshot(requirement)));
    }

    @Transactional
    public void restore(Long id) {
        RequirementDO requirement = requireForMutation(id, true);
        requirement.setDeleted(false);
        if (requirementMapper.updateById(requirement) != 1) {
            throw BusinessException.conflict("需求已被其他人修改，请刷新后重试");
        }
        operationLogService.record(AuditEvent.success(AuditAction.REQUIREMENT_RESTORED.name(),
                AuditResourceType.REQUIREMENT.name(), id, null, null, null, snapshot(requirement)));
    }

    public RequirementListDTO detail(Long id) {
        RequirementDO requirement = requirementMapper.selectById(id);
        if (requirement == null || Boolean.TRUE.equals(requirement.getDeleted())) throw BusinessException.notFound("需求不存在");
        return toDTO(requirement);
    }

    public PageResult<RequirementListDTO> page(RequirementPageQry qry) {
        RequirementPageQry query = qry == null ? new RequirementPageQry() : qry;
        boolean includeDeleted = Boolean.TRUE.equals(query.getDeleted());
        LambdaQueryWrapper<RequirementDO> wrapper = new LambdaQueryWrapper<RequirementDO>()
                .eq(RequirementDO::getDeleted, includeDeleted)
                .like(StringUtils.hasText(query.getKeyword()), RequirementDO::getTitle, query.getKeyword())
                .eq(StringUtils.hasText(query.getStatus()), RequirementDO::getStatus, query.getStatus())
                .eq(query.getOwnerId() != null, RequirementDO::getOwnerId, query.getOwnerId())
                .eq(query.getTargetType() != null, RequirementDO::getExecutionTargetType, query.getTargetType())
                .orderByDesc(RequirementDO::getUpdatedAt)
                .orderByDesc(RequirementDO::getId);
        List<RequirementListDTO> items = safe(requirementMapper.selectList(wrapper)).stream()
                .map(this::toDTO).toList();
        int page = query.getCurrPage();
        int size = query.getPageSize();
        int from = Math.min((page - 1) * size, items.size());
        int to = Math.min(from + size, items.size());
        return PageResult.of(items.size(), page, size, items.subList(from, to));
    }

    private RequirementDO requireForMutation(Long id, boolean restore) {
        if (id == null) throw BusinessException.notFound("需求不存在");
        RequirementDO requirement = requirementMapper.selectByIdForUpdate(id);
        if (requirement == null || Boolean.TRUE.equals(requirement.getDeleted()) != restore) {
            throw BusinessException.notFound("需求不存在");
        }
        return requirement;
    }

    private void validate(RequirementSaveCmd cmd) {
        if (cmd == null || !StringUtils.hasText(cmd.getTitle())) throw BusinessException.error("需求名称不能为空");
        cmd.setTitle(cmd.getTitle().trim());
        if (cmd.getPriority() != null && (cmd.getPriority() < 0 || cmd.getPriority() > 3)) {
            throw BusinessException.error("需求优先级不合法");
        }
    }

    private void requireVersion(RequirementDO requirement, Integer expected) {
        if (!Objects.equals(requirement.getVersion(), expected)) {
            throw BusinessException.conflict("需求已被其他人修改，请刷新后重试");
        }
    }

    private RequirementListDTO toDTO(RequirementDO requirement) {
        RequirementListDTO dto = new RequirementListDTO();
        dto.setId(requirement.getId());
        dto.setTitle(requirement.getTitle());
        dto.setDescription(requirement.getDescription());
        dto.setPriority(requirement.getPriority());
        dto.setOwnerId(requirement.getOwnerId());
        dto.setOwnerName(requirement.getOwnerId() == null ? null
                : userService.listByIdsIncludingDeleted(List.of(requirement.getOwnerId())).stream().findFirst()
                .map(com.brad.pms.convertor.Convertors::userDisplayName).orElse(null));
        dto.setStatus(requirement.getStatus());
        dto.setDeleted(requirement.getDeleted());
        dto.setVersion(requirement.getVersion());
        dto.setWorkflowConfigured(null);
        dto.setExecutionTarget(targetReadService.findCurrentTarget(requirement));
        return dto;
    }

    private java.util.Map<String, Object> snapshot(RequirementDO requirement) {
        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("title", requirement.getTitle());
        snapshot.put("priority", requirement.getPriority());
        snapshot.put("ownerId", requirement.getOwnerId());
        snapshot.put("status", requirement.getStatus());
        snapshot.put("deleted", requirement.getDeleted());
        snapshot.put("executionTargetType", requirement.getExecutionTargetType());
        snapshot.put("executionTargetId", requirement.getExecutionTargetId());
        return snapshot;
    }

    private <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
