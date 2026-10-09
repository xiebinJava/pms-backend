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
import com.brad.pms.entity.ProjectNodeIterationPlanDO;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import com.brad.pms.service.IterationPlanCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class IterationPlanUpdateCommand implements PmsCommand {
    private static final Set<String> ALLOWED = Set.of("iterationPlanId", "name", "ownerId", "goal", "status",
            "startDate", "dueDate", "sort", "systemId", "systemVersionId");
    private static final List<String> REFRESH = List.of("iteration-plan", "project-detail");
    private final IterationPlanCommandService planService;
    private final ProjectNodeIterationPlanMapper planMapper;
    private final PmsCommandSupport support;

    @Override public CommandName name() { return CommandName.ITERATION_PLAN_UPDATE; }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> args = request.arguments(); support.rejectUnknown(args, ALLOWED, name().code());
        Long id = CommandArgumentReader.requiredLong(args, "iterationPlanId");
        ProjectNodeIterationPlanDO current = planMapper.selectById(id);
        if (current == null) throw com.brad.pms.common.exception.BusinessException.notFound("迭代计划不存在");
        Map<String, Object> change = new LinkedHashMap<>(); change.put("entity", "iteration-plan"); change.put("action", "update");
        change.put("iterationPlanId", id); change.put("name", CommandArgumentReader.requiredText(args, "name"));
        return support.preview(request, name(), "更新迭代计划", change, List.of(), REFRESH);
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> args = support.readArguments(operation, name().code()); support.rejectUnknown(args, ALLOWED, name().code());
        Long id = CommandArgumentReader.requiredLong(args, "iterationPlanId");
        planService.update(id, CreateIterationPlanCommand.command(args));
        return support.result(operation, "迭代计划已更新", Map.of("iterationPlanId", id), REFRESH);
    }
}
