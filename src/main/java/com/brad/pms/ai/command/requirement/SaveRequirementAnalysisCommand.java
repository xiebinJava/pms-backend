package com.brad.pms.ai.command.requirement;

import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.ai.command.PmsCommandSupport;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.RequirementReceivingAnalysisSaveCmd;
import com.brad.pms.dto.response.RequirementReceivingAnalysisDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.service.RequirementReceivingAnalysisService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.NullNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Saves a partial AI-produced patch into the requirement analysis component. */
@Component
@RequiredArgsConstructor
public class SaveRequirementAnalysisCommand implements PmsCommand {

    private static final Set<String> ALLOWED = Set.of("requirementId", "nodeId", "version", "patch");
    private static final List<String> REFRESH = List.of("requirement-detail");

    private final RequirementReceivingAnalysisService analysisService;
    private final PmsCommandSupport support;
    private final ObjectMapper objectMapper;

    @Override
    public CommandName name() {
        return CommandName.REQUIREMENT_ANALYSIS_SAVE;
    }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> arguments = request.arguments();
        support.rejectUnknown(arguments, ALLOWED, name().code());
        Long requirementId = CommandArgumentReader.requiredLong(arguments, "requirementId");
        Long nodeId = CommandArgumentReader.requiredLong(arguments, "nodeId");
        Integer version = CommandArgumentReader.requiredInteger(arguments, "version");
        Map<String, JsonNode> patch = support.fieldValues(arguments, "patch");
        if (patch == null) throw BusinessException.error("参数 patch 必须是对象");

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "requirement");
        change.put("action", "analysis.save");
        change.put("requirementId", requirementId);
        change.put("nodeId", nodeId);
        change.put("version", version);
        change.put("patch", arguments.get("patch"));
        return support.preview(request, name(), "保存需求分析成果", change,
                List.of("仅保存需求接收与分析组件成果，不自动推进流程节点"), REFRESH);
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = support.readArguments(operation, name().code());
        support.rejectUnknown(arguments, ALLOWED, name().code());
        Long requirementId = CommandArgumentReader.requiredLong(arguments, "requirementId");
        Long nodeId = CommandArgumentReader.requiredLong(arguments, "nodeId");
        Integer version = CommandArgumentReader.requiredInteger(arguments, "version");
        Map<String, JsonNode> patch = support.fieldValues(arguments, "patch");
        if (patch == null) throw BusinessException.error("参数 patch 必须是对象");

        RequirementReceivingAnalysisDTO current = analysisService.get(requirementId, nodeId);
        ObjectNode merged = current == null || current.getState() == null
                ? objectMapper.createObjectNode()
                : asObject(objectMapper.valueToTree(current.getState()));
        patch.forEach((key, value) -> merged.set(key, value == null ? NullNode.getInstance() : value.deepCopy()));

        RequirementReceivingAnalysisSaveCmd command = new RequirementReceivingAnalysisSaveCmd();
        command.setVersion(version);
        command.setState(merged);
        RequirementReceivingAnalysisDTO saved = analysisService.save(requirementId, nodeId, command);
        return support.result(operation, "需求分析成果已保存", Map.of(
                "analysis", saved,
                "requirementId", requirementId,
                "nodeId", nodeId), REFRESH);
    }

    private ObjectNode asObject(JsonNode state) {
        if (state != null && state.isObject()) return (ObjectNode) state;
        throw BusinessException.error("需求接收分析内容格式不正确");
    }
}
