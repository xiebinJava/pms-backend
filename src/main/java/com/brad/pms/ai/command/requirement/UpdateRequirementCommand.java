package com.brad.pms.ai.command.requirement;

import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.ai.command.PmsCommandSupport;
import com.brad.pms.dto.request.RequirementSaveCmd;
import com.brad.pms.dto.response.RequirementListDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.service.RequirementManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UpdateRequirementCommand implements PmsCommand {

    private static final Set<String> ALLOWED = Set.of(
            "requirementId", "version", "title", "description", "priority", "ownerId");
    private static final List<String> REFRESH = List.of("requirement-list", "requirement-detail");

    private final RequirementManagementService requirementService;
    private final PmsCommandSupport support;

    @Override
    public CommandName name() {
        return CommandName.REQUIREMENT_UPDATE;
    }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> arguments = request.arguments();
        support.rejectUnknown(arguments, ALLOWED, name().code());
        Long id = CommandArgumentReader.requiredLong(arguments, "requirementId");
        Integer version = CommandArgumentReader.optionalInteger(arguments, "version", null);
        if (version == null) throw com.brad.pms.common.exception.BusinessException.error("参数 version 必须是整数");
        CommandArgumentReader.requiredText(arguments, "title");
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "requirement");
        change.put("action", "update");
        change.put("requirementId", id);
        change.put("requirementVersion", version);
        change.put("title", arguments.get("title"));
        return support.preview(request, name(), "更新需求", change, List.of(), REFRESH);
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = support.readArguments(operation, name().code());
        support.rejectUnknown(arguments, ALLOWED, name().code());
        Long id = CommandArgumentReader.requiredLong(arguments, "requirementId");
        RequirementSaveCmd command = new RequirementSaveCmd();
        command.setTitle(CommandArgumentReader.requiredText(arguments, "title"));
        command.setDescription(CommandArgumentReader.optionalText(arguments, "description"));
        command.setPriority(CommandArgumentReader.optionalInteger(arguments, "priority", null));
        command.setOwnerId(CommandArgumentReader.optionalLong(arguments, "ownerId"));
        command.setVersion(CommandArgumentReader.optionalInteger(arguments, "version", null));
        requirementService.update(id, command);
        RequirementListDTO requirement = requirementService.detail(id);
        return support.result(operation, "需求已更新", Map.of("requirement", requirement), REFRESH);
    }
}
