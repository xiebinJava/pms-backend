package com.brad.pms.ai.command.node;

import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.ai.command.PmsCommandVersionGuard;
import com.brad.pms.ai.node.NodeWorkbench;
import com.brad.pms.ai.node.NodeWorkbenchRegistry;
import com.brad.pms.ai.node.WorkbenchSnapshot;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.service.ProjectPermissionService;
import com.brad.pms.service.WorkflowTemplateService;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Writes any node workbench field through one command. Field semantics live in
 * the workbench service (validation, permissions, version checks); this command
 * only merges the caller's patch onto the current document and reports the diff.
 */
@Component
@RequiredArgsConstructor
public class NodeFieldUpdateCommand implements PmsCommand {

    private static final Set<String> ALLOWED_ARGUMENTS =
            Set.of("projectId", "nodeId", "workbench", "fields");

    private static final List<String> REFRESH_SCOPES =
            List.of("project-detail", "project-dashboard", "task-board");

    private final NodeWorkbenchRegistry workbenchRegistry;
    private final ProjectPermissionService permissionService;
    private final WorkflowTemplateService workflowTemplateService;
    private final ObjectMapper objectMapper;

    @Override
    public CommandName name() {
        return CommandName.NODE_FIELD_UPDATE;
    }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> arguments = request.arguments();
        CommandArgumentReader.rejectUnknown(arguments, ALLOWED_ARGUMENTS, "node.field.update");
        Long projectId = CommandArgumentReader.requiredLong(arguments, "projectId");
        Long nodeId = CommandArgumentReader.requiredLong(arguments, "nodeId");
        NodeWorkbench workbench = workbenchRegistry.require(CommandArgumentReader.requiredText(arguments, "workbench"));
        Map<String, Object> patch = requirePatch(arguments);
        ProjectDO project = permissionService.requireProjectManageable(projectId, "编辑节点内容");
        requireWorkbenchOnNode(project, workbench, permissionService.requireNode(projectId, nodeId));

