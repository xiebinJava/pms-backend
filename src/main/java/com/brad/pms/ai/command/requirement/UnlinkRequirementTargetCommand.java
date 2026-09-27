package com.brad.pms.ai.command.requirement;

import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.ai.command.PmsCommandSupport;
import com.brad.pms.dto.response.RequirementExecutionTargetDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.service.RequirementExecutionTargetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UnlinkRequirementTargetCommand implements PmsCommand {
    private static final Set<String> ALLOWED = Set.of("requirementId", "requirementVersion", "reason");
    private static final List<String> REFRESH = List.of("requirement-list", "requirement-detail", "development-list");
    private final RequirementExecutionTargetService targetService;
    private final PmsCommandSupport support;

    @Override
    public CommandName name() { return CommandName.REQUIREMENT_EXECUTION_TARGET_UNLINK; }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> arguments = request.arguments();
        support.rejectUnknown(arguments, ALLOWED, name().code());
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "requirement-execution-target");
        change.put("action", "unlink");
        change.put("requirementId", CommandArgumentReader.requiredLong(arguments, "requirementId"));
        change.put("requirementVersion", CommandArgumentReader.requiredInteger(arguments, "requirementVersion"));
        return support.preview(request, name(), "解除需求执行对象", change, List.of(), REFRESH);
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = support.readArguments(operation, name().code());
        support.rejectUnknown(arguments, ALLOWED, name().code());
        RequirementExecutionTargetDTO target = targetService.unlink(
                CommandArgumentReader.requiredLong(arguments, "requirementId"),
                CommandArgumentReader.requiredInteger(arguments, "requirementVersion"),
                CommandArgumentReader.optionalText(arguments, "reason"));
        return support.result(operation, "需求执行对象已解除",
                Map.of("target", target == null ? Map.of() : target), REFRESH);
    }
}
