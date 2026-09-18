package com.brad.pms.ai.command.project;

import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.ai.command.PmsCommandVersionGuard;
import com.brad.pms.common.enums.ProjectStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.service.ProjectPermissionService;
import com.brad.pms.service.ProjectService;
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
public class ArchiveProjectCommand implements PmsCommand {

    private static final Set<String> ALLOWED_ARGUMENTS = Set.of("projectId", "reason");

    private final ProjectService projectService;
    private final ProjectPermissionService permissionService;
    private final ObjectMapper objectMapper;

    @Override
    public CommandName name() {
        return CommandName.PROJECT_ARCHIVE;
    }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        CommandArgumentReader.rejectUnknown(request.arguments(), ALLOWED_ARGUMENTS, "project.archive");
        Long projectId = CommandArgumentReader.requiredLong(request.arguments(), "projectId");
        String reason = CommandArgumentReader.requiredText(request.arguments(), "reason");
        ProjectDO project = permissionService.requireProjectManageable(projectId, "归档项目");
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "project");
        change.put("action", "archive");
        change.put("projectId", projectId);
        change.put("projectName", project.getName());
        change.put("fromStatus", ProjectStatus.normalize(project.getStatus()));
        change.put("toStatus", ProjectStatus.TERMINATED.getCode());
        change.put("reason", reason);
        change.put("projectVersion", project.getVersion());
        return new CommandPreview(null, name(), Instant.now().plusSeconds(600), request.contextVersion(),
                List.of("归档在 PMS 中对应终止项目；执行后项目及当前节点将进入只读状态"),
                List.of(change), List.of("project-list", "project-detail", "project-dashboard"));
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = readArguments(operation.getArgumentsJson());
        CommandArgumentReader.rejectUnknown(arguments, ALLOWED_ARGUMENTS, "project.archive");
        Long projectId = CommandArgumentReader.requiredLong(arguments, "projectId");
        String reason = CommandArgumentReader.requiredText(arguments, "reason");
        ProjectDO project = permissionService.requireProjectManageable(projectId, "归档项目");
        assertFresh(operation, project.getVersion());
        ProjectDTO archived = projectService.terminate(projectId, reason);
        return new CommandResult(operation.getId(), "SUCCEEDED", "项目已归档", Map.of("project", archived),
                List.of("project-list", "project-detail", "project-dashboard"));
    }

    private void assertFresh(AiOperationDO operation, Integer projectVersion) {
        try {
            List<Map<String, Object>> changes = objectMapper.readValue(operation.getExpectedVersionsJson(), new TypeReference<>() { });
            if (!changes.isEmpty()) {
                PmsCommandVersionGuard.requireMatch("项目", changes.get(0).get("projectVersion"), projectVersion);
            }
        } catch (JsonProcessingException e) {
            throw BusinessException.error("项目归档预览版本信息无效");
        }
    }

    private Map<String, Object> readArguments(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw BusinessException.error("项目归档参数无效");
        }
    }
}
