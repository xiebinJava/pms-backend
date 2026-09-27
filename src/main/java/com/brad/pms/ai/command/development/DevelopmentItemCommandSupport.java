package com.brad.pms.ai.command.development;

import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.PmsCommandSupport;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.DevelopmentItemNodeUpdateCmd;
import com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO;
import com.brad.pms.dto.response.DevelopmentItemWorkflowNodeDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.service.DevelopmentItemWorkflowService;
import com.brad.pms.workflow.DevelopmentItemType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DevelopmentItemCommandSupport {

    private final DevelopmentItemWorkflowService workflowService;
    private final PmsCommandSupport commandSupport;

    public DevelopmentItemType itemType(Map<String, Object> arguments) {
        return DevelopmentItemType.from(CommandArgumentReader.requiredText(arguments, "itemType"));
    }

    public Long itemId(Map<String, Object> arguments) {
        return CommandArgumentReader.requiredLong(arguments, "itemId");
    }

    public Long nodeId(Map<String, Object> arguments) {
        return CommandArgumentReader.requiredLong(arguments, "nodeId");
    }

    public DevelopmentItemWorkflowNodeDTO node(Map<String, Object> arguments) {
        DevelopmentItemType itemType = itemType(arguments);
        DevelopmentItemWorkflowDetailDTO detail = workflowService.detail(itemType, itemId(arguments));
        return detail.getNodes().stream()
                .filter(node -> node.getId().equals(nodeId(arguments)))
                .findFirst()
                .orElseThrow(() -> BusinessException.notFound("流程节点不存在"));
    }

    public Integer requiredVersion(Map<String, Object> arguments) {
        Integer version = CommandArgumentReader.optionalInteger(arguments, "version", null);
        if (version == null) throw BusinessException.error("参数 version 必须是整数");
        return version;
    }

    public void requireCurrentVersion(DevelopmentItemWorkflowNodeDTO node, Integer expected) {
        if (!java.util.Objects.equals(node.getVersion(), expected)) {
            throw BusinessException.conflict("流程节点已被其他人修改，请刷新后重试");
        }
    }

    public DevelopmentItemNodeUpdateCmd baseUpdate(DevelopmentItemWorkflowNodeDTO node, Integer version) {
        DevelopmentItemNodeUpdateCmd command = new DevelopmentItemNodeUpdateCmd();
        command.setOwnerId(node.getOwnerId());
        command.setStartDate(node.getStartDate());
        command.setEndDate(node.getEndDate());
        command.setFieldValues(node.getFieldValues() == null ? Map.of() : new LinkedHashMap<>(node.getFieldValues()));
        command.setVersion(version);
        return command;
    }

    public Map<String, Object> change(String action, Map<String, Object> arguments,
                                      DevelopmentItemWorkflowNodeDTO node) {
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "development-item-node");
        change.put("action", action);
        change.put("itemType", itemType(arguments).name());
        change.put("itemId", itemId(arguments));
        change.put("nodeId", nodeId(arguments));
        change.put("nodeKey", node.getNodeKey());
        change.put("nodeVersion", node.getVersion());
        return change;
    }

    public PmsCommandSupport commandSupport() {
        return commandSupport;
    }
}
