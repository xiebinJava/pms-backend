package com.brad.pms.ai.command.node;

import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.common.enums.NodeStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.response.ProjectNodeDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.service.NodeService;
import com.brad.pms.service.ProjectPermissionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CompleteNodeCommand implements PmsCommand {

    private final NodeService nodeService;
    private final ProjectPermissionService permissionService;
    private final ObjectMapper objectMapper;

    @Override
    public CommandName name() {
        return CommandName.NODE_COMPLETE;
    }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Long projectId = CommandArgumentReader.requiredLong(request.arguments(), "projectId");
        Long nodeId = CommandArgumentReader.requiredLong(request.arguments(), "nodeId");
        ProjectDO project = permissionService.requireProjectReadable(projectId);
        ProjectNodeDO node = permissionService.requireCompletableNode(projectId, nodeId);

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "node");
        change.put("action", "complete");
        change.put("projectId", projectId);
        change.put("nodeId", nodeId);
        change.put("nodeName", node.getName());
        change.put("fromStatus", node.getStatus());
        change.put("toStatus", NodeStatus.COMPLETED.getCode());
        change.put("projectVersion", project.getVersion());
        change.put("nodeVersion", node.getVersion());
        return new CommandPreview(null, name(), Instant.now().plusSeconds(600), request.contextVersion(),
                List.of("完成节点前仍会执行 PMS 的任务、必填字段和工作台组件校验"),
                List.of(change), List.of("project-detail", "project-dashboard", "task-board"));
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = readArguments(operation.getArgumentsJson());
        Long projectId = CommandArgumentReader.requiredLong(arguments, "projectId");
        Long nodeId = CommandArgumentReader.requiredLong(arguments, "nodeId");
        ProjectDO project = permissionService.requireProjectReadable(projectId);
        ProjectNodeDO node = permissionService.requireNode(projectId, nodeId);
        assertFresh(operation, project.getVersion(), node.getVersion());
        permissionService.requireCompletableNode(projectId, nodeId);
        List<ProjectNodeDTO> nodes = nodeService.complete(projectId, nodeId);
        return new CommandResult(operation.getId(), "SUCCEEDED", "节点已完成", Map.of(
                "projectId", projectId,
                "nodeId", nodeId,
                "nodes", nodes), List.of("project-detail", "project-dashboard", "task-board"));
    }

    private void assertFresh(AiOperationDO operation, Integer projectVersion, Integer nodeVersion) {
        try {
            List<Map<String, Object>> changes = objectMapper.readValue(operation.getExpectedVersionsJson(), new TypeReference<>() { });
            if (changes.isEmpty()) return;
            Map<String, Object> change = changes.get(0);
            NodeCommandVersionGuard.requireMatch("项目", change.get("projectVersion"), projectVersion);
            NodeCommandVersionGuard.requireMatch("节点", change.get("nodeVersion"), nodeVersion);
        } catch (JsonProcessingException e) {
            throw BusinessException.error("节点完成预览版本信息无效");
        }
    }

    private Map<String, Object> readArguments(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw BusinessException.error("节点完成参数无效");
        }
    }
}
