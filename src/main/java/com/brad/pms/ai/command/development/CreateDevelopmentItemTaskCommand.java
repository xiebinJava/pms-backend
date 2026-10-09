package com.brad.pms.ai.command.development;

import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.ai.command.PmsCommandSupport;
import com.brad.pms.dto.request.DevelopmentItemTaskSaveCmd;
import com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.service.DevelopmentItemWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CreateDevelopmentItemTaskCommand implements PmsCommand {
    private static final Set<String> ALLOWED = Set.of("itemType", "itemId", "nodeId", "parentId", "title",
            "description", "status", "priority", "assigneeId", "dueDate", "sort");
    private static final List<String> REFRESH = List.of("development-detail", "project-detail", "topic-detail", "story-detail", "task-board");
    private final DevelopmentItemWorkflowService workflowService;
    private final DevelopmentItemCommandSupport itemSupport;
    private final PmsCommandSupport support;

    @Override public CommandName name() { return CommandName.DEVELOPMENT_ITEM_TASK_CREATE; }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> args = request.arguments(); support.rejectUnknown(args, ALLOWED, name().code());
        var node = itemSupport.node(args);
        Map<String, Object> change = itemSupport.change("task.create", args, node);
        change.put("title", CommandArgumentReader.requiredText(args, "title"));
        change.put("assigneeId", CommandArgumentReader.optionalLong(args, "assigneeId"));
        return support.preview(request, name(), "创建研发事项节点任务", change, List.of(), REFRESH);
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> args = support.readArguments(operation, name().code());
        support.rejectUnknown(args, ALLOWED, name().code());
        DevelopmentItemTaskSaveCmd command = new DevelopmentItemTaskSaveCmd();
        command.setParentId(CommandArgumentReader.optionalLong(args, "parentId"));
        command.setTitle(CommandArgumentReader.requiredText(args, "title"));
        command.setDescription(CommandArgumentReader.optionalText(args, "description"));
        command.setStatus(CommandArgumentReader.optionalInteger(args, "status", 0));
        command.setPriority(CommandArgumentReader.optionalInteger(args, "priority", 1));
        command.setAssigneeId(CommandArgumentReader.optionalLong(args, "assigneeId"));
        command.setDueDate(CommandArgumentReader.optionalDate(args, "dueDate"));
        command.setSort(CommandArgumentReader.optionalInteger(args, "sort", 0));
        DevelopmentItemWorkflowDetailDTO detail = workflowService.saveTask(itemSupport.itemType(args),
                itemSupport.itemId(args), itemSupport.nodeId(args), null, command);
        return support.result(operation, "研发事项节点任务已创建", Map.of("workflow", detail), REFRESH);
    }
}
