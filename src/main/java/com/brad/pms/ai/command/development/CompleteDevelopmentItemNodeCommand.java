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
import com.brad.pms.workflow.WorkflowFieldValueValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
        List<CommandPreview.Precondition> preconditions = preconditions(args, node);
        return support.preview(request, name(), "完成研发事项流程节点", itemSupport.change("complete", args, node),
                List.of("完成前必须满足节点必填字段和任务完成规则"), REFRESH, preconditions);
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> args = support.readArguments(operation, name().code());
        support.rejectUnknown(args, ALLOWED, name().code());
        DevelopmentItemWorkflowDetailDTO detail = workflowService.completeNode(itemSupport.itemType(args),
                itemSupport.itemId(args), itemSupport.nodeId(args));
        return support.result(operation, "研发事项流程节点已完成", Map.of("workflow", detail), REFRESH);
    }

    private List<CommandPreview.Precondition> preconditions(Map<String, Object> args,
                                                             com.brad.pms.dto.response.DevelopmentItemWorkflowNodeDTO node) {
        List<CommandPreview.Precondition> result = new ArrayList<>();
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("itemType", itemSupport.itemType(args).name());
        target.put("itemId", itemSupport.itemId(args));
        target.put("nodeId", itemSupport.nodeId(args));
        target.put("version", node.getVersion());

        if (node.getOwnerId() == null) {
            result.add(new CommandPreview.Precondition(
                    "NODE_OWNER_REQUIRED", "请先分配节点负责人", true,
                    "development-item.node.owner.update", target));
        }
        LocalDate startDate = node.getStartDate();
        LocalDate endDate = node.getEndDate();
        if (startDate == null || endDate == null) {
            result.add(new CommandPreview.Precondition(
                    "NODE_SCHEDULE_REQUIRED", "请先设置节点排期", true,
                    "development-item.node.schedule.update", target));
        } else if (startDate.isAfter(endDate)) {
            result.add(new CommandPreview.Precondition(
                    "NODE_SCHEDULE_INVALID", "节点排期开始日期不能晚于结束日期", true,
                    "development-item.node.schedule.update", target));
        }

        Map<String, com.fasterxml.jackson.databind.JsonNode> values = node.getFieldValues() == null
                ? Map.of() : node.getFieldValues();
        List<String> missingFields = new ArrayList<>(WorkflowFieldValueValidator.missingRequiredFields(
                node.getFields() == null ? List.of() : node.getFields(), values));
        if (node.getFields() != null) {
            for (var field : node.getFields()) {
                if (!field.required() || Boolean.FALSE.equals(field.visible()) || field.binding() == null) continue;
                var value = node.getBoundFieldValues() == null ? null : node.getBoundFieldValues().get(field.binding());
                if (value == null || value.isNull() || (value.isTextual() && value.asText().isBlank())) {
                    missingFields.add(field.label());
                }
            }
        }
        if (!missingFields.isEmpty()) {
            result.add(new CommandPreview.Precondition(
                    "REQUIRED_FIELDS", "请先填写节点必填字段", true,
                    "development-item.node.field.update", Map.of("target", target, "fields", missingFields)));
        }

        int incompleteTasks = countIncompleteTasks(node.getTasks());
        if (incompleteTasks > 0) {
            result.add(new CommandPreview.Precondition(
                    "INCOMPLETE_TASKS", "请先完成当前节点的全部任务和子任务", true,
                    "task.update", Map.of("target", target, "count", incompleteTasks)));
        }
        return result;
    }

    private int countIncompleteTasks(List<com.brad.pms.dto.response.DevelopmentItemTaskDTO> tasks) {
        if (tasks == null) return 0;
        int count = 0;
        for (var task : tasks) {
            if (task == null) continue;
            if (!Integer.valueOf(2).equals(task.getStatus())) count++;
            count += countIncompleteTasks(task.getChildren());
        }
        return count;
    }
}
