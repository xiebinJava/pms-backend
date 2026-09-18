package com.brad.pms.ai.command.project;

import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.common.ProjectScheduleRange;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.ProjectCreateCmd;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.entity.AiOperationDO;
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

@Component
@RequiredArgsConstructor
public class CreateProjectCommand implements PmsCommand {

    private static final Set<String> ALLOWED_ARGUMENTS = Set.of(
            "name", "description", "priority", "projectLevel", "projectTypeId",
            "workflowTemplateVersionId", "startDate", "endDate", "orgUnitId");

    private final ProjectService projectService;
    private final ProjectPermissionService permissionService;
    private final ObjectMapper objectMapper;

    @Override
    public CommandName name() {
        return CommandName.PROJECT_CREATE;
    }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> arguments = request.arguments();
        CommandArgumentReader.rejectUnknown(arguments, ALLOWED_ARGUMENTS, "project.create");
        String projectName = CommandArgumentReader.requiredText(arguments, "name");
        LocalDate startDate = CommandArgumentReader.optionalDate(arguments, "startDate");
        LocalDate endDate = CommandArgumentReader.optionalDate(arguments, "endDate");
        if (!ProjectScheduleRange.isOrdered(startDate, endDate)) {
            throw BusinessException.error("项目开始日期不能晚于结束日期");
        }
        Long orgUnitId = CommandArgumentReader.optionalLong(arguments, "orgUnitId");
        Long resolvedOrgUnitId = permissionService.requireProjectCreateOrgUnit(orgUnitId);

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "project");
        change.put("action", "create");
        change.put("name", projectName);
        change.put("description", CommandArgumentReader.optionalText(arguments, "description"));
        change.put("priority", CommandArgumentReader.optionalInteger(arguments, "priority", 1));
        change.put("projectLevel", CommandArgumentReader.optionalInteger(arguments, "projectLevel", 0));
        change.put("projectTypeId", CommandArgumentReader.optionalLong(arguments, "projectTypeId"));
        change.put("workflowTemplateVersionId", CommandArgumentReader.optionalLong(arguments, "workflowTemplateVersionId"));
        change.put("startDate", startDate);
        change.put("endDate", endDate);
        change.put("orgUnitId", resolvedOrgUnitId);
        return new CommandPreview(null, name(), Instant.now().plusSeconds(600), request.contextVersion(),
                List.of("创建后 PMS 会初始化项目节点，并将当前用户加入项目成员；项目经理需后续单独设置"),
                List.of(change), List.of("project-list", "project-dashboard"));
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = readArguments(operation.getArgumentsJson());
        CommandArgumentReader.rejectUnknown(arguments, ALLOWED_ARGUMENTS, "project.create");
        ProjectCreateCmd command = new ProjectCreateCmd();
        command.setName(CommandArgumentReader.requiredText(arguments, "name"));
        command.setDescription(CommandArgumentReader.optionalText(arguments, "description"));
        command.setPriority(CommandArgumentReader.optionalInteger(arguments, "priority", 1));
        command.setProjectLevel(CommandArgumentReader.optionalInteger(arguments, "projectLevel", 0));
        command.setProjectTypeId(CommandArgumentReader.optionalLong(arguments, "projectTypeId"));
        command.setWorkflowTemplateVersionId(CommandArgumentReader.optionalLong(arguments, "workflowTemplateVersionId"));
        command.setStartDate(CommandArgumentReader.optionalDate(arguments, "startDate"));
        command.setEndDate(CommandArgumentReader.optionalDate(arguments, "endDate"));
        command.setOrgUnitId(CommandArgumentReader.optionalLong(arguments, "orgUnitId"));
        ProjectDTO project = projectService.create(command);
        return new CommandResult(operation.getId(), "SUCCEEDED", "项目已创建", Map.of("project", project),
                List.of("project-list", "project-dashboard", "project-detail"));
    }

    private Map<String, Object> readArguments(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw BusinessException.error("项目创建参数无效");
        }
    }
}
