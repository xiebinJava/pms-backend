package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.ProjectTypeSaveCmd;
import com.brad.pms.dto.request.WorkflowTemplateSaveCmd;
import com.brad.pms.dto.response.ProjectTypeDTO;
import com.brad.pms.dto.response.WorkflowProjectNodeOptionDTO;
import com.brad.pms.dto.response.WorkflowTemplateDTO;
import com.brad.pms.dto.response.WorkflowTemplateOptionsDTO;
import com.brad.pms.dto.response.WorkflowTemplateSummaryDTO;
import com.brad.pms.dto.response.WorkflowTemplateVersionSummaryDTO;
import com.brad.pms.dto.response.DevelopmentWorkflowTemplateOptionsDTO;
import com.brad.pms.entity.ProjectTypeDO;
import com.brad.pms.entity.WorkflowTemplateDO;
import com.brad.pms.entity.WorkflowTemplateVersionDO;
import com.brad.pms.mapper.ProjectMapper;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkflowTemplateService {
    private static final String LEGACY_TOPIC_SOURCE_PROJECT_NODE_KEY = "develop";

    private final ProjectTypeMapper projectTypeMapper;
    private final ProjectMapper projectMapper;
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

    private List<ProjectTypeDTO> listProjectCreationTypes() {
        return projectTypeMapper.selectList(new LambdaQueryWrapper<ProjectTypeDO>()
                        .eq(ProjectTypeDO::getStatus, 1)
                        .ne(ProjectTypeDO::getProjectCreationEnabled, false)
                        .orderByAsc(ProjectTypeDO::getSort)
                        .orderByAsc(ProjectTypeDO::getId))
                .stream().map(this::toProjectTypeDTO).toList();
    }

    @Transactional
    public ProjectTypeDTO createProjectType(ProjectTypeSaveCmd cmd) {
        ProjectTypeDO type = new ProjectTypeDO();
        type.setCode("process-type-pending-" + UUID.randomUUID().toString().replace("-", ""));
        type.setName(cmd.getName().trim());
        type.setDescription(trimToNull(cmd.getDescription()));
        type.setSort(cmd.getSort() == null ? 0 : cmd.getSort());
        type.setStatus(1);
        type.setProjectCreationEnabled(false);
        type.setDeleted(false);
        try {
            projectTypeMapper.insert(type);
            if (type.getId() == null) throw BusinessException.error("流程类型创建失败，请重试");
            type.setCode("process-type-" + type.getId());
            if (projectTypeMapper.updateById(type) != 1) {
                throw BusinessException.error("流程类型创建失败，请重试");
            }
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.conflict("流程类型创建失败，请重试");
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
        List<ProjectTypeDTO> projectTypes = listProjectCreationTypes();
        var projectTypeIds = projectTypes.stream().map(ProjectTypeDTO::getId).collect(Collectors.toSet());
        options.setProjectTypes(projectTypes);
        options.setTemplates(listTemplates(null, true).stream()
                .filter(template -> projectTypeIds.contains(template.getProjectTypeId()))
                .toList());
        return options;
    }

    public DevelopmentWorkflowTemplateOptionsDTO developmentOptions() {
        DevelopmentWorkflowTemplateOptionsDTO options = new DevelopmentWorkflowTemplateOptionsDTO();
        options.setTopicTemplates(listTemplatesForProcessType("topic-management"));
        options.setStoryTemplates(listTemplatesForProcessType("story-management"));
        options.setRequirementTemplates(listTemplatesForProcessType("requirement-management"));
        return options;
    }

    private List<WorkflowTemplateSummaryDTO> listTemplatesForProcessType(String processTypeCode) {
        ProjectTypeDO type = projectTypeMapper.selectOne(new LambdaQueryWrapper<ProjectTypeDO>()
                .eq(ProjectTypeDO::getCode, processTypeCode)
                .eq(ProjectTypeDO::getStatus, 1));
        return type == null ? List.of() : listTemplates(type.getId(), true);
    }

    /**
     * Lists project workflow nodes that can host a topic workflow. Published versions remain selectable
     * while offered for new projects; archived versions remain selectable while active projects pin them.
     */
    public List<WorkflowProjectNodeOptionDTO> listTopicSourceNodeOptions() {
        List<ProjectTypeDO> selectableTypes = projectTypeMapper.selectList(new LambdaQueryWrapper<ProjectTypeDO>()
                .eq(ProjectTypeDO::getStatus, 1)
                .ne(ProjectTypeDO::getProjectCreationEnabled, false)
                .orderByAsc(ProjectTypeDO::getSort)
                .orderByAsc(ProjectTypeDO::getId));
        Set<Long> selectableTypeIds = selectableTypes.stream().map(ProjectTypeDO::getId).collect(Collectors.toSet());
        List<WorkflowTemplateDO> selectableTemplates = selectableTypeIds.isEmpty() ? List.of()
                : templateMapper.selectList(new LambdaQueryWrapper<WorkflowTemplateDO>()
                        .in(WorkflowTemplateDO::getProjectTypeId, selectableTypeIds)
                        .orderByAsc(WorkflowTemplateDO::getId));
        Set<Long> selectableTemplateIds = selectableTemplates.stream().map(WorkflowTemplateDO::getId)
                .collect(Collectors.toSet());

        List<WorkflowTemplateVersionDO> publishedVersions = selectableTemplateIds.isEmpty() ? List.of()
                : versionMapper.selectList(new LambdaQueryWrapper<WorkflowTemplateVersionDO>()
                        .in(WorkflowTemplateVersionDO::getTemplateId, selectableTemplateIds)
                        .eq(WorkflowTemplateVersionDO::getStatus, "PUBLISHED")
                        .orderByDesc(WorkflowTemplateVersionDO::getVersionNo)
                        .orderByDesc(WorkflowTemplateVersionDO::getId));
        List<Long> activePinnedVersionIds = projectMapper.selectActiveWorkflowTemplateVersionIds();
        List<WorkflowTemplateVersionDO> archivedPinnedVersions = activePinnedVersionIds == null
                || activePinnedVersionIds.isEmpty() ? List.of()
                : versionMapper.selectList(new LambdaQueryWrapper<WorkflowTemplateVersionDO>()
                        .in(WorkflowTemplateVersionDO::getId, activePinnedVersionIds)
                        .eq(WorkflowTemplateVersionDO::getStatus, "ARCHIVED")
                        .orderByDesc(WorkflowTemplateVersionDO::getVersionNo)
                        .orderByDesc(WorkflowTemplateVersionDO::getId));

        Map<String, WorkflowNodeDefinition> nodesByKey = new LinkedHashMap<>();
        publishedVersions.forEach(version -> addNodes(nodesByKey, parse(version.getDefinitionJson())));
        archivedPinnedVersions.forEach(version -> addNodes(nodesByKey, parse(version.getDefinitionJson())));
        // Keep current project workflow labels authoritative when keys overlap; fill remaining gaps for legacy projects.
        addNodes(nodesByKey, BuiltInWorkflowTemplate.compatibilityDefinition());

        Map<String, Long> nameCounts = nodesByKey.values().stream()
                .collect(Collectors.groupingBy(WorkflowNodeDefinition::name, Collectors.counting()));
        return nodesByKey.values().stream()
                .sorted(Comparator.comparing(WorkflowNodeDefinition::name)
                        .thenComparing(WorkflowNodeDefinition::key))
                .map(node -> new WorkflowProjectNodeOptionDTO(node.key(),
                        nameCounts.getOrDefault(node.name(), 0L) > 1
                                ? node.name() + " (" + node.key() + ")" : node.name()))
                .toList();
    }

    private void addNodes(Map<String, WorkflowNodeDefinition> nodesByKey, WorkflowTemplateDefinition definition) {
        for (WorkflowNodeDefinition node : definition.nodes()) {
            // Stable keys are the identity. Keep the first definition for any repeated key.
            nodesByKey.putIfAbsent(node.key(), node);
        }
    }

    public WorkflowTemplateDTO getTemplate(Long templateId) {
        return toTemplateDTO(requireTemplate(templateId));
    }

    public WorkflowTemplateSummaryDTO getTemplateSummary(Long templateId) {
        WorkflowTemplateDO template = requireTemplate(templateId);
        return toSummary(template, projectTypeMapper.selectById(template.getProjectTypeId()));
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
        ProjectTypeDO processType;
        if (templateId == null) {
            processType = requireActiveType(cmd.getProjectTypeId());
            definition = validateDefinitionForProcessType(processType.getCode(), definition);
            validateTopicSourceProjectNodeKey(processType, definition);
            template = new WorkflowTemplateDO();
            template.setCode("wf-" + UUID.randomUUID().toString().replace("-", ""));
            template.setProjectTypeId(processType.getId());
            template.setName(cmd.getName().trim());
            template.setDescription(trimToNull(cmd.getDescription()));
            template.setLatestVersionNo(0);
            template.setDeleted(false);
            template.setCreatedBy(UserContext.userIdOrNull());
            if (templateMapper.insert(template) != 1) {
                throw BusinessException.conflict("流程模板未能创建，请重试");
            }
        } else {
            template = requireTemplateForUpdate(templateId);
            if (cmd.getProjectTypeId() != null && !cmd.getProjectTypeId().equals(template.getProjectTypeId())) {
                throw BusinessException.error("流程模板所属项目类型不可更改");
            }
            processType = projectTypeMapper.selectById(template.getProjectTypeId());
            definition = validateDefinitionForProcessType(
                    processType == null ? null : processType.getCode(), definition);
            validateTopicSourceProjectNodeKey(processType, definition);
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

    private void validateTopicSourceProjectNodeKey(ProjectTypeDO type, WorkflowTemplateDefinition definition) {
        validateSourceNodeKeys(type, definition);
    }

    private WorkflowTemplateDefinition validateDefinitionForProcessType(
            String processTypeCode, WorkflowTemplateDefinition definition) {
        try {
            return WorkflowTemplateDefinitionValidator.validateForProcessType(processTypeCode, definition);
        } catch (IllegalArgumentException e) {
            throw BusinessException.error(e.getMessage());
        }
    }

    private void validateSourceNodeKeys(ProjectTypeDO type, WorkflowTemplateDefinition definition) {
        if (type == null || definition == null) return;
        String projectNodeKey = trimToNull(definition.sourceProjectNodeKey());
        String topicNodeKey = trimToNull(definition.sourceTopicNodeKey());
        if ("topic-management".equals(type.getCode())) {
            if (topicNodeKey != null) throw BusinessException.error("专题流程不能配置专题节点挂载点");
            if (projectNodeKey == null) throw BusinessException.error("专题流程必须配置项目节点挂载点");
            boolean selectable = listTopicSourceNodeOptions().stream()
                    .anyMatch(option -> projectNodeKey.equals(option.key()));
            if (!selectable) throw BusinessException.error("专题流程绑定的项目节点不存在或不可用");
            return;
        }
        if ("story-management".equals(type.getCode())) {
            if (projectNodeKey != null) throw BusinessException.error("故事流程不能配置项目节点挂载点");
            if (topicNodeKey == null) throw BusinessException.error("故事流程必须配置专题节点挂载点");
            boolean selectable = listStorySourceTopicNodeOptions().stream()
                    .anyMatch(option -> topicNodeKey.equals(option.key()));
            if (!selectable) throw BusinessException.error("故事流程绑定的专题节点不存在或不可用");
            return;
        }
        if (projectNodeKey != null || topicNodeKey != null) {
            throw BusinessException.error("当前流程类型不支持事项挂载点");
        }
    }

    @Transactional
    public WorkflowTemplateDTO publish(Long templateId) {
        WorkflowTemplateDO template = requireTemplateForUpdate(templateId);
        WorkflowTemplateVersionDO draft = findLatestVersion(templateId, "DRAFT");
        if (draft == null) throw BusinessException.error("没有可发布的流程草稿");
        WorkflowTemplateDefinition definition;
        try {
            definition = WorkflowTemplateDefinitionValidator.validate(parse(draft.getDefinitionJson()));
        } catch (IllegalArgumentException e) {
            throw BusinessException.error(e.getMessage());
        }
        ProjectTypeDO processType = projectTypeMapper.selectById(template.getProjectTypeId());
        definition = validateDefinitionForProcessType(
                processType == null ? null : processType.getCode(), definition);
        validateSourceNodeKeys(processType, definition);
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
        WorkflowTemplateVersionDO versionReference = versionMapper.selectById(versionId);
        if (versionReference == null) throw BusinessException.error("只能选择已发布的流程版本");
        WorkflowTemplateDO template = requireTemplateForUpdate(versionReference.getTemplateId());
        WorkflowTemplateVersionDO version = versionMapper.selectByIdForUpdate(versionId);
        if (version == null || !"PUBLISHED".equals(version.getStatus())) {
            throw BusinessException.error("只能选择已发布的流程版本");
        }
        if (!template.getId().equals(version.getTemplateId())) throw BusinessException.error("流程模板版本与模板不匹配");
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

    @Transactional
    public void archiveVersion(Long templateId, Long versionId) {
        WorkflowTemplateDO template = requireTemplateForUpdate(templateId);
        WorkflowTemplateVersionDO version = versionMapper.selectByIdForUpdate(versionId);
        if (version == null) throw BusinessException.notFound("流程模板版本不存在");
        if (!templateId.equals(version.getTemplateId())) throw BusinessException.error("该版本不属于当前流程模板");
        if (!"PUBLISHED".equals(version.getStatus())) throw BusinessException.error("只能归档已发布的流程版本");
        if (projectTypeMapper.selectCount(new LambdaQueryWrapper<ProjectTypeDO>()
                .eq(ProjectTypeDO::getDefaultTemplateVersionId, versionId)) > 0) {
            throw BusinessException.conflict("该版本仍是项目类型的默认版本，请先更改默认版本");
        }
        version.setStatus("ARCHIVED");
        if (versionMapper.updateById(version) != 1) throw BusinessException.conflict("流程版本已被其他人修改，请刷新后重试");
        operationLogService.record(AuditEvent.success(AuditAction.WORKFLOW_TEMPLATE_VERSION_ARCHIVED.name(),
                AuditResourceType.WORKFLOW_TEMPLATE.name(), template.getId(), null, null,
                Map.of("versionNo", version.getVersionNo(), "status", "PUBLISHED"),
                Map.of("versionNo", version.getVersionNo(), "status", "ARCHIVED")));
    }

    @Transactional
    public void archiveTemplate(Long templateId) {
        WorkflowTemplateDO template = requireTemplateForUpdate(templateId);
        if ("current-process".equals(template.getCode())) throw BusinessException.error("内置模板不能归档");
        List<Long> versionIds = versions(templateId).stream().map(WorkflowTemplateVersionDO::getId).toList();
        if (!versionIds.isEmpty() && projectTypeMapper.selectCount(new LambdaQueryWrapper<ProjectTypeDO>()
                .in(ProjectTypeDO::getDefaultTemplateVersionId, versionIds)) > 0) {
            throw BusinessException.conflict("该模板包含项目类型的默认版本，请先更改默认版本");
        }
        if (templateMapper.deleteById(templateId) != 1) throw BusinessException.conflict("流程模板已被其他人归档，请刷新后重试");
        operationLogService.record(AuditEvent.success(AuditAction.WORKFLOW_TEMPLATE_ARCHIVED.name(),
                AuditResourceType.WORKFLOW_TEMPLATE.name(), templateId, null, null,
                Map.of("name", template.getName(), "deleted", false),
                Map.of("name", template.getName(), "deleted", true, "versionCount", versionIds.size())));
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
        requireProjectCreationType(type);
        Long selectedVersionId = versionId == null ? type.getDefaultTemplateVersionId() : versionId;
        if (selectedVersionId == null) throw BusinessException.error("该项目类型尚未配置默认流程模板");
        WorkflowTemplateVersionDO version = requirePublishedVersion(selectedVersionId);
        WorkflowTemplateDO template = requireTemplate(version.getTemplateId());
        if (!type.getId().equals(template.getProjectTypeId())) throw BusinessException.error("所选流程模板与项目类型不匹配");
        return new WorkflowTemplateBinding(type, version, template);
    }

    /**
     * Returns the configured published default for a non-project workflow type.
     * A missing active process type or an unset default means the workflow is not
     * configured yet; callers may still create the development item without a flow.
     */
    public WorkflowTemplateBinding resolveDefaultForProcessType(String processTypeCode) {
        return resolveForProcessType(processTypeCode, null);
    }

    public WorkflowTemplateBinding resolveForProcessType(String processTypeCode, Long requestedVersionId) {
        ProjectTypeDO type = projectTypeMapper.selectOne(new LambdaQueryWrapper<ProjectTypeDO>()
                .eq(ProjectTypeDO::getCode, processTypeCode)
                .eq(ProjectTypeDO::getStatus, 1));
        if (type == null) return null;
        Long selectedVersionId = requestedVersionId == null
                ? type.getDefaultTemplateVersionId() : requestedVersionId;
        if (selectedVersionId == null) return null;
        WorkflowTemplateVersionDO version = requirePublishedVersion(selectedVersionId);
        WorkflowTemplateDO template = requireTemplate(version.getTemplateId());
        if (!type.getId().equals(template.getProjectTypeId())) {
            throw BusinessException.error("所选流程模板与流程类型不匹配");
        }
        return new WorkflowTemplateBinding(type, version, template);
    }

    /** Resolves the configured topic host node, retaining the compatibility node for legacy templates. */
    public String resolveTopicSourceProjectNodeKey() {
        WorkflowTemplateBinding binding = resolveDefaultForProcessType("topic-management");
        return binding == null
                ? LEGACY_TOPIC_SOURCE_PROJECT_NODE_KEY
                : resolveTopicSourceProjectNodeKeyForRuntime(binding.version().getId());
    }

    public String resolveTopicSourceProjectNodeKey(Long templateVersionId) {
        if (templateVersionId == null) return null;
        return trimToNull(getDefinition(templateVersionId).sourceProjectNodeKey());
    }

    /**
     * Resolves a topic host node for runtime creation. Published templates created
     * before the mount-key field existed retain the original compatibility node.
     */
    public String resolveTopicSourceProjectNodeKeyForRuntime(Long templateVersionId) {
        String configuredKey = resolveTopicSourceProjectNodeKey(templateVersionId);
        return configuredKey == null ? LEGACY_TOPIC_SOURCE_PROJECT_NODE_KEY : configuredKey;
    }

    /**
     * Resolves the topic host from an already loaded runtime definition.
     * Batch readers use this overload to avoid reloading the pinned version.
     */
    public String resolveTopicSourceProjectNodeKeyForRuntime(WorkflowTemplateDefinition definition) {
        String configuredKey = definition == null ? null : trimToNull(definition.sourceProjectNodeKey());
        return configuredKey == null ? LEGACY_TOPIC_SOURCE_PROJECT_NODE_KEY : configuredKey;
    }

    public String resolveStorySourceTopicNodeKey(Long templateVersionId) {
        if (templateVersionId == null) return null;
        return trimToNull(getDefinition(templateVersionId).sourceTopicNodeKey());
    }

    /** Lists stable node keys from all published Topic Management templates for Story Management binding. */
    public List<WorkflowProjectNodeOptionDTO> listStorySourceTopicNodeOptions() {
        List<ProjectTypeDO> topicTypes = projectTypeMapper.selectList(new LambdaQueryWrapper<ProjectTypeDO>()
                .eq(ProjectTypeDO::getCode, "topic-management")
                .eq(ProjectTypeDO::getStatus, 1)
                .eq(ProjectTypeDO::getDeleted, false));
        Set<Long> typeIds = topicTypes.stream().map(ProjectTypeDO::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (typeIds.isEmpty()) return List.of();
        List<WorkflowTemplateDO> templates = templateMapper.selectList(new LambdaQueryWrapper<WorkflowTemplateDO>()
                .in(WorkflowTemplateDO::getProjectTypeId, typeIds)
                .eq(WorkflowTemplateDO::getDeleted, false));
        Set<Long> templateIds = templates.stream().map(WorkflowTemplateDO::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (templateIds.isEmpty()) return List.of();
        List<WorkflowTemplateVersionDO> versions = versionMapper.selectList(new LambdaQueryWrapper<WorkflowTemplateVersionDO>()
                .in(WorkflowTemplateVersionDO::getTemplateId, templateIds)
                .eq(WorkflowTemplateVersionDO::getStatus, "PUBLISHED"));
        Map<String, WorkflowNodeDefinition> nodesByKey = new LinkedHashMap<>();
        versions.forEach(version -> addNodes(nodesByKey, parse(version.getDefinitionJson())));
        return nodesByKey.values().stream()
                .sorted(Comparator.comparing(WorkflowNodeDefinition::name).thenComparing(WorkflowNodeDefinition::key))
                .map(node -> new WorkflowProjectNodeOptionDTO(node.key(), node.name()))
                .toList();
    }

    private void requireProjectCreationType(ProjectTypeDO type) {
        if (Boolean.FALSE.equals(type.getProjectCreationEnabled())) {
            throw BusinessException.error("该流程类型不可用于新建项目");
        }
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

    /** Batch equivalent of getDefinition, retaining legacy null binding and validation semantics. */
    public Map<Long, WorkflowTemplateDefinition> getDefinitions(Collection<Long> versionIds) {
        Map<Long, WorkflowTemplateDefinition> result = new HashMap<>();
        var ids = new HashSet<>(versionIds);
        if (ids.remove(null)) result.put(null, BuiltInWorkflowTemplate.compatibilityDefinition());
        if (!ids.isEmpty()) {
            for (WorkflowTemplateVersionDO version : versionMapper.selectList(new LambdaQueryWrapper<WorkflowTemplateVersionDO>()
                    .in(WorkflowTemplateVersionDO::getId, ids))) {
                result.put(version.getId(), parse(version.getDefinitionJson()));
            }
            if (!result.keySet().containsAll(ids)) throw BusinessException.error("流程模板版本不存在");
        }
        return result;
    }

    private ProjectTypeDTO toProjectTypeDTO(ProjectTypeDO type) {
        ProjectTypeDTO dto = new ProjectTypeDTO();
        dto.setId(type.getId());
        dto.setCode(type.getCode());
        dto.setName(type.getName());
        dto.setDescription(type.getDescription());
        dto.setStatus(type.getStatus());
        dto.setProjectCreationEnabled(type.getProjectCreationEnabled());
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
        dto.setVersions(versions.stream()
                .sorted(Comparator.comparing(WorkflowTemplateVersionDO::getVersionNo))
                .map(version -> {
                    WorkflowTemplateVersionSummaryDTO summary = new WorkflowTemplateVersionSummaryDTO();
                    summary.setId(version.getId());
                    summary.setVersionNo(version.getVersionNo());
                    summary.setStatus(version.getStatus());
                    summary.setIsDefault(type != null && version.getId().equals(type.getDefaultTemplateVersionId()));
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

    private WorkflowTemplateDO requireTemplateForUpdate(Long id) {
        WorkflowTemplateDO template = templateMapper.selectActiveByIdForUpdate(id);
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