        WorkbenchSnapshot snapshot = workbench.read(projectId, nodeId);
        requireKnownFields(workbench, snapshot, patch);

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "node-workbench");
        change.put("action", "update");
        change.put("projectId", projectId);
        change.put("nodeId", nodeId);
        change.put("workbench", workbench.key());
        change.put("workbenchLabel", workbench.label());
        change.put("fields", describeChanges(snapshot, patch));
        change.put("workbenchVersion", snapshot.version());
        change.put("fieldVersions", expectedFieldVersions(snapshot, patch));
        change.put("projectVersion", project.getVersion());
        return new CommandPreview(null, name(), Instant.now().plusSeconds(600), request.contextVersion(),
                List.of("只修改本次提供的字段，未提供的字段保留原值",
                        "字段清单：" + String.join("、", snapshot.labels().values())),
                List.of(change), REFRESH_SCOPES);
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = readArguments(operation.getArgumentsJson());
        CommandArgumentReader.rejectUnknown(arguments, ALLOWED_ARGUMENTS, "node.field.update");
        Long projectId = CommandArgumentReader.requiredLong(arguments, "projectId");
        Long nodeId = CommandArgumentReader.requiredLong(arguments, "nodeId");
        NodeWorkbench workbench = workbenchRegistry.require(CommandArgumentReader.requiredText(arguments, "workbench"));
        Map<String, Object> patch = requirePatch(arguments);
        ProjectDO project = permissionService.requireProjectManageable(projectId, "编辑节点内容");
        requireWorkbenchOnNode(project, workbench, permissionService.requireNode(projectId, nodeId));

        WorkbenchSnapshot current = workbench.read(projectId, nodeId);
        requireKnownFields(workbench, current, patch);
        assertFresh(operation, current, patch, project.getVersion());

        Object updated = workbench.write(projectId, nodeId, patch);
        return new CommandResult(operation.getId(), "SUCCEEDED",
                workbench.label() + "已更新",
                Map.of("projectId", projectId, "nodeId", nodeId,
                        "workbench", workbench.key(), "document", updated),
                REFRESH_SCOPES);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requirePatch(Map<String, Object> arguments) {
        Object raw = arguments.get("fields");
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            throw BusinessException.error("fields 必须包含至少一个要修改的字段");
        }
        return (Map<String, Object>) map;
    }

    private void requireKnownFields(NodeWorkbench workbench, WorkbenchSnapshot snapshot, Map<String, Object> patch) {
        if (workbench.dynamicFields()) return;
        for (String field : patch.keySet()) {
            if (!snapshot.labels().containsKey(field)) {
                throw BusinessException.error(workbench.label() + "不支持字段: " + field
                        + "；可用字段：" + String.join("、", snapshot.labels().keySet()));
            }
        }
    }

    /**
     * A workbench is only writable on a node that actually runs it, so the Agent
     * cannot write release fields on the kickoff node just because the command
     * exists.
     */
    private void requireWorkbenchOnNode(ProjectDO project, NodeWorkbench workbench, ProjectNodeDO node) {
        WorkflowNodeDefinition definition = workflowTemplateService.getNodeDefinition(
                project.getWorkflowTemplateVersionId(), node.getNodeKey());
        if (definition == null) throw BusinessException.error("节点没有可配置的字段定义");
        if (workbench.dynamicFields()) {
            if (definition.fields().isEmpty()) {
                throw BusinessException.error("节点「" + node.getName() + "」没有配置自定义字段");
            }
            return;
        }
        List<String> components = definition.runtimeComponents();
        if (!components.contains(workbench.key())) {
            throw BusinessException.error("节点「" + node.getName() + "」没有" + workbench.label()
                    + "工作台；该节点可写工作台：" + (components.isEmpty() ? "无" : String.join("、", components)));
        }
    }

    private List<Map<String, Object>> describeChanges(WorkbenchSnapshot snapshot, Map<String, Object> patch) {
        return patch.entrySet().stream().map(entry -> {
            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("field", entry.getKey());
            diff.put("label", snapshot.labels().getOrDefault(entry.getKey(), entry.getKey()));
            diff.put("from", snapshot.values().get(entry.getKey()));
            diff.put("to", entry.getValue());
            return diff;
        }).toList();
    }

    private Map<String, Object> expectedFieldVersions(WorkbenchSnapshot snapshot, Map<String, Object> patch) {
        Map<String, Object> versions = new LinkedHashMap<>();
        patch.keySet().forEach(field -> {
            if (snapshot.fieldVersions().containsKey(field)) {
                versions.put(field, snapshot.fieldVersions().get(field));
            }
        });
        return versions;
    }

    @SuppressWarnings("unchecked")
    private void assertFresh(AiOperationDO operation,
                             WorkbenchSnapshot current,
                             Map<String, Object> patch,
                             Integer projectVersion) {
        List<Map<String, Object>> changes = expectedChanges(operation);
        if (changes.isEmpty()) {
            throw BusinessException.error("节点字段更新预览版本信息无效");
        }
        Map<String, Object> expected = changes.get(0);
        PmsCommandVersionGuard.requireMatch("项目", expected.get("projectVersion"), projectVersion);
        if (expected.get("workbenchVersion") != null) {
            PmsCommandVersionGuard.requireMatch("节点工作台", expected.get("workbenchVersion"), current.version());
        }
        Object rawVersions = expected.get("fieldVersions");
        if (rawVersions instanceof Map<?, ?> versions) {
            ((Map<String, Object>) versions).forEach((field, version) -> {
                if (!patch.containsKey(field)) return;
                Integer actual = current.fieldVersions().get(field);
                if (!Objects.equals(version, actual)) {
                    throw BusinessException.conflict("字段「" + field + "」已被其他人修改，请重新生成预览");
                }
            });
        }
    }

    private List<Map<String, Object>> expectedChanges(AiOperationDO operation) {
        try {
            return objectMapper.readValue(operation.getExpectedVersionsJson(), new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw BusinessException.error("节点字段更新预览版本信息无效");
        }
    }

    private Map<String, Object> readArguments(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw BusinessException.error("节点字段更新参数无效");
        }
    }
}
