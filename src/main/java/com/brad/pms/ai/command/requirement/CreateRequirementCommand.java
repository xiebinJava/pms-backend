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
public class CreateRequirementCommand implements PmsCommand {

    private static final Set<String> ALLOWED = Set.of(
            "title", "description", "priority", "ownerId", "templateVersionId");
    private static final List<String> REFRESH = List.of("requirement-list", "requirement-detail");

    private final RequirementManagementService requirementService;
    private final PmsCommandSupport support;

    @Override
    public CommandName name() {
        return CommandName.REQUIREMENT_CREATE;
    }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> arguments = request.arguments();
        support.rejectUnknown(arguments, ALLOWED, name().code());
        String title = CommandArgumentReader.requiredText(arguments, "title");
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "requirement");
        change.put("action", "create");
        change.put("title", title);
        change.put("ownerId", CommandArgumentReader.optionalLong(arguments, "ownerId"));
        return support.preview(request, name(), "创建需求", change,
                List.of("需求创建后可再关联一个项目、专题或故事作为唯一执行对象"), REFRESH);
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = support.readArguments(operation, name().code());
        support.rejectUnknown(arguments, ALLOWED, name().code());
        RequirementSaveCmd command = new RequirementSaveCmd();
        command.setTitle(CommandArgumentReader.requiredText(arguments, "title"));
        command.setDescription(CommandArgumentReader.optionalText(arguments, "description"));
        command.setPriority(CommandArgumentReader.optionalInteger(arguments, "priority", null));
        command.setOwnerId(CommandArgumentReader.optionalLong(arguments, "ownerId"));
        command.setTemplateVersionId(CommandArgumentReader.optionalLong(arguments, "templateVersionId"));
        Long id = requirementService.create(command);
        RequirementListDTO requirement = requirementService.detail(id);
        return support.result(operation, "需求已创建", Map.of("requirement", requirement), REFRESH);
    }
}
