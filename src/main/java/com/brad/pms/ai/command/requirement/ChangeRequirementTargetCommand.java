package com.brad.pms.ai.command.requirement;

import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.dto.response.RequirementExecutionTargetDTO;
import com.brad.pms.entity.AiOperationDO;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ChangeRequirementTargetCommand extends LinkRequirementTargetCommand {

    public ChangeRequirementTargetCommand(com.brad.pms.service.RequirementExecutionTargetService targetService,
                                          com.brad.pms.ai.command.PmsCommandSupport support) {
        super(targetService, support);
    }

    @Override
    public CommandName name() {
        return CommandName.REQUIREMENT_EXECUTION_TARGET_CHANGE;
    }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> arguments = request.arguments();
        support.rejectUnknown(arguments, ALLOWED, name().code());
        if (com.brad.pms.ai.command.CommandArgumentReader.optionalText(arguments, "reason") == null) {
            throw com.brad.pms.common.exception.BusinessException.error("改绑执行对象必须填写 reason");
        }
        return support.preview(request, name(), "更换需求执行对象", targetChange(arguments, "replace"),
                java.util.List.of("一个需求只能关联一个执行对象"), refreshScopes());
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = support.readArguments(operation, name().code());
        support.rejectUnknown(arguments, ALLOWED, name().code());
        RequirementExecutionTargetDTO target = targetService.change(
                com.brad.pms.ai.command.CommandArgumentReader.requiredLong(arguments, "requirementId"), target(arguments));
        return support.result(operation, "需求执行对象已更换", Map.of("target", target), refreshScopes());
    }
}
