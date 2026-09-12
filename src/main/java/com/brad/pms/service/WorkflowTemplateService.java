package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.ProjectTypeSaveCmd;
import com.brad.pms.dto.request.WorkflowTemplateSaveCmd;
import com.brad.pms.dto.response.ProjectTypeDTO;
import com.brad.pms.dto.response.WorkflowTemplateDTO;
import com.brad.pms.dto.response.WorkflowTemplateOptionsDTO;
import com.brad.pms.dto.response.WorkflowTemplateSummaryDTO;
import com.brad.pms.dto.response.WorkflowTemplateVersionSummaryDTO;
import com.brad.pms.entity.ProjectTypeDO;
import com.brad.pms.entity.WorkflowTemplateDO;
import com.brad.pms.entity.WorkflowTemplateVersionDO;
import com.brad.pms.mapper.ProjectTypeMapper;
import com.brad.pms.mapper.WorkflowTemplateMapper;
import com.brad.pms.mapper.WorkflowTemplateVersionMapper;
import com.brad.pms.security.UserContext;
import com.brad.pms.workflow.BuiltInWorkflowTemplate;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import com.brad.pms.workflow.WorkflowTemplateDefinition;
import com.brad.pms.workflow.WorkflowTemplateDefinitionValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkflowTemplateService {
    private final ProjectTypeMapper projectTypeMapper;
    private final WorkflowTemplateMapper templateMapper;
    private final WorkflowTemplateVersionMapper versionMapper;
    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    public List<ProjectTypeDTO> listProjectTypes() {
        return projectTypeMapper.selectList(new LambdaQueryWrapper<ProjectTypeDO>()
                        .eq(ProjectTypeDO::getStatus, 1)
                        .orderByAsc(ProjectTypeDO::getSort)
                        .orderByAsc(ProjectTypeDO::getId))
                .stream().map(this::toProjectTypeDTO).toList();
    }

    public ProjectTypeDTO createProjectType(ProjectTypeSaveCmd cmd) {
        ProjectTypeDO type = new ProjectTypeDO();
        type.setCode(cmd.getCode().trim());
        type.setName(cmd.getName().trim());
        type.setDescription(trimToNull(cmd.getDescription()));
        type.setSort(cmd.getSort() == null ? 0 : cmd.getSort());
        type.setStatus(1);
        type.setDeleted(false);
        try {
            projectTypeMapper.insert(type);
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.conflict("项目类型标识已存在");
        }
        operationLogService.record(AuditEvent.success(AuditAction.PROJECT_TYPE_CREATED.name(),
                AuditResourceType.PROJECT_TYPE.name(), type.getId(), null, null, null,
                Map.of("code", type.getCode(), "name", type.getName())));
        return toProjectTypeDTO(type);
    }

    public List<WorkflowTemplateSummaryDTO> listTemplates(Long projectTypeId, boolean publishedOnly) {
        List<WorkflowTemplateDO> templates = templateMapper.selectList(new LambdaQueryWrapper<WorkflowTemplateDO>()
                .eq(projectTypeId != null, WorkflowTemplateDO::getProjectTypeId, projectTypeId)
                .orderByAsc(WorkflowTemplateDO::getProjectTypeId)
                .orderByAsc(WorkflowTemplateDO::getId));
        Map<Long, ProjectTypeDO> types = projectTypeMapper.selectList(null).stream()
                .collect(Collectors.toMap(ProjectTypeDO::getId, type -> type));
        List<WorkflowTemplateSummaryDTO> summaries = templates.stream()
                .map(template -> toSummary(template, types.get(template.getProjectTypeId())))
                .filter(summary -> !publishedOnly || summary.getPublishedVersionId() != null)
                .toList();
        return summaries;
    }

    public WorkflowTemplateOptionsDTO options() {
        WorkflowTemplateOptionsDTO options = new WorkflowTemplateOptionsDTO();
        options.setProjectTypes(listProjectTypes());
        options.setTemplates(listTemplates(null, true));
        return options;
    }

    public WorkflowTemplateDTO getTemplate(Long templateId) {
        return toTemplateDTO(requireTemplate(templateId));
    }

    @Transactional
    public WorkflowTemplateDTO saveDraft(Long templateId, WorkflowTemplateSaveCmd cmd) {
        WorkflowTemplateDefinition definition;
        try {
            definition = WorkflowTemplateDefinitionValidator.validate(cmd.getDefinition());
        } catch (IllegalArgumentException e) {
            throw BusinessException.error(e.getMessage());
        }
        WorkflowTemplateDO template;
        if (templateId == null) {
            ProjectTypeDO type = requireActiveType(cmd.getProjectTypeId());
            template = new WorkflowTemplateDO();
            template.setCode("wf-" + UUID.randomUUID().toString().replace("-", ""));
            template.setProjectTypeId(type.getId());
            template.setName(cmd.getName().trim());
            template.setDescription(trimToNull(cmd.getDescription()));
            template.setLatestVersionNo(0);
            template.setDeleted(false);
            template.setCreatedBy(UserContext.userIdOrNull());
            if (templateMapper.insert(template) != 1) {
                throw BusinessException.conflict("流程模板未能创建，请重试");
            }
        } else {
            template = requireTemplate(templateId);
            if (cmd.getProjectTypeId() != null && !cmd.getProjectTypeId().equals(template.getProjectTypeId())) {
                throw BusinessException.error("流程模板所属项目类型不可更改");
            }
        }

        WorkflowTemplateVersionDO draft = findLatestVersion(template.getId(), "DRAFT");
        if (templateId != null) {
            Integer currentRevision = draft == null ? null : draft.getVersion();
            if (!Objects.equals(cmd.getExpectedDraftRevision(), currentRevision)) {
                throw BusinessException.conflict("流程草稿已被其他人修改，请刷新后重试");
            }
            template.setName(cmd.getName().trim());
            template.setDescription(trimToNull(cmd.getDescription()));
            if (templateMapper.updateById(template) != 1) {
                throw BusinessException.conflict("流程模板无法更新，请刷新后重试");
            }
        }
        if (draft == null) {
            draft = new WorkflowTemplateVersionDO();
            draft.setTemplateId(template.getId());
            draft.setVersionNo((template.getLatestVersionNo() == null ? 0 : template.getLatestVersionNo()) + 1);
            draft.setStatus("DRAFT");
            draft.setCreatedBy(UserContext.userIdOrNull());
        }
        draft.setDefinitionJson(serialize(definition));
        if (draft.getId() == null) {
            try {
                if (versionMapper.insert(draft) != 1) {
                    throw BusinessException.conflict("流程草稿未能保存，请重试");
                }
            } catch (DuplicateKeyException e) {
                throw BusinessException.conflict("流程草稿已被其他人创建，请刷新后重试");
            }
        } else if (versionMapper.updateById(draft) != 1) {
            throw BusinessException.conflict("流程草稿已被其他人修改，请刷新后重试");
        }
        template.setLatestVersionNo(Math.max(template.getLatestVersionNo() == null ? 0 : template.getLatestVersionNo(), draft.getVersionNo()));
        if (templateMapper.updateById(template) != 1) {
            throw BusinessException.conflict("流程模板无法更新，请刷新后重试");
        }
        operationLogService.record(AuditEvent.success(AuditAction.WORKFLOW_TEMPLATE_DRAFT_SAVED.name(),
                AuditResourceType.WORKFLOW_TEMPLATE.name(), template.getId(), null, null, null,
                Map.of("versionNo", draft.getVersionNo(), "nodeCount", definition.nodes().size())));
        return toTemplateDTO(template);
    }

    @Transactional
    public WorkflowTemplateDTO publish(Long templateId) {
        WorkflowTemplateDO template = requireTemplate(templateId);
        WorkflowTemplateVersionDO draft = findLatestVersion(templateId, "DRAFT");
        if (draft == null) throw BusinessException.error("没有可发布的流程草稿");
        draft.setStatus("PUBLISHED");
        draft.setPublishedAt(LocalDateTime.now());
        if (versionMapper.updateById(draft) != 1) throw BusinessException.conflict("流程版本已被其他人修改");
        operationLogService.record(AuditEvent.success(AuditAction.WORKFLOW_TEMPLATE_PUBLISHED.name(),
                AuditResourceType.WORKFLOW_TEMPLATE.name(), templateId, null, null,
                Map.of("versionNo", draft.getVersionNo(), "status", "DRAFT"),
                Map.of("versionNo", draft.getVersionNo(), "status", "PUBLISHED")));
        return toTemplateDTO(template);
    }

    @Transactional
    public ProjectTypeDTO setDefaultTemplate(Long projectTypeId, Long versionId) {
        ProjectTypeDO type = requireActiveType(projectTypeId);
        WorkflowTemplateVersionDO version = requirePublishedVersion(versionId);
        WorkflowTemplateDO template = requireTemplate(version.getTemplateId());
        if (!projectTypeId.equals(template.getProjectTypeId())) {
            throw BusinessException.error("默认流程必须属于当前项目类型");
        }
        Long previous = type.getDefaultTemplateVersionId();
        type.setDefaultTemplateVersionId(versionId);
        if (projectTypeMapper.updateById(type) != 1) throw BusinessException.conflict("项目类型已被其他人修改");
        operationLogService.record(AuditEvent.success(AuditAction.WORKFLOW_TEMPLATE_DEFAULT_CHANGED.name(),
                AuditResourceType.PROJECT_TYPE.name(), type.getId(), null, null,
                Map.of("templateVersionId", previous == null ? "NONE" : previous),
                Map.of("templateVersionId", versionId)));
        return toProjectTypeDTO(type);
    }

    public ProjectTypeDO requireActiveType(Long typeId) {
        if (typeId == null) throw BusinessException.error("请选择项目类型");
        ProjectTypeDO type = projectTypeMapper.selectById(typeId);
        if (type == null || !Integer.valueOf(1).equals(type.getStatus())) throw BusinessException.error("项目类型不存在或已停用");
        return type;
    }

    public WorkflowTemplateBinding resolveForProjectCreation(Long projectTypeId, Long versionId) {
        ProjectTypeDO type;
        if (projectTypeId == null) {
            type = projectTypeMapper.selectOne(new LambdaQueryWrapper<ProjectTypeDO>()
                    .eq(ProjectTypeDO::getCode, "general").eq(ProjectTypeDO::getStatus, 1));
            if (type == null) throw BusinessException.error("尚未配置默认项目类型");
        } else {
            type = requireActiveType(projectTypeId);
        }
        Long selectedVersionId = versionId == null ? type.getDefaultTemplateVersionId() : versionId;
        if (selectedVersionId == null) throw BusinessException.error("该项目类型尚未配置默认流程模板");
        WorkflowTemplateVersionDO version = requirePublishedVersion(selectedVersionId);
        WorkflowTemplateDO template = requireTemplate(version.getTemplateId());
        if (!type.getId().equals(template.getProjectTypeId())) throw BusinessException.error("所选流程模板与项目类型不匹配");
        return new WorkflowTemplateBinding(type, version, template);
    }

    public WorkflowTemplateDefinition getDefinition(Long versionId) {
        if (versionId == null) return BuiltInWorkflowTemplate.compatibilityDefinition();
        WorkflowTemplateVersionDO version = versionMapper.selectById(versionId);
        if (version == null) throw BusinessException.error("流程模板版本不存在");
        return parse(version.getDefinitionJson());
    }

    public WorkflowNodeDefinition getNodeDefinition(Long versionId, String nodeKey) {
        return getDefinition(versionId).nodes().stream()
                .filter(node -> node.key().equals(nodeKey)).findFirst().orElse(null);
    }

    private ProjectTypeDTO toProjectTypeDTO(ProjectTypeDO type) {
        ProjectTypeDTO dto = new ProjectTypeDTO();
        dto.setId(type.getId());
        dto.setCode(type.getCode());
        dto.setName(type.getName());
        dto.setDescription(type.getDescription());
        dto.setStatus(type.getStatus());
        dto.setSort(type.getSort());
        dto.setDefaultTemplateVersionId(type.getDefaultTemplateVersionId());
        if (type.getDefaultTemplateVersionId() != null) {
            WorkflowTemplateVersionDO version = versionMapper.selectById(type.getDefaultTemplateVersionId());
            if (version != null) {
                WorkflowTemplateDO template = templateMapper.selectById(version.getTemplateId());
                if (template != null) {
                    dto.setDefaultTemplateId(template.getId());
                    dto.setDefaultTemplateName(template.getName());
                }
            }
        }
        return dto;
    }

    private WorkflowTemplateSummaryDTO toSummary(WorkflowTemplateDO template, ProjectTypeDO type) {
        List<WorkflowTemplateVersionDO> versions = versions(template.getId());
        WorkflowTemplateVersionDO draft = latest(versions, "DRAFT");
        WorkflowTemplateVersionDO published = latest(versions, "PUBLISHED");
        List<WorkflowTemplateVersionDO> publishedVersions = versions.stream()
                .filter(version -> "PUBLISHED".equals(version.getStatus()))
                .sorted(Comparator.comparing(WorkflowTemplateVersionDO::getVersionNo)).toList();
        WorkflowTemplateSummaryDTO dto = new WorkflowTemplateSummaryDTO();
        dto.setId(template.getId());
        dto.setCode(template.getCode());
        dto.setProjectTypeId(template.getProjectTypeId());
        dto.setName(template.getName());
        dto.setDescription(template.getDescription());
        dto.setDraftVersionNo(draft == null ? null : draft.getVersionNo());
        dto.setDraftRevision(draft == null ? null : draft.getVersion());
        dto.setPublishedVersionNo(published == null ? null : published.getVersionNo());
        dto.setPublishedVersionId(published == null ? null : published.getId());
        dto.setPublishedVersions(publishedVersions.stream().map(version -> {
            WorkflowTemplateVersionSummaryDTO summary = new WorkflowTemplateVersionSummaryDTO();
            summary.setId(version.getId());
            summary.setVersionNo(version.getVersionNo());
            return summary;
        }).toList());
        dto.setDefaultTemplateVersionId(type == null ? null : type.getDefaultTemplateVersionId());
        dto.setDefaultTemplate(type != null && publishedVersions.stream()
                .anyMatch(version -> version.getId().equals(type.getDefaultTemplateVersionId())));
        return dto;
    }

    private WorkflowTemplateDTO toTemplateDTO(WorkflowTemplateDO template) {
        List<WorkflowTemplateVersionDO> versions = versions(template.getId());
        WorkflowTemplateVersionDO draft = latest(versions, "DRAFT");
        WorkflowTemplateVersionDO published = latest(versions, "PUBLISHED");
        WorkflowTemplateVersionDO active = draft == null ? published : draft;
        WorkflowTemplateDTO dto = new WorkflowTemplateDTO();
        dto.setId(template.getId());
        dto.setCode(template.getCode());
        dto.setProjectTypeId(template.getProjectTypeId());
        dto.setName(template.getName());
        dto.setDescription(template.getDescription());
        dto.setLatestVersionNo(template.getLatestVersionNo());
        dto.setDraftVersionId(draft == null ? null : draft.getId());
        dto.setDraftVersionNo(draft == null ? null : draft.getVersionNo());
        dto.setDraftRevision(draft == null ? null : draft.getVersion());
        dto.setPublishedVersionId(published == null ? null : published.getId());
        dto.setPublishedVersionNo(published == null ? null : published.getVersionNo());
        dto.setDefinition(active == null ? null : parse(active.getDefinitionJson()));
        dto.setFixedBlocks(List.of("owner", "schedule", "task-board"));
        return dto;
    }

    private WorkflowTemplateDO requireTemplate(Long id) {
        WorkflowTemplateDO template = templateMapper.selectById(id);
        if (template == null) throw BusinessException.error("流程模板不存在");
        return template;
    }

    private WorkflowTemplateVersionDO requirePublishedVersion(Long id) {
        WorkflowTemplateVersionDO version = versionMapper.selectById(id);
        if (version == null || !"PUBLISHED".equals(version.getStatus())) throw BusinessException.error("只能选择已发布的流程版本");
        return version;
    }

    private WorkflowTemplateVersionDO findLatestVersion(Long templateId, String status) {
        return versionMapper.selectOne(new LambdaQueryWrapper<WorkflowTemplateVersionDO>()
                .eq(WorkflowTemplateVersionDO::getTemplateId, templateId)
                .eq(WorkflowTemplateVersionDO::getStatus, status)
                .orderByDesc(WorkflowTemplateVersionDO::getVersionNo)
                .last("LIMIT 1"));
    }

    private List<WorkflowTemplateVersionDO> versions(Long templateId) {
        return versionMapper.selectList(new LambdaQueryWrapper<WorkflowTemplateVersionDO>()
                .eq(WorkflowTemplateVersionDO::getTemplateId, templateId)
                .orderByDesc(WorkflowTemplateVersionDO::getVersionNo));
    }

    private WorkflowTemplateVersionDO latest(List<WorkflowTemplateVersionDO> versions, String status) {
        return versions.stream().filter(v -> status.equals(v.getStatus()))
                .max(Comparator.comparing(WorkflowTemplateVersionDO::getVersionNo)).orElse(null);
    }

    private String serialize(WorkflowTemplateDefinition definition) {
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (JsonProcessingException e) {
            throw BusinessException.error("流程模板定义无法保存");
        }
    }

    private WorkflowTemplateDefinition parse(String json) {
        try {
            return WorkflowTemplateDefinitionValidator.validate(objectMapper.readValue(json, WorkflowTemplateDefinition.class));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw BusinessException.error("流程模板定义格式损坏");
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record WorkflowTemplateBinding(ProjectTypeDO projectType, WorkflowTemplateVersionDO version,
                                          WorkflowTemplateDO template) { }
}
