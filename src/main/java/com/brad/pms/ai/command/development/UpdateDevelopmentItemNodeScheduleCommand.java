package com.brad.pms.ai.command.development;

import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.ai.command.PmsCommandSupport;
import com.brad.pms.dto.request.DevelopmentItemNodeUpdateCmd;
import com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO;
import com.brad.pms.dto.response.DevelopmentItemWorkflowNodeDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.service.DevelopmentItemWorkflowService;
import com.brad.pms.workflow.DevelopmentItemType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UpdateDevelopmentItemNodeScheduleCommand implements PmsCommand {
    private static final Set<String> ALLOWED = Set.of("itemType", "itemId", "nodeId", "startDate", "endDate", "version");
    private static final List<String> REFRESH = List.of("development-detail", "project-detail", "topic-detail", "story-detail");
    private final DevelopmentItemWorkflowService workflowService;
    private final DevelopmentItemCommandSupport itemSupport;
    private final PmsCommandSupport support;

    @Override public CommandName name() { return CommandName.DEVELOPMENT_ITEM_NODE_SCHEDULE_UPDATE; }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> args = request.arguments(); support.rejectUnknown(args, ALLOWED, name().code());
        DevelopmentItemWorkflowNodeDTO node = itemSupport.node(args);
        itemSupport.requireCurrentVersion(node, itemSupport.requiredVersion(args));
        var change = itemSupport.change("schedule.update", args, node);
        change.put("fromStartDate", node.getStartDate()); change.put("fromEndDate", node.getEndDate());
        change.put("toStartDate", CommandArgumentReader.optionalDate(args, "startDate"));
        change.put("toEndDate", CommandArgumentReader.optionalDate(args, "endDate"));
        return support.preview(request, name(), "更新研发事项节点排期", change, List.of(), REFRESH);
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> args = support.readArguments(operation, name().code());
        support.rejectUnknown(args, ALLOWED, name().code());
        DevelopmentItemType type = itemSupport.itemType(args);
        DevelopmentItemWorkflowNodeDTO node = itemSupport.node(args);
        Integer version = itemSupport.requiredVersion(args); itemSupport.requireCurrentVersion(node, version);
        DevelopmentItemNodeUpdateCmd command = itemSupport.baseUpdate(node, version);
        command.setStartDate(CommandArgumentReader.optionalDate(args, "startDate"));
        command.setEndDate(CommandArgumentReader.optionalDate(args, "endDate"));
        DevelopmentItemWorkflowDetailDTO detail = workflowService.updateNode(type, itemSupport.itemId(args),
                itemSupport.nodeId(args), command);
        return support.result(operation, "研发事项节点排期已更新", Map.of("workflow", detail), REFRESH);
    }
}
