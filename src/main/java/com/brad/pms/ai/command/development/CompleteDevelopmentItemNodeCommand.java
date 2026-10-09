package com.brad.pms.ai.command.development;

import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.ai.command.PmsCommandSupport;
import com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.service.DevelopmentItemWorkflowService;
import com.brad.pms.workflow.DevelopmentItemType;
import com.brad.pms.ai.command.CommandArgumentReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CompleteDevelopmentItemNodeCommand implements PmsCommand {
    private static final Set<String> ALLOWED = Set.of("itemType", "itemId", "nodeId");
    private static final List<String> REFRESH = List.of("development-detail", "project-detail", "topic-detail", "story-detail");
    private final DevelopmentItemWorkflowService workflowService;
    private final DevelopmentItemCommandSupport itemSupport;
    private final PmsCommandSupport support;

    @Override public CommandName name() { return CommandName.DEVELOPMENT_ITEM_NODE_COMPLETE; }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> args = request.arguments(); support.rejectUnknown(args, ALLOWED, name().code());
        var node = itemSupport.node(args);
        return support.preview(request, name(), "完成研发事项流程节点", itemSupport.change("complete", args, node),
                List.of("完成前必须满足节点必填字段和任务完成规则"), REFRESH);
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> args = support.readArguments(operation, name().code());
        support.rejectUnknown(args, ALLOWED, name().code());
        DevelopmentItemWorkflowDetailDTO detail = workflowService.completeNode(itemSupport.itemType(args),
                itemSupport.itemId(args), itemSupport.nodeId(args));
        return support.result(operation, "研发事项流程节点已完成", Map.of("workflow", detail), REFRESH);
    }
}
