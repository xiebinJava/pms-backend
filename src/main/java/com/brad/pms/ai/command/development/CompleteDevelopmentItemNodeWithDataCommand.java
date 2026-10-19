package com.brad.pms.ai.command.development;

import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.ai.command.PmsCommandSupport;
import com.brad.pms.ai.connector.context.CompletionContract;
import com.brad.pms.dto.request.DevelopmentItemNodeUpdateCmd;
import com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO;
import com.brad.pms.dto.response.DevelopmentItemWorkflowNodeDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.service.DevelopmentItemWorkflowService;
import com.brad.pms.workflow.DevelopmentItemType;
import com.brad.pms.workflow.WorkflowFieldValueValidator;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Saves node details and advances the node in one automatic operation. */
@Component
@RequiredArgsConstructor
public class CompleteDevelopmentItemNodeWithDataCommand implements PmsCommand {
    private static final Set<String> ALLOWED = Set.of(
            "itemType", "itemId", "nodeId", "version", "ownerId", "startDate", "endDate", "fieldValues");
    private static final List<String> REFRESH = List.of("development-detail", "project-detail", "topic-detail", "story-detail");

    private final DevelopmentItemWorkflowService workflowService;
    private final DevelopmentItemCommandSupport itemSupport;
    private final PmsCommandSupport support;

    @Override public CommandName name() { return CommandName.DEVELOPMENT_ITEM_NODE_COMPLETE_WITH_DATA; }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> args = request.arguments();
        support.rejectUnknown(args, ALLOWED, name().code());
        DevelopmentItemWorkflowNodeDTO node = itemSupport.node(args);
        itemSupport.requireCurrentVersion(node, itemSupport.requiredVersion(args));
        Long ownerId = args.containsKey("ownerId") ? CommandArgumentReader.optionalLong(args, "ownerId") : node.getOwnerId();
        LocalDate startDate = args.containsKey("startDate") ? CommandArgumentReader.optionalDate(args, "startDate") : node.getStartDate();
        LocalDate endDate = args.containsKey("endDate") ? CommandArgumentReader.optionalDate(args, "endDate") : node.getEndDate();
        Map<String, JsonNode> incoming = args.containsKey("fieldValues") ? support.fieldValues(args, "fieldValues") : null;
        Map<String, JsonNode> values = mergeFieldValues(node, incoming);

        Map<String, Object> change = itemSupport.change("complete-with-data", args, node);
        CompletionContract.addTo(change, CompletionContract.developmentMode(node.getRuntimeComponents()),
                CompletionContract.developmentBlockReason(node.getRuntimeComponents()));
        change.put("ownerId", ownerId);
        change.put("startDate", startDate);
        change.put("endDate", endDate);
        change.put("allowedMutations", CompletionContract.allowedMutations());
        if (incoming != null) change.put("fieldValues", incoming);
        return support.preview(request, name(), "保存节点详情并完成研发事项流程节点", change,
                List.of("负责人、排期和动态字段会与节点推进在同一事务中提交"), REFRESH,
                preconditions(args, node, ownerId, startDate, endDate, values));
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> args = support.readArguments(operation, name().code());
        support.rejectUnknown(args, ALLOWED, name().code());
        DevelopmentItemType type = itemSupport.itemType(args);
        DevelopmentItemWorkflowNodeDTO node = itemSupport.node(args);
        Integer version = itemSupport.requiredVersion(args);
        itemSupport.requireCurrentVersion(node, version);

        DevelopmentItemNodeUpdateCmd update = new DevelopmentItemNodeUpdateCmd();
        update.setOwnerId(node.getOwnerId());
        update.setStartDate(node.getStartDate());
        update.setEndDate(node.getEndDate());
        update.setVersion(version);
        if (args.containsKey("ownerId")) update.setOwnerId(CommandArgumentReader.optionalLong(args, "ownerId"));
        if (args.containsKey("startDate")) update.setStartDate(CommandArgumentReader.optionalDate(args, "startDate"));
        if (args.containsKey("endDate")) update.setEndDate(CommandArgumentReader.optionalDate(args, "endDate"));
        if (args.containsKey("fieldValues")) {
            Map<String, JsonNode> incoming = support.fieldValues(args, "fieldValues");
            if (incoming == null || incoming.isEmpty()) {
                throw com.brad.pms.common.exception.BusinessException.error("fieldValues 不能为空");
            }
            update.setFieldValues(mergeFieldValues(node, incoming));
        }

        // AiOperationService owns the surrounding transaction. A completion
        // failure therefore rolls back the preceding detail update as well.
        workflowService.updateNode(type, itemSupport.itemId(args), itemSupport.nodeId(args), update);
        DevelopmentItemWorkflowDetailDTO detail = workflowService.completeNode(
                type, itemSupport.itemId(args), itemSupport.nodeId(args));
        return support.result(operation, "研发事项节点详情已保存并完成", Map.of(
                "workflow", detail,
                "allowedMutations", CompletionContract.allowedMutations()), REFRESH);
    }

    private Map<String, JsonNode> mergeFieldValues(DevelopmentItemWorkflowNodeDTO node,
                                                    Map<String, JsonNode> incoming) {
        Map<String, JsonNode> values = new LinkedHashMap<>(node.getFieldValues() == null ? Map.of() : node.getFieldValues());
        if (incoming != null) values.putAll(incoming);
        return values;
    }

    private List<CommandPreview.Precondition> preconditions(
            Map<String, Object> args, DevelopmentItemWorkflowNodeDTO node,
            Long ownerId, LocalDate startDate, LocalDate endDate, Map<String, JsonNode> values) {
        List<CommandPreview.Precondition> result = new ArrayList<>();
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("itemType", itemSupport.itemType(args).name());
        target.put("itemId", itemSupport.itemId(args));
        target.put("nodeId", itemSupport.nodeId(args));
        target.put("version", node.getVersion());
        if (ownerId == null) {
            result.add(new CommandPreview.Precondition("NODE_OWNER_REQUIRED", "请提供节点负责人", true,
                    "development-item.node.owner.update", target));
        }
        if (startDate == null || endDate == null) {
            result.add(new CommandPreview.Precondition("NODE_SCHEDULE_REQUIRED", "请提供节点排期", true,
                    "development-item.node.schedule.update", target));
        } else if (startDate.isAfter(endDate)) {
            result.add(new CommandPreview.Precondition("NODE_SCHEDULE_INVALID", "节点排期开始日期不能晚于结束日期", true,
                    "development-item.node.schedule.update", target));
        }
        List<String> missing = new ArrayList<>(WorkflowFieldValueValidator.missingRequiredFields(
                node.getFields() == null ? List.of() : node.getFields(), values));
        if (node.getFields() != null) {
            for (var field : node.getFields()) {
                if (!field.required() || Boolean.FALSE.equals(field.visible()) || field.binding() == null) continue;
                var value = node.getBoundFieldValues() == null ? null : node.getBoundFieldValues().get(field.binding());
                if (value == null || value.isNull() || (value.isTextual() && value.asText().isBlank())) missing.add(field.label());
            }
        }
        if (!missing.isEmpty()) {
            result.add(new CommandPreview.Precondition("REQUIRED_FIELDS", "请提供节点必填字段", true,
                    "development-item.node.field.update", Map.of("target", target, "fields", missing)));
        }
        int incompleteTasks = countIncompleteTasks(node.getTasks());
        if (incompleteTasks > 0) {
            result.add(new CommandPreview.Precondition("INCOMPLETE_TASKS", "请先完成当前节点的全部任务和子任务", true,
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
