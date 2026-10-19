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
import com.brad.pms.ai.connector.context.CompletionContract;
import com.brad.pms.common.ProjectScheduleRange;
import com.brad.pms.common.enums.NodeStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodeOwnerUpdateCmd;
import com.brad.pms.dto.request.NodeScheduleUpdateCmd;
import com.brad.pms.dto.response.ProjectNodeDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.service.NodeService;
import com.brad.pms.service.ProjectPermissionService;
import com.brad.pms.service.WorkflowTemplateService;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Saves one project node workbench and completes the node in one operation. */
@Component
@RequiredArgsConstructor
public class CompleteNodeWithDataCommand implements PmsCommand {

    private static final Set<String> ALLOWED_ARGUMENTS = Set.of(
            "projectId", "nodeId", "workbench", "fields", "ownerId", "startDate", "endDate");
    private static final List<String> REFRESH_SCOPES =
            List.of("project-detail", "project-dashboard", "task-board");

    private final NodeService nodeService;
    private final ProjectPermissionService permissionService;
    private final NodeWorkbenchRegistry workbenchRegistry;
    private final WorkflowTemplateService workflowTemplateService;
    private final ObjectMapper objectMapper;

    @Override
    public CommandName name() {
        return CommandName.NODE_COMPLETE_WITH_DATA;
    }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> arguments = request.arguments();
        CommandArgumentReader.rejectUnknown(arguments, ALLOWED_ARGUMENTS, name().code());
        Long projectId = CommandArgumentReader.requiredLong(arguments, "projectId");
        Long nodeId = CommandArgumentReader.requiredLong(arguments, "nodeId");
        ProjectDO project = permissionService.requireProjectManageable(projectId, "编辑节点内容");
        ProjectNodeDO node = permissionService.requireCompletableNode(projectId, nodeId);
        NodeWorkbench workbench = requireWorkbench(project, node, arguments);
        Map<String, Object> fields = requireFields(arguments);
        WorkbenchSnapshot snapshot = workbench.read(projectId, nodeId);
        requireKnownFields(workbench, snapshot, fields);

