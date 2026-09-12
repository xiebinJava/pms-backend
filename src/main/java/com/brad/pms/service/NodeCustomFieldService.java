package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.audit.AuditResourceType;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.WorkflowNodeFieldValuesCmd;
import com.brad.pms.dto.response.WorkflowFieldAttachmentDTO;
import com.brad.pms.dto.response.WorkflowNodeFieldValuesDTO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeFieldAttachmentDO;
import com.brad.pms.entity.ProjectNodeFieldValueDO;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectNodeFieldAttachmentMapper;
import com.brad.pms.mapper.ProjectNodeFieldValueMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.security.UserContext;
import com.brad.pms.storage.FileStorageService;
import com.brad.pms.workflow.WorkflowFieldDefinition;
import com.brad.pms.workflow.WorkflowFieldType;
import com.brad.pms.workflow.WorkflowFieldValueValidator;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NodeCustomFieldService {
    private final ProjectNodeFieldValueMapper valueMapper;
    private final ProjectNodeFieldAttachmentMapper attachmentMapper;
    private final ProjectNodeMapper nodeMapper;
    private final ProjectMapper projectMapper;
    private final ProjectPermissionService permissionService;
    private final WorkflowTemplateService workflowTemplateService;
    private final FileStorageService fileStorageService;
    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    public WorkflowNodeFieldValuesDTO get(Long projectId, Long nodeId) {
        permissionService.requireProjectReadable(projectId);
        permissionService.requireNode(projectId, nodeId);
        return getValuesForNode(projectId, nodeId);
    }

    @Transactional
    public WorkflowNodeFieldValuesDTO save(Long projectId, Long nodeId, WorkflowNodeFieldValuesCmd cmd) {
        ProjectDO project = permissionService.requireProjectReadable(projectId);
        ProjectNodeDO node = permissionService.requireManageableNode(projectId, nodeId, "编辑节点字段");
        WorkflowNodeDefinition definition = requireDefinition(project, node);
        Map<String, JsonNode> values;
        try {
            values = WorkflowFieldValueValidator.validate(definition.fields(), cmd == null ? null : cmd.getValues());
        } catch (IllegalArgumentException e) {
            throw BusinessException.error(e.getMessage());
        }
        Map<String, WorkflowFieldDefinition> fields = definition.fields().stream()
                .collect(Collectors.toMap(WorkflowFieldDefinition::key, field -> field));
        for (Map.Entry<String, JsonNode> entry : values.entrySet()) {
            WorkflowFieldDefinition field = fields.get(entry.getKey());
            JsonNode value = entry.getValue();
            if (value != null && !value.isNull() && field.type() == WorkflowFieldType.PERSON && value.isIntegralNumber()) {
                permissionService.requireProjectMember(projectId, value.asLong());
            }
            if (value != null && !value.isNull() && field.type() == WorkflowFieldType.ATTACHMENT) {
                validateAttachmentReferences(projectId, nodeId, field.key(), value);
            }
            ProjectNodeFieldValueDO existing = findValue(nodeId, field.key());
            String json = serialize(value);
            if (existing == null) {
                ProjectNodeFieldValueDO created = new ProjectNodeFieldValueDO();
                created.setProjectId(projectId);
                created.setNodeId(nodeId);
                created.setFieldKey(field.key());
                created.setValueJson(json);
                created.setCreatedBy(UserContext.userIdOrNull());
                try {
                    valueMapper.insert(created);
                } catch (DuplicateKeyException e) {
                    throw BusinessException.conflict("节点字段已被其他人修改，请刷新后重试");
                }
            } else {
                Integer expectedVersion = cmd == null || cmd.getVersions() == null ? null : cmd.getVersions().get(field.key());
                if (!Objects.equals(existing.getVersion(), expectedVersion)) {
                    throw BusinessException.conflict("节点字段已被其他人修改，请刷新后重试");
                }
                existing.setValueJson(json);
                if (valueMapper.updateById(existing) != 1) {
                    throw BusinessException.conflict("节点字段已被其他人修改，请刷新后重试");
                }
            }
        }
        if (!values.isEmpty()) {
            operationLogService.record(AuditEvent.success(AuditAction.NODE_CUSTOM_FIELDS_SAVED.name(),
                    AuditResourceType.PROJECT_NODE.name(), nodeId, projectId, null, null,
                    Map.of("fieldKeys", values.keySet())));
        }
        return getValuesForNode(projectId, nodeId);
    }

    public WorkflowNodeFieldValuesDTO getValuesForNode(Long projectId, Long nodeId) {
        List<ProjectNodeFieldValueDO> rows = valueMapper.selectList(new LambdaQueryWrapper<ProjectNodeFieldValueDO>()
                .eq(ProjectNodeFieldValueDO::getProjectId, projectId)
                .eq(ProjectNodeFieldValueDO::getNodeId, nodeId));
        Map<String, JsonNode> values = new LinkedHashMap<>();
        Map<String, Integer> versions = new LinkedHashMap<>();
        for (ProjectNodeFieldValueDO row : rows) {
            values.put(row.getFieldKey(), parseValue(row.getValueJson()));
            versions.put(row.getFieldKey(), row.getVersion());
        }
        List<ProjectNodeFieldAttachmentDO> attachments = attachmentMapper.selectList(new LambdaQueryWrapper<ProjectNodeFieldAttachmentDO>()
                .eq(ProjectNodeFieldAttachmentDO::getProjectId, projectId)
                .eq(ProjectNodeFieldAttachmentDO::getNodeId, nodeId)
                .orderByAsc(ProjectNodeFieldAttachmentDO::getId));
        Map<String, List<WorkflowFieldAttachmentDTO>> attachmentDtos = new LinkedHashMap<>();
        for (ProjectNodeFieldAttachmentDO row : attachments) {
            attachmentDtos.computeIfAbsent(row.getFieldKey(), key -> new ArrayList<>()).add(toAttachmentDTO(row));
        }
        WorkflowNodeFieldValuesDTO dto = new WorkflowNodeFieldValuesDTO();
        dto.setValues(values);
        dto.setVersions(versions);
        dto.setAttachments(attachmentDtos);
        return dto;
    }

    public void requireRequiredFields(Long projectId, Long nodeId, WorkflowNodeDefinition definition) {
        if (definition == null || definition.fields() == null || definition.fields().isEmpty()) return;
        WorkflowNodeFieldValuesDTO dto = getValuesForNode(projectId, nodeId);
        try {
            WorkflowFieldValueValidator.validate(definition.fields(), dto.getValues());
        } catch (IllegalArgumentException e) {
            throw BusinessException.error(e.getMessage());
        }
        validateAllAttachmentReferences(projectId, nodeId, definition, dto.getValues());
        List<String> missing = WorkflowFieldValueValidator.missingRequiredFields(definition.fields(), dto.getValues());
        if (!missing.isEmpty()) throw BusinessException.error("请先填写" + String.join("、", missing));
    }

    @Transactional
    public WorkflowFieldAttachmentDTO upload(Long projectId, Long nodeId, String fieldKey, MultipartFile file) {
        ProjectDO project = permissionService.requireProjectReadable(projectId);
        permissionService.requireManageableNode(projectId, nodeId, "上传节点字段附件");
        WorkflowFieldDefinition field = requireField(requireDefinition(project, requireNode(projectId, nodeId)), fieldKey);
        if (field.type() != WorkflowFieldType.ATTACHMENT) throw BusinessException.error("该节点字段不是附件类型");
        FileStorageService.StoredFile stored = fileStorageService.storeAttachment(file);
        ProjectNodeFieldAttachmentDO row = new ProjectNodeFieldAttachmentDO();
        row.setProjectId(projectId);
        row.setNodeId(nodeId);
        row.setFieldKey(fieldKey);
        row.setFileKey(stored.key());
        row.setOriginalName(sanitizeName(stored.originalFilename(), stored.key()));
        row.setContentType(stored.contentType());
        row.setSizeBytes(stored.size());
        row.setCreatedBy(UserContext.userIdOrNull());
        try {
            attachmentMapper.insert(row);
        } catch (RuntimeException e) {
            fileStorageService.delete(stored.key());
            throw e;
        }
        operationLogService.record(AuditEvent.success(AuditAction.WORKFLOW_FIELD_ATTACHMENT_UPLOADED.name(),
                AuditResourceType.PROJECT_NODE_FIELD_ATTACHMENT.name(), row.getId(), projectId, null, null,
                Map.of("nodeId", nodeId, "fieldKey", fieldKey, "originalName", row.getOriginalName(), "sizeBytes", row.getSizeBytes())));
        return toAttachmentDTO(row);
    }

    public Resource loadAttachment(Long projectId, Long nodeId, String fieldKey, Long attachmentId) {
        permissionService.requireProjectReadable(projectId);
        permissionService.requireNode(projectId, nodeId);
        return fileStorageService.load(requireAttachment(projectId, nodeId, fieldKey, attachmentId).getFileKey());
    }

    public String attachmentContentType(Long projectId, Long nodeId, String fieldKey, Long attachmentId) {
        return defaultContentType(requireAttachmentAfterRead(projectId, nodeId, fieldKey, attachmentId).getContentType());
    }

    public String attachmentName(Long projectId, Long nodeId, String fieldKey, Long attachmentId) {
        return requireAttachmentAfterRead(projectId, nodeId, fieldKey, attachmentId).getOriginalName();
    }

    @Transactional
    public void deleteAttachment(Long projectId, Long nodeId, String fieldKey, Long attachmentId) {
        ProjectDO project = permissionService.requireProjectReadable(projectId);
        permissionService.requireManageableNode(projectId, nodeId, "删除节点字段附件");
        ProjectNodeFieldAttachmentDO attachment = requireAttachment(projectId, nodeId, fieldKey, attachmentId);
        ProjectNodeFieldValueDO value = findValue(nodeId, attachment.getFieldKey());
        if (value != null && value.getValueJson() != null) {
            JsonNode ids = parseValue(value.getValueJson());
            if (ids != null && ids.isArray()) {
                List<Long> nextIds = new ArrayList<>();
                ids.forEach(id -> { if (id.asLong() != attachmentId) nextIds.add(id.asLong()); });
                value.setValueJson(serialize(objectMapper.valueToTree(nextIds)));
                if (valueMapper.updateById(value) != 1) throw BusinessException.conflict("节点字段已被其他人修改，请刷新后重试");
            }
        }
        attachmentMapper.deleteById(attachment.getId());
        fileStorageService.delete(attachment.getFileKey());
        operationLogService.record(AuditEvent.success(AuditAction.WORKFLOW_FIELD_ATTACHMENT_DELETED.name(),
                AuditResourceType.PROJECT_NODE_FIELD_ATTACHMENT.name(), attachment.getId(), projectId, null,
                Map.of("nodeId", nodeId, "fieldKey", attachment.getFieldKey(), "originalName", attachment.getOriginalName()), null));
    }

    private void validateAllAttachmentReferences(Long projectId, Long nodeId, WorkflowNodeDefinition definition,
                                                  Map<String, JsonNode> values) {
        for (WorkflowFieldDefinition field : definition.fields()) {
            JsonNode value = values.get(field.key());
            if (field.type() == WorkflowFieldType.ATTACHMENT && value != null && value.isArray()) {
                validateAttachmentReferences(projectId, nodeId, field.key(), value);
            }
        }
    }

    private void validateAttachmentReferences(Long projectId, Long nodeId, String fieldKey, JsonNode value) {
        for (JsonNode id : value) {
            ProjectNodeFieldAttachmentDO attachment = attachmentMapper.selectById(id.asLong());
            if (attachment == null || !Objects.equals(attachment.getProjectId(), projectId)
                    || !Objects.equals(attachment.getNodeId(), nodeId) || !Objects.equals(attachment.getFieldKey(), fieldKey)) {
                throw BusinessException.error("附件不存在或不属于当前节点字段");
            }
        }
    }

    private WorkflowNodeDefinition requireDefinition(ProjectDO project, ProjectNodeDO node) {
        WorkflowNodeDefinition definition = workflowTemplateService.getNodeDefinition(
                project.getWorkflowTemplateVersionId(), node.getNodeKey());
        if (definition == null) throw BusinessException.error("节点没有可配置的字段定义");
        return definition;
    }

    private ProjectNodeDO requireNode(Long projectId, Long nodeId) {
        ProjectNodeDO node = nodeMapper.selectById(nodeId);
        if (node == null || !Objects.equals(node.getProjectId(), projectId)) throw BusinessException.error("节点不存在");
        return node;
    }

    private WorkflowFieldDefinition requireField(WorkflowNodeDefinition definition, String key) {
        return definition.fields().stream().filter(field -> field.key().equals(key)).findFirst()
                .orElseThrow(() -> BusinessException.error("节点字段不存在"));
    }

    private ProjectNodeFieldValueDO findValue(Long nodeId, String fieldKey) {
        return valueMapper.selectOne(new LambdaQueryWrapper<ProjectNodeFieldValueDO>()
                .eq(ProjectNodeFieldValueDO::getNodeId, nodeId).eq(ProjectNodeFieldValueDO::getFieldKey, fieldKey));
    }

    private ProjectNodeFieldAttachmentDO requireAttachment(Long projectId, Long nodeId, String fieldKey, Long attachmentId) {
        ProjectNodeFieldAttachmentDO attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !Objects.equals(attachment.getProjectId(), projectId)
                || !Objects.equals(attachment.getNodeId(), nodeId)
                || !Objects.equals(attachment.getFieldKey(), fieldKey)) throw BusinessException.error("附件不存在");
        return attachment;
    }

    private ProjectNodeFieldAttachmentDO requireAttachmentAfterRead(Long projectId, Long nodeId, String fieldKey, Long attachmentId) {
        permissionService.requireProjectReadable(projectId);
        permissionService.requireNode(projectId, nodeId);
        return requireAttachment(projectId, nodeId, fieldKey, attachmentId);
    }

    private WorkflowFieldAttachmentDTO toAttachmentDTO(ProjectNodeFieldAttachmentDO row) {
        WorkflowFieldAttachmentDTO dto = new WorkflowFieldAttachmentDTO();
        dto.setId(row.getId());
        dto.setOriginalName(row.getOriginalName());
        dto.setContentType(row.getContentType());
        dto.setSizeBytes(row.getSizeBytes());
        dto.setUrl("/api/projects/" + row.getProjectId() + "/nodes/" + row.getNodeId() + "/fields/"
                + row.getFieldKey() + "/attachments/" + row.getId());
        dto.setCreatedBy(row.getCreatedBy());
        dto.setCreatedAt(row.getCreatedAt());
        return dto;
    }

    private JsonNode parseValue(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw BusinessException.error("节点字段数据格式损坏");
        }
    }

    private String serialize(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw BusinessException.error("节点字段值无法保存");
        }
    }

    private static String defaultContentType(String contentType) {
        return contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
    }

    private static String sanitizeName(String original, String fallback) {
        if (original == null || original.isBlank()) return fallback;
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[\\r\\n\\t]", "").trim();
        return name.isBlank() ? fallback : name;
    }
}
