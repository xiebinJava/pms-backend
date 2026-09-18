package com.brad.pms.ai.command.task;

import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.TaskUpdateCmd;
import com.brad.pms.dto.response.TaskDetailDTO;
import com.brad.pms.dto.response.ProjectTaskDTO;
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

@Component
@RequiredArgsConstructor
public class AssignTaskCommand implements PmsCommand {

    private final TaskService taskService;
    private final ProjectPermissionService permissionService;
    private final ObjectMapper objectMapper;

    @Override
    public CommandName name() {
        return CommandName.TASK_ASSIGN;
    }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Long taskId = CommandArgumentReader.requiredLong(request.arguments(), "taskId");
        Long assigneeId = CommandArgumentReader.requiredLong(request.arguments(), "assigneeId");
        TaskDetailDTO task = taskService.getDetail(taskId);
        ProjectDO project = permissionService.requireProjectReadable(task.getProjectId());
        ProjectNodeDO node = permissionService.requireManageableNode(task.getProjectId(), task.getNodeId(), "分配任务");

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "task");
        change.put("action", "assign");
        change.put("taskId", taskId);
        change.put("title", task.getTitle());
        change.put("fromAssigneeId", task.getAssigneeId());
        change.put("toAssigneeId", assigneeId);
        change.put("expectedVersion", task.getVersion());
        change.put("projectVersion", project.getVersion());
        change.put("nodeVersion", node.getVersion());
        return new CommandPreview(null, name(), Instant.now().plusSeconds(600), request.contextVersion(),
                List.of(), List.of(change), List.of("project-detail", "task-board", "project-dashboard"));
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = readArguments(operation.getArgumentsJson());
        Long taskId = CommandArgumentReader.requiredLong(arguments, "taskId");
        Long assigneeId = CommandArgumentReader.requiredLong(arguments, "assigneeId");
        TaskDetailDTO task = taskService.getDetail(taskId);
        ProjectDO project = permissionService.requireProjectReadable(task.getProjectId());
        ProjectNodeDO node = permissionService.requireManageableNode(task.getProjectId(), task.getNodeId(), "分配任务");
        assertFresh(operation, project.getVersion(), node.getVersion());
        Integer expectedVersion = CommandArgumentReader.optionalInteger(arguments, "version", null);
        if (expectedVersion == null) expectedVersion = expectedVersion(operation);
        if (expectedVersion == null) throw BusinessException.conflict("任务版本缺失，请重新生成预览");

        TaskUpdateCmd command = new TaskUpdateCmd();
        command.setVersion(expectedVersion);
        command.setAssigneeId(assigneeId);
        ProjectTaskDTO updatedTask = taskService.update(taskId, command);
        return new CommandResult(operation.getId(), "SUCCEEDED", "任务负责人已更新", Map.of("task", updatedTask),
                List.of("project-detail", "task-board", "project-dashboard"));
    }

    private void assertFresh(AiOperationDO operation, Integer projectVersion, Integer nodeVersion) {
        try {
            List<Map<String, Object>> changes = objectMapper.readValue(operation.getExpectedVersionsJson(),
                    new TypeReference<>() { });
            if (changes.isEmpty()) return;
            Map<String, Object> change = changes.get(0);
            CommandVersionGuard.requireMatch("项目", change.get("projectVersion"), projectVersion);
            CommandVersionGuard.requireMatch("节点", change.get("nodeVersion"), nodeVersion);
        } catch (Exception e) {
            if (e instanceof BusinessException businessException) throw businessException;
            throw BusinessException.error("任务分配预览版本信息无效");
        }
    }

    private Integer expectedVersion(AiOperationDO operation) {
        try {
            List<Map<String, Object>> changes = objectMapper.readValue(operation.getExpectedVersionsJson(),
                    new TypeReference<>() { });
            if (changes.isEmpty()) return null;
            Object value = changes.get(0).get("expectedVersion");
            if (value instanceof Number number) return number.intValue();
            if (value instanceof String string) return Integer.valueOf(string);
            return null;
        } catch (Exception e) {
            throw BusinessException.error("任务版本信息无效");
        }
    }

    private Map<String, Object> readArguments(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw BusinessException.error("任务分配参数无效");
        }
    }
}