        Long ownerId = arguments.containsKey("ownerId")
                ? CommandArgumentReader.optionalLong(arguments, "ownerId") : node.getOwnerId();
        LocalDate startDate = arguments.containsKey("startDate")
                ? CommandArgumentReader.optionalDate(arguments, "startDate") : node.getStartDate();
        LocalDate endDate = arguments.containsKey("endDate")
                ? CommandArgumentReader.optionalDate(arguments, "endDate") : node.getEndDate();

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "node");
        change.put("action", "complete-with-data");
        change.put("projectId", projectId);
        change.put("nodeId", nodeId);
        change.put("nodeName", node.getName());
        change.put("fromStatus", node.getStatus());
        change.put("toStatus", NodeStatus.COMPLETED.getCode());
        change.put("workbench", workbench.key());
        change.put("workbenchLabel", workbench.label());
        CompletionContract.addTo(change, CompletionContract.projectMode(workbench.key()),
                CompletionContract.projectBlockReason(workbench.key()));
        change.put("fields", describeChanges(snapshot, fields));
        change.put("workbenchVersion", snapshot.version());
        change.put("fieldVersions", expectedFieldVersions(snapshot, fields));
        change.put("fromOwnerId", node.getOwnerId());
        change.put("toOwnerId", ownerId);
        change.put("fromStartDate", node.getStartDate());
        change.put("fromEndDate", node.getEndDate());
        change.put("toStartDate", startDate);
        change.put("toEndDate", endDate);
        change.put("allowedMutations", CompletionContract.allowedMutations());
        change.put("projectVersion", project.getVersion());
        change.put("nodeVersion", node.getVersion());

        List<CommandPreview.Precondition> preconditions = new ArrayList<>();
        if (ownerId == null) {
            preconditions.add(new CommandPreview.Precondition("NODE_OWNER_REQUIRED", "请提供节点负责人", true,
                    "node.owner.update", Map.of("projectId", projectId, "nodeId", nodeId)));
        }
        if (startDate == null || endDate == null) {
            preconditions.add(new CommandPreview.Precondition("NODE_SCHEDULE_REQUIRED", "请提供完整节点排期", true,
                    "node.schedule.update", Map.of("projectId", projectId, "nodeId", nodeId)));
        } else if (!ProjectScheduleRange.isOrdered(startDate, endDate)) {
            preconditions.add(new CommandPreview.Precondition("NODE_SCHEDULE_INVALID", "节点排期开始日期不能晚于结束日期", true,
                    "node.schedule.update", Map.of("projectId", projectId, "nodeId", nodeId)));
        }
        return new CommandPreview(null, name(), Instant.now().plusSeconds(600), request.contextVersion(),
                List.of("节点字段、负责人、排期和流程推进会在同一事务中提交"),
                List.of(change), REFRESH_SCOPES, preconditions);
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = readArguments(operation.getArgumentsJson());
        CommandArgumentReader.rejectUnknown(arguments, ALLOWED_ARGUMENTS, name().code());
        Long projectId = CommandArgumentReader.requiredLong(arguments, "projectId");
        Long nodeId = CommandArgumentReader.requiredLong(arguments, "nodeId");
        ProjectDO project = permissionService.requireProjectManageable(projectId, "编辑节点内容");
        ProjectNodeDO node = permissionService.requireNode(projectId, nodeId);
        NodeWorkbench workbench = requireWorkbench(project, node, arguments);
        Map<String, Object> fields = requireFields(arguments);
        WorkbenchSnapshot snapshot = workbench.read(projectId, nodeId);
        requireKnownFields(workbench, snapshot, fields);
        assertFresh(operation, project.getVersion(), node.getVersion(), snapshot, fields);
        permissionService.requireCompletableNode(projectId, nodeId);

        if (arguments.containsKey("ownerId")) {
            NodeOwnerUpdateCmd ownerUpdate = new NodeOwnerUpdateCmd();
            ownerUpdate.setVersion(node.getVersion());
            ownerUpdate.setOwnerId(CommandArgumentReader.optionalLong(arguments, "ownerId"));
            nodeService.updateOwner(projectId, nodeId, ownerUpdate);
            node = permissionService.requireNode(projectId, nodeId);
        }
        if (arguments.containsKey("startDate") || arguments.containsKey("endDate")) {
            NodeScheduleUpdateCmd scheduleUpdate = new NodeScheduleUpdateCmd();
            scheduleUpdate.setVersion(node.getVersion());
            scheduleUpdate.setStartDate(arguments.containsKey("startDate")
                    ? CommandArgumentReader.optionalDate(arguments, "startDate") : node.getStartDate());
            scheduleUpdate.setEndDate(arguments.containsKey("endDate")
                    ? CommandArgumentReader.optionalDate(arguments, "endDate") : node.getEndDate());
            nodeService.updateSchedule(projectId, nodeId, scheduleUpdate);
        }

        Object document = workbench.write(projectId, nodeId, fields);
        workbench.confirmForCompletion(projectId, nodeId);
        List<ProjectNodeDTO> nodes = nodeService.complete(projectId, nodeId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectId", projectId);
        data.put("nodeId", nodeId);
        data.put("workbench", workbench.key());
        data.put("document", document);
        data.put("nodes", nodes);
        data.put("allowedMutations", CompletionContract.allowedMutations());
        return new CommandResult(operation.getId(), "SUCCEEDED", "节点详情已保存并完成", data, REFRESH_SCOPES);
    }

    private NodeWorkbench requireWorkbench(ProjectDO project, ProjectNodeDO node, Map<String, Object> arguments) {
        NodeWorkbench workbench = workbenchRegistry.require(CommandArgumentReader.requiredText(arguments, "workbench"));
        WorkflowNodeDefinition definition = workflowTemplateService.getNodeDefinition(
                project.getWorkflowTemplateVersionId(), node.getNodeKey());
        if (definition == null) {
            throw BusinessException.error("节点没有可配置的字段定义");
        }
        if (workbench.dynamicFields()) {
            if (definition.fields() == null || definition.fields().isEmpty()) {
                throw BusinessException.error("节点「" + node.getName() + "」没有配置自定义字段");
            }
            return workbench;
        }
        if (!definition.runtimeComponents().contains(workbench.key())) {
            throw BusinessException.error("节点「" + node.getName() + "」没有" + workbench.label() + "工作台");
        }
        return workbench;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireFields(Map<String, Object> arguments) {
        Object raw = arguments.get("fields");
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            throw BusinessException.error("fields 必须包含至少一个要保存的字段");
        }
        return (Map<String, Object>) map;
    }

    private void requireKnownFields(NodeWorkbench workbench, WorkbenchSnapshot snapshot,
                                    Map<String, Object> fields) {
        if (workbench.dynamicFields()) return;
        for (String field : fields.keySet()) {
            if (!snapshot.labels().containsKey(field)) {
                throw BusinessException.error(workbench.label() + "不支持字段: " + field
                        + "；可用字段：" + String.join("、", snapshot.labels().keySet()));
            }
        }
    }

    private List<Map<String, Object>> describeChanges(WorkbenchSnapshot snapshot, Map<String, Object> fields) {
        return fields.entrySet().stream().map(entry -> {
            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("field", entry.getKey());
            diff.put("label", snapshot.labels().getOrDefault(entry.getKey(), entry.getKey()));
            diff.put("from", snapshot.values().get(entry.getKey()));
            diff.put("to", entry.getValue());
            return diff;
        }).toList();
    }

    private Map<String, Object> expectedFieldVersions(WorkbenchSnapshot snapshot, Map<String, Object> fields) {
        Map<String, Object> versions = new LinkedHashMap<>();
        fields.keySet().forEach(field -> {
            if (snapshot.fieldVersions().containsKey(field)) versions.put(field, snapshot.fieldVersions().get(field));
        });
        return versions;
    }

    @SuppressWarnings("unchecked")
    private void assertFresh(AiOperationDO operation, Integer projectVersion, Integer nodeVersion,
                             WorkbenchSnapshot snapshot, Map<String, Object> fields) {
        List<Map<String, Object>> changes = expectedChanges(operation);
        if (changes.isEmpty()) throw BusinessException.error("节点完成预览版本信息无效");
        Map<String, Object> expected = changes.get(0);
        PmsCommandVersionGuard.requireMatch("项目", expected.get("projectVersion"), projectVersion);
        PmsCommandVersionGuard.requireMatch("节点", expected.get("nodeVersion"), nodeVersion);
        if (expected.get("workbenchVersion") != null) {
            PmsCommandVersionGuard.requireMatch("节点工作台", expected.get("workbenchVersion"), snapshot.version());
        }
        Object rawVersions = expected.get("fieldVersions");
        if (rawVersions instanceof Map<?, ?> versions) {
            ((Map<String, Object>) versions).forEach((field, version) -> {
                if (!fields.containsKey(field)) return;
                if (!Objects.equals(version, snapshot.fieldVersions().get(field))) {
                    throw BusinessException.conflict("字段「" + field + "」已被其他人修改，请重新生成预览");
                }
            });
        }
    }

    private List<Map<String, Object>> expectedChanges(AiOperationDO operation) {
        try {
            return objectMapper.readValue(operation.getExpectedVersionsJson(), new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw BusinessException.error("节点完成预览版本信息无效");
        }
    }

    private Map<String, Object> readArguments(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw BusinessException.error("节点完成参数无效");
        }
    }
}
