package com.brad.pms.ai.command.iteration;

import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.ai.command.PmsCommandSupport;
import com.brad.pms.dto.request.NodeIterationPlanCmd;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.service.IterationPlanCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CreateIterationPlanCommand implements PmsCommand {
    private static final Set<String> ALLOWED = Set.of("projectId", "nodeId", "name", "ownerId", "goal", "status",
            "startDate", "dueDate", "sort");
    private static final List<String> REFRESH = List.of("iteration-plan", "project-detail");
    private final IterationPlanCommandService planService;
    private final PmsCommandSupport support;

    @Override public CommandName name() { return CommandName.ITERATION_PLAN_CREATE; }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> args = request.arguments(); support.rejectUnknown(args, ALLOWED, name().code());
        Map<String, Object> change = new LinkedHashMap<>(); change.put("entity", "iteration-plan"); change.put("action", "create");
        change.put("projectId", CommandArgumentReader.requiredLong(args, "projectId"));
        change.put("nodeId", CommandArgumentReader.requiredLong(args, "nodeId"));
        change.put("name", CommandArgumentReader.requiredText(args, "name"));
        return support.preview(request, name(), "创建迭代计划", change,
                List.of("迭代计划是项目规划视图，没有独立流程模板"), REFRESH);
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> args = support.readArguments(operation, name().code()); support.rejectUnknown(args, ALLOWED, name().code());
        Long id = planService.create(CommandArgumentReader.requiredLong(args, "projectId"),
                CommandArgumentReader.requiredLong(args, "nodeId"), command(args));
        return support.result(operation, "迭代计划已创建", Map.of("iterationPlanId", id), REFRESH);
    }

    static NodeIterationPlanCmd command(Map<String, Object> args) {
        NodeIterationPlanCmd cmd = new NodeIterationPlanCmd();
        cmd.setName(CommandArgumentReader.requiredText(args, "name"));
        cmd.setOwnerId(CommandArgumentReader.optionalLong(args, "ownerId"));
        cmd.setGoal(CommandArgumentReader.optionalText(args, "goal"));
        cmd.setStatus(CommandArgumentReader.optionalText(args, "status"));
        cmd.setStartDate(CommandArgumentReader.optionalDate(args, "startDate"));
        cmd.setDueDate(CommandArgumentReader.optionalDate(args, "dueDate"));
        cmd.setSort(CommandArgumentReader.optionalInteger(args, "sort", null));
        return cmd;
    }
}
