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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UpdateDevelopmentItemNodeFieldCommand implements PmsCommand {
    private static final Set<String> ALLOWED = Set.of("itemType", "itemId", "nodeId", "fieldValues", "version");
    private static final List<String> REFRESH = List.of("development-detail", "project-detail", "topic-detail", "story-detail");
    private final DevelopmentItemWorkflowService workflowService;
    private final DevelopmentItemCommandSupport itemSupport;
    private final PmsCommandSupport support;

    @Override public CommandName name() { return CommandName.DEVELOPMENT_ITEM_NODE_FIELD_UPDATE; }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> args = request.arguments(); support.rejectUnknown(args, ALLOWED, name().code());
        DevelopmentItemWorkflowNodeDTO node = itemSupport.node(args);
        itemSupport.requireCurrentVersion(node, itemSupport.requiredVersion(args));
        Map<String, Object> change = itemSupport.change("field.update", args, node);
        change.put("fieldValues", itemSupport.commandSupport().fieldValues(args, "fieldValues"));
        return support.preview(request, name(), "更新研发事项节点动态字段", change, List.of(), REFRESH);
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> args = support.readArguments(operation, name().code());
        support.rejectUnknown(args, ALLOWED, name().code());
        DevelopmentItemType type = itemSupport.itemType(args);
        DevelopmentItemWorkflowNodeDTO node = itemSupport.node(args);
        Integer version = itemSupport.requiredVersion(args); itemSupport.requireCurrentVersion(node, version);
        DevelopmentItemNodeUpdateCmd command = itemSupport.baseUpdate(node, version);
        Map<String, com.fasterxml.jackson.databind.JsonNode> values = new LinkedHashMap<>(
                node.getFieldValues() == null ? Map.of() : node.getFieldValues());
        Map<String, com.fasterxml.jackson.databind.JsonNode> incoming = support.fieldValues(args, "fieldValues");
        if (incoming == null || incoming.isEmpty()) throw com.brad.pms.common.exception.BusinessException.error("fieldValues 不能为空");
        values.putAll(incoming); command.setFieldValues(values);
        DevelopmentItemWorkflowDetailDTO detail = workflowService.updateNode(type, itemSupport.itemId(args),
                itemSupport.nodeId(args), command);
        return support.result(operation, "研发事项节点字段已更新", Map.of("workflow", detail), REFRESH);
    }
}
