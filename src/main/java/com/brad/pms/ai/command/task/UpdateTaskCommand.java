package com.brad.pms.ai.command.task;

import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.TaskUpdateCmd;
import com.brad.pms.dto.response.ProjectTaskDTO;
import com.brad.pms.dto.response.TaskDetailDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.service.ProjectPermissionService;
import com.brad.pms.service.TaskService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UpdateTaskCommand implements PmsCommand {

    private static final Set<String> ALLOWED_ARGUMENTS = Set.of(
            "taskId", "version", "title", "description", "deliverable", "status", "priority",
            "assigneeId", "requirementId", "clearRequirement", "sort", "dueDate", "clearDueDate");

    private final TaskService taskService;
    private final ProjectPermissionService permissionService;
    private final ObjectMapper objectMapper;

    @Override
    public CommandName name() {
        return CommandName.TASK_UPDATE;
    }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> arguments = request.arguments();
        rejectUnknownArguments(arguments);
        Long taskId = CommandArgumentReader.requiredLong(arguments, "taskId");
        TaskDetailDTO task = taskService.getDetail(taskId);
        ProjectDO project = permissionService.requireProjectReadable(task.getProjectId());
        ProjectNodeDO node = permissionService.requireNode(task.getProjectId(), task.getNodeId());
        if (onlyTaskIdAndVersion(arguments)) throw BusinessException.error("task.update 至少需要一个待修改字段");

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "task");
        change.put("action", "update");
        change.put("taskId", taskId);
        change.put("title", task.getTitle());
        change.put("expectedVersion", task.getVersion());
        change.put("projectVersion", project.getVersion());
        change.put("nodeVersion", node.getVersion());
        addIfPresent(change, arguments, "title");
        addIfPresent(change, arguments, "description");
        addIfPresent(change, arguments, "deliverable");
        addIfPresent(change, arguments, "status");
        addIfPresent(change, arguments, "priority");
        addIfPresent(change, arguments, "assigneeId");
        addIfPresent(change, arguments, "requirementId");
        addIfPresent(change, arguments, "clearRequirement");
        addIfPresent(change, arguments, "sort");
        addIfPresent(change, arguments, "dueDate");
        addIfPresent(change, arguments, "clearDueDate");
        return new CommandPreview(null, name(), Instant.now().plusSeconds(600), request.contextVersion(),
                List.of("执行时仍会由 PMS 按任务负责人/项目权限和状态流转规则复核"),
                List.of(change), List.of("project-detail", "task-board", "project-dashboard"));
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = readArguments(operation.getArgumentsJson());
        rejectUnknownArguments(arguments);
        Long taskId = CommandArgumentReader.requiredLong(arguments, "taskId");
        TaskDetailDTO task = taskService.getDetail(taskId);
        ProjectDO project = permissionService.requireProjectReadable(task.getProjectId());
        ProjectNodeDO node = permissionService.requireNode(task.getProjectId(), task.getNodeId());
        assertFresh(operation, task.getVersion(), project.getVersion(), node.getVersion());

        TaskUpdateCmd command = new TaskUpdateCmd();
        command.setVersion(resolveExpectedVersion(arguments, operation));
        command.setTitle(CommandArgumentReader.optionalText(arguments, "title"));
        command.setDescription(optionalNullableText(arguments, "description"));
        command.setDeliverable(optionalNullableText(arguments, "deliverable"));
        command.setStatus(CommandArgumentReader.optionalInteger(arguments, "status", null));
        command.setPriority(CommandArgumentReader.optionalInteger(arguments, "priority", null));
        command.setAssigneeId(CommandArgumentReader.optionalLong(arguments, "assigneeId"));
        command.setRequirementId(CommandArgumentReader.optionalLong(arguments, "requirementId"));
        command.setClearRequirement(booleanValue(arguments, "clearRequirement"));
        command.setSort(CommandArgumentReader.optionalInteger(arguments, "sort", null));
        command.setDueDate(CommandArgumentReader.optionalDate(arguments, "dueDate"));
        command.setClearDueDate(booleanValue(arguments, "clearDueDate"));
        ProjectTaskDTO updatedTask = taskService.update(taskId, command);
        return new CommandResult(operation.getId(), "SUCCEEDED", "任务已更新", Map.of("task", updatedTask),
                List.of("project-detail", "task-board", "project-dashboard"));
    }

    private void assertFresh(AiOperationDO operation, Integer taskVersion, Integer projectVersion, Integer nodeVersion) {
        try {
            List<Map<String, Object>> changes = objectMapper.readValue(operation.getExpectedVersionsJson(), new TypeReference<>() { });
            if (changes.isEmpty()) return;
            Map<String, Object> change = changes.get(0);
            CommandVersionGuard.requireMatch("任务", change.get("expectedVersion"), taskVersion);
            CommandVersionGuard.requireMatch("项目", change.get("projectVersion"), projectVersion);
            CommandVersionGuard.requireMatch("节点", change.get("nodeVersion"), nodeVersion);
        } catch (Exception e) {
            if (e instanceof BusinessException businessException) throw businessException;
            throw BusinessException.error("任务更新预览版本信息无效");
        }
    }

    private Integer resolveExpectedVersion(Map<String, Object> arguments, AiOperationDO operation) {
        Integer explicit = CommandArgumentReader.optionalInteger(arguments, "version", null);
        if (explicit != null) return explicit;
        try {
            List<Map<String, Object>> changes = objectMapper.readValue(operation.getExpectedVersionsJson(), new TypeReference<>() { });
            if (changes.isEmpty()) throw BusinessException.conflict("任务版本缺失，请重新生成预览");
            Object expected = changes.get(0).get("expectedVersion");
            if (expected instanceof Number number) return number.intValue();
            if (expected instanceof String value) return Integer.valueOf(value);
            throw BusinessException.conflict("任务版本缺失，请重新生成预览");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.error("任务版本信息无效");
        }
    }

    private void rejectUnknownArguments(Map<String, Object> arguments) {
        arguments.keySet().stream()
                .filter(key -> !ALLOWED_ARGUMENTS.contains(key))
                .findFirst()
                .ifPresent(key -> { throw BusinessException.error("不支持的 task.update 参数: " + key); });
    }

    private boolean onlyTaskIdAndVersion(Map<String, Object> arguments) {
        return arguments.keySet().stream().allMatch(key -> key.equals("taskId") || key.equals("version"));
    }

    private void addIfPresent(Map<String, Object> change, Map<String, Object> arguments, String key) {
        if (arguments.containsKey(key)) change.put(key, arguments.get(key));
    }

    private String optionalNullableText(Map<String, Object> arguments, String key) {
        if (!arguments.containsKey(key)) return null;
        Object value = arguments.get(key);
        if (value == null || value instanceof String) return (String) value;
        throw BusinessException.error("参数 " + key + " 必须是文本");
    }

    private Boolean booleanValue(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value == null) return false;
        if (value instanceof Boolean bool) return bool;
        throw BusinessException.error("参数 " + key + " 必须是布尔值");
    }

    private Map<String, Object> readArguments(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw BusinessException.error("任务更新参数无效");
        }
    }
}
