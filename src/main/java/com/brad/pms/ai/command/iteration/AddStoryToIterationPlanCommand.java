package com.brad.pms.ai.command.iteration;

import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.ai.command.PmsCommandSupport;
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
public class AddStoryToIterationPlanCommand implements PmsCommand {
    private static final Set<String> ALLOWED = Set.of("iterationPlanId", "storyId");
    private static final List<String> REFRESH = List.of("iteration-plan", "story-detail", "story-list");
    private final IterationPlanCommandService planService;
    private final PmsCommandSupport support;

    @Override public CommandName name() { return CommandName.ITERATION_PLAN_STORY_ADD; }
    @Override public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> args = request.arguments(); support.rejectUnknown(args, ALLOWED, name().code());
        Map<String, Object> change = new LinkedHashMap<>(); change.put("entity", "iteration-plan-story"); change.put("action", "add");
        change.put("iterationPlanId", CommandArgumentReader.requiredLong(args, "iterationPlanId"));
        change.put("storyId", CommandArgumentReader.requiredLong(args, "storyId"));
        return support.preview(request, name(), "将故事加入迭代计划", change, List.of(), REFRESH);
    }
    @Override public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> args = support.readArguments(operation, name().code()); support.rejectUnknown(args, ALLOWED, name().code());
        Long planId = CommandArgumentReader.requiredLong(args, "iterationPlanId");
        planService.addStory(planId, CommandArgumentReader.requiredLong(args, "storyId"));
        return support.result(operation, "故事已加入迭代计划", Map.of("iterationPlanId", planId), REFRESH);
    }
}
