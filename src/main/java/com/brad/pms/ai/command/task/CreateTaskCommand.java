package com.brad.pms.ai.command.task;

import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.TaskCreateCmd;
import com.brad.pms.dto.response.ProjectTaskDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.service.ProjectPermissionService;
import com.brad.pms.service.TaskService;
import com.brad.pms.security.UserContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CreateTaskCommand implements PmsCommand {

    private final TaskService taskService;
    private final ProjectPermissionService permissionService;
    private final ObjectMapper objectMapper;

    @Override
    public CommandName name() {
        return CommandName.TASK_CREATE;
    }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> arguments = request.arguments();
        Long projectId = CommandArgumentReader.requiredLong(arguments, "projectId");
        Long nodeId = CommandArgumentReader.requiredLong(arguments, "nodeId");
        String title = CommandArgumentReader.requiredText(arguments, "title");
        ProjectDO project = permissionService.requireProjectReadable(projectId);
        ProjectNodeDO node = permissionService.requireManageableNode(projectId, nodeId, "创建任务");

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "task");
        change.put("action", "create");
        change.put("projectId", projectId);
        change.put("nodeId", nodeId);
        change.put("title", title);
        change.put("projectVersion", project.getVersion());
        change.put("nodeVersion", node.getVersion());
        change.put("assigneeId", resolveAssigneeId(arguments));
        change.put("dueDate", CommandArgumentReader.optionalText(arguments, "dueDate"));
        return new CommandPreview(null, name(), Instant.now().plusSeconds(600), request.contextVersion(),
                List.of(), List.of(change), List.of("project-detail", "task-board", "project-dashboard"));
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = readArguments(operation.getArgumentsJson());
        TaskCreateCmd command = new TaskCreateCmd();
        command.setProjectId(CommandArgumentReader.requiredLong(arguments, "projectId"));
        command.setNodeId(CommandArgumentReader.requiredLong(arguments, "nodeId"));
        command.setParentId(CommandArgumentReader.optionalLong(arguments, "parentId"));
        command.setTitle(CommandArgumentReader.requiredText(arguments, "title"));
        command.setDescription(CommandArgumentReader.optionalText(arguments, "description"));
        command.setDeliverable(CommandArgumentReader.optionalText(arguments, "deliverable"));
        command.setStatus(CommandArgumentReader.optionalInteger(arguments, "status", 0));
        command.setPriority(CommandArgumentReader.optionalInteger(arguments, "priority", 1));
        command.setAssigneeId(resolveAssigneeId(arguments));
        command.setRequirementId(CommandArgumentReader.optionalLong(arguments, "requirementId"));
        command.setSort(CommandArgumentReader.optionalInteger(arguments, "sort", 0));
        command.setDueDate(CommandArgumentReader.optionalDate(arguments, "dueDate"));
        ProjectDO project = permissionService.requireProjectReadable(command.getProjectId());
        ProjectNodeDO node = permissionService.requireManageableNode(command.getProjectId(), command.getNodeId(), "创建任务");
        assertFresh(operation, project.getVersion(), node.getVersion());
        ProjectTaskDTO task = taskService.create(command);
        return new CommandResult(operation.getId(), "SUCCEEDED", "任务已创建", Map.of("task", task),
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
        } catch (JsonProcessingException e) {
            throw BusinessException.error("任务创建预览版本信息无效");
        }
    }

    private Map<String, Object> readArguments(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw BusinessException.error("任务创建参数无效");
        }
    }

    private Long resolveAssigneeId(Map<String, Object> arguments) {
        String scope = CommandArgumentReader.optionalText(arguments, "assigneeScope");
        if (scope == null) return CommandArgumentReader.optionalLong(arguments, "assigneeId");
        if (!"current_user".equals(scope)) {
            throw BusinessException.error("参数 assigneeScope 只支持 current_user");
        }
        return UserContext.userId();
    }
}
