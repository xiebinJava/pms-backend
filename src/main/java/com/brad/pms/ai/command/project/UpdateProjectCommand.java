package com.brad.pms.ai.command.project;

import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.ai.command.PmsCommandVersionGuard;
import com.brad.pms.common.ProjectScheduleRange;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.ProjectUpdateCmd;
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
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Partial project-field update for the PMS Agent. Only the fields the caller
 * names change; project composition (members, followers, project manager) is
 * deliberately out of scope so an edit cannot drop them by accident.
 */
@Component
@RequiredArgsConstructor
public class UpdateProjectCommand implements PmsCommand {

    private static final Set<String> ALLOWED_ARGUMENTS = Set.of(
            "projectId", "name", "description", "priority", "projectLevel", "orgUnitId", "startDate", "endDate");

    private static final List<String> REFRESH_SCOPES =
            List.of("project-detail", "project-list", "project-dashboard");

    private final ProjectService projectService;
    private final ProjectPermissionService permissionService;
    private final ObjectMapper objectMapper;

    @Override
    public CommandName name() {
        return CommandName.PROJECT_UPDATE;
    }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> arguments = request.arguments();
        CommandArgumentReader.rejectUnknown(arguments, ALLOWED_ARGUMENTS, "project.update");
        Long projectId = CommandArgumentReader.requiredLong(arguments, "projectId");
        ProjectDO project = permissionService.requireProjectWritable(projectId, "编辑项目");

        String name = arguments.containsKey("name")
                ? CommandArgumentReader.requiredText(arguments, "name") : project.getName();
        String description = arguments.containsKey("description")
                ? CommandArgumentReader.optionalText(arguments, "description") : project.getDescription();
        LocalDate startDate = resolveDate(arguments, "startDate", project.getStartDate());
        LocalDate endDate = resolveDate(arguments, "endDate", project.getEndDate());
        if (!ProjectScheduleRange.isOrdered(startDate, endDate)) {
            throw BusinessException.error("项目开始日期不能晚于结束日期");
        }
        Integer priority = resolveInteger(arguments, "priority");
        Integer projectLevel = resolveInteger(arguments, "projectLevel");
        Long orgUnitId = arguments.containsKey("orgUnitId")
                ? CommandArgumentReader.optionalLong(arguments, "orgUnitId") : null;

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "project");
        change.put("action", "update");
        change.put("projectId", projectId);
        change.put("projectName", project.getName());
        change.put("fromName", project.getName());
        change.put("toName", name);
        change.put("fromDescription", project.getDescription());
        change.put("toDescription", description);
        change.put("fromStartDate", project.getStartDate());
        change.put("toStartDate", startDate);
        change.put("fromEndDate", project.getEndDate());
        change.put("toEndDate", endDate);
        if (priority != null) {
            change.put("fromPriority", project.getPriority());
            change.put("toPriority", priority);
        }
        if (projectLevel != null) {
            change.put("fromProjectLevel", project.getProjectLevel());
            change.put("toProjectLevel", projectLevel);
        }
        if (orgUnitId != null) {
            change.put("fromOrgUnitId", project.getOrgUnitId());
            change.put("toOrgUnitId", orgUnitId);
        }
        change.put("projectVersion", project.getVersion());
        return new CommandPreview(null, name(), Instant.now().plusSeconds(600), request.contextVersion(),
                List.of("未提供的字段保留原值；显式传 null 可清空描述或日期",
                        "项目更新不会改动项目成员、关注人和项目经理"),
                List.of(change), REFRESH_SCOPES);
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = readArguments(operation.getArgumentsJson());
        CommandArgumentReader.rejectUnknown(arguments, ALLOWED_ARGUMENTS, "project.update");
        Long projectId = CommandArgumentReader.requiredLong(arguments, "projectId");
        ProjectDO project = permissionService.requireProjectWritable(projectId, "编辑项目");
        assertFresh(operation, project.getVersion());

        ProjectUpdateCmd command = new ProjectUpdateCmd();
        command.setVersion(project.getVersion());
        command.setName(arguments.containsKey("name")
                ? CommandArgumentReader.requiredText(arguments, "name") : project.getName());
        command.setDescription(arguments.containsKey("description")
                ? CommandArgumentReader.optionalText(arguments, "description") : project.getDescription());
        command.setStartDate(resolveDate(arguments, "startDate", project.getStartDate()));
        command.setEndDate(resolveDate(arguments, "endDate", project.getEndDate()));
        if (!ProjectScheduleRange.isOrdered(command.getStartDate(), command.getEndDate())) {
            throw BusinessException.error("项目开始日期不能晚于结束日期");
        }
        command.setPriority(resolveInteger(arguments, "priority"));
        command.setProjectLevel(resolveInteger(arguments, "projectLevel"));
        command.setOrgUnitId(arguments.containsKey("orgUnitId")
                ? CommandArgumentReader.optionalLong(arguments, "orgUnitId") : null);

        ProjectDTO updated = projectService.update(projectId, command);
        return new CommandResult(operation.getId(), "SUCCEEDED", "项目已更新", Map.of("project", updated), REFRESH_SCOPES);
    }

    private LocalDate resolveDate(Map<String, Object> arguments, String key, LocalDate current) {
        return arguments.containsKey(key) ? CommandArgumentReader.optionalDate(arguments, key) : current;
    }

    private Integer resolveInteger(Map<String, Object> arguments, String key) {
        return arguments.containsKey(key) ? CommandArgumentReader.optionalInteger(arguments, key, null) : null;
    }

    private void assertFresh(AiOperationDO operation, Integer projectVersion) {
        try {
            List<Map<String, Object>> changes = objectMapper.readValue(
                    operation.getExpectedVersionsJson(), new TypeReference<>() { });
            if (!changes.isEmpty()) {
                PmsCommandVersionGuard.requireMatch("项目", changes.get(0).get("projectVersion"), projectVersion);
            }
        } catch (JsonProcessingException e) {
            throw BusinessException.error("项目更新预览版本信息无效");
        }
    }

    private Map<String, Object> readArguments(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw BusinessException.error("项目更新参数无效");
        }
    }
}
