package com.brad.pms.ai.command.requirement;

import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.ai.command.PmsCommandSupport;
import com.brad.pms.common.enums.RequirementExecutionTargetType;
import com.brad.pms.dto.request.RequirementExecutionTargetCmd;
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
public class LinkRequirementTargetCommand implements PmsCommand {

    protected static final Set<String> ALLOWED = Set.of(
            "requirementId", "requirementVersion", "targetType", "targetId", "reason");
    private static final List<String> REFRESH = List.of("requirement-list", "requirement-detail", "development-list");

    protected final RequirementExecutionTargetService targetService;
    protected final PmsCommandSupport support;

    @Override
    public CommandName name() {
        return CommandName.REQUIREMENT_EXECUTION_TARGET_LINK;
    }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> arguments = request.arguments();
        support.rejectUnknown(arguments, ALLOWED, name().code());
        Map<String, Object> change = targetChange(arguments, "link");
        return support.preview(request, name(), "关联需求执行对象", change,
                List.of("一个需求只能关联一个执行对象"), REFRESH);
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = support.readArguments(operation, name().code());
        support.rejectUnknown(arguments, ALLOWED, name().code());
        RequirementExecutionTargetDTO target = targetService.link(
                CommandArgumentReader.requiredLong(arguments, "requirementId"), target(arguments));
        return support.result(operation, "需求执行对象已关联", Map.of("target", target), REFRESH);
    }

    protected RequirementExecutionTargetCmd target(Map<String, Object> arguments) {
        RequirementExecutionTargetCmd command = new RequirementExecutionTargetCmd();
        command.setTargetType(targetType(arguments));
        command.setTargetId(CommandArgumentReader.requiredLong(arguments, "targetId"));
        command.setRequirementVersion(CommandArgumentReader.requiredInteger(arguments, "requirementVersion"));
        command.setReason(CommandArgumentReader.optionalText(arguments, "reason"));
        return command;
    }

    protected RequirementExecutionTargetType targetType(Map<String, Object> arguments) {
        String raw = CommandArgumentReader.requiredText(arguments, "targetType");
        try {
            return RequirementExecutionTargetType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw com.brad.pms.common.exception.BusinessException.error("targetType 只支持 PROJECT、TOPIC 或 STORY");
        }
    }

    protected Map<String, Object> targetChange(Map<String, Object> arguments, String action) {
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "requirement-execution-target");
        change.put("action", action);
        change.put("requirementId", CommandArgumentReader.requiredLong(arguments, "requirementId"));
        change.put("targetType", targetType(arguments).name());
        change.put("targetId", CommandArgumentReader.requiredLong(arguments, "targetId"));
        change.put("requirementVersion", CommandArgumentReader.requiredInteger(arguments, "requirementVersion"));
        return change;
    }

    protected List<String> refreshScopes() {
        return REFRESH;
    }
}
