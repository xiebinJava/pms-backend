package com.brad.pms.ai.command.node;

import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.ai.command.PmsCommandVersionGuard;
import com.brad.pms.common.ProjectScheduleRange;
import com.brad.pms.common.enums.NodeStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodeScheduleUpdateCmd;
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
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UpdateNodeScheduleCommand implements PmsCommand {

    private static final Set<String> ALLOWED_ARGUMENTS = Set.of("projectId", "nodeId", "startDate", "endDate");

    private final NodeService nodeService;
    private final ProjectPermissionService permissionService;
    private final ObjectMapper objectMapper;

    @Override
    public CommandName name() {
        return CommandName.NODE_SCHEDULE_UPDATE;
    }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> arguments = request.arguments();
        CommandArgumentReader.rejectUnknown(arguments, ALLOWED_ARGUMENTS, "node.schedule.update");
        Long projectId = CommandArgumentReader.requiredLong(arguments, "projectId");
        Long nodeId = CommandArgumentReader.requiredLong(arguments, "nodeId");
        if (!arguments.containsKey("startDate") && !arguments.containsKey("endDate")) {
            throw BusinessException.error("节点排期至少需要提供 startDate 或 endDate");
        }
        ProjectDO project = permissionService.requireProjectReadable(projectId);
        ProjectNodeDO node = permissionService.requireManageableNode(projectId, nodeId, "编辑节点排期");
        ensureWritable(node);
        LocalDate startDate = resolveDate(arguments, "startDate", node.getStartDate());
        LocalDate endDate = resolveDate(arguments, "endDate", node.getEndDate());
        validateDates(startDate, endDate);

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "node");
        change.put("action", "schedule.update");
        change.put("projectId", projectId);
        change.put("nodeId", nodeId);
        change.put("nodeName", node.getName());
        change.put("fromStartDate", node.getStartDate());
        change.put("fromEndDate", node.getEndDate());
        change.put("toStartDate", startDate);
        change.put("toEndDate", endDate);
        change.put("projectVersion", project.getVersion());
        change.put("nodeVersion", node.getVersion());
        return new CommandPreview(null, name(), Instant.now().plusSeconds(600), request.contextVersion(),
                List.of("未提供的日期会保留原值；显式传 null 可清空对应日期"), List.of(change),
                List.of("project-detail", "project-dashboard", "task-board"));
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = readArguments(operation.getArgumentsJson());
        CommandArgumentReader.rejectUnknown(arguments, ALLOWED_ARGUMENTS, "node.schedule.update");
        Long projectId = CommandArgumentReader.requiredLong(arguments, "projectId");
        Long nodeId = CommandArgumentReader.requiredLong(arguments, "nodeId");
        ProjectDO project = permissionService.requireProjectReadable(projectId);
        ProjectNodeDO node = permissionService.requireNode(projectId, nodeId);
        assertFresh(operation, project.getVersion(), node.getVersion());

        NodeScheduleUpdateCmd command = new NodeScheduleUpdateCmd();
        command.setVersion(node.getVersion());
        command.setStartDate(resolveDate(arguments, "startDate", node.getStartDate()));
        command.setEndDate(resolveDate(arguments, "endDate", node.getEndDate()));
        validateDates(command.getStartDate(), command.getEndDate());
        ProjectNodeDTO updated = nodeService.updateSchedule(projectId, nodeId, command);
        return new CommandResult(operation.getId(), "SUCCEEDED", "节点排期已更新", Map.of("node", updated),
                List.of("project-detail", "project-dashboard", "task-board"));
    }

    private void ensureWritable(ProjectNodeDO node) {
        if (NodeStatus.isReadOnly(node.getStatus())) {
            throw BusinessException.forbidden("节点已锁定，回滚后才可以编辑节点排期");
        }
    }

    private LocalDate resolveDate(Map<String, Object> arguments, String key, LocalDate current) {
        return arguments.containsKey(key) ? CommandArgumentReader.optionalDate(arguments, key) : current;
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (!ProjectScheduleRange.isOrdered(startDate, endDate)) {
            throw BusinessException.error("节点排期开始日期不能晚于结束日期");
        }
    }

    private void assertFresh(AiOperationDO operation, Integer projectVersion, Integer nodeVersion) {
        try {
            List<Map<String, Object>> changes = objectMapper.readValue(operation.getExpectedVersionsJson(), new TypeReference<>() { });
            if (!changes.isEmpty()) {
                Map<String, Object> change = changes.get(0);
                PmsCommandVersionGuard.requireMatch("项目", change.get("projectVersion"), projectVersion);
                PmsCommandVersionGuard.requireMatch("节点", change.get("nodeVersion"), nodeVersion);
            }
        } catch (JsonProcessingException e) {
            throw BusinessException.error("节点排期预览版本信息无效");
        }
    }

    private Map<String, Object> readArguments(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw BusinessException.error("节点排期参数无效");
        }
    }
}
