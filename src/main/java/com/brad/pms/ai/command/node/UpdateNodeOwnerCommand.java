package com.brad.pms.ai.command.node;

import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.ai.command.PmsCommandVersionGuard;
import com.brad.pms.common.enums.NodeStatus;
import com.brad.pms.common.enums.UserStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodeOwnerUpdateCmd;
import com.brad.pms.dto.response.ProjectNodeDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.service.NodeService;
import com.brad.pms.service.ProjectPermissionService;
import com.brad.pms.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UpdateNodeOwnerCommand implements PmsCommand {

    private static final Set<String> ALLOWED_ARGUMENTS = Set.of("projectId", "nodeId", "ownerId");

    private final NodeService nodeService;
    private final ProjectPermissionService permissionService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Override
    public CommandName name() {
        return CommandName.NODE_OWNER_UPDATE;
    }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> arguments = request.arguments();
        CommandArgumentReader.rejectUnknown(arguments, ALLOWED_ARGUMENTS, "node.owner.update");
        Long projectId = CommandArgumentReader.requiredLong(arguments, "projectId");
        Long nodeId = CommandArgumentReader.requiredLong(arguments, "nodeId");
        Long ownerId = CommandArgumentReader.requiredLong(arguments, "ownerId");
        ProjectDO project = permissionService.requireProjectManageable(projectId, "分配节点负责人");
        ProjectNodeDO node = permissionService.requireNode(projectId, nodeId);
        ensureWritable(node);
        requireActiveUser(ownerId);

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "node");
        change.put("action", "owner.update");
        change.put("projectId", projectId);
        change.put("nodeId", nodeId);
        change.put("nodeName", node.getName());
        change.put("fromOwnerId", node.getOwnerId());
        change.put("toOwnerId", ownerId);
        change.put("projectVersion", project.getVersion());
        change.put("nodeVersion", node.getVersion());
        return new CommandPreview(null, name(), Instant.now().plusSeconds(600), request.contextVersion(),
                List.of("执行时会自动确保该用户加入项目成员"), List.of(change),
                List.of("project-detail", "project-dashboard", "task-board"));
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = readArguments(operation.getArgumentsJson());
        CommandArgumentReader.rejectUnknown(arguments, ALLOWED_ARGUMENTS, "node.owner.update");
        Long projectId = CommandArgumentReader.requiredLong(arguments, "projectId");
        Long nodeId = CommandArgumentReader.requiredLong(arguments, "nodeId");
        Long ownerId = CommandArgumentReader.requiredLong(arguments, "ownerId");
        ProjectDO project = permissionService.requireProjectManageable(projectId, "分配节点负责人");
        ProjectNodeDO node = permissionService.requireNode(projectId, nodeId);
        assertFresh(operation, project.getVersion(), node.getVersion());

        NodeOwnerUpdateCmd command = new NodeOwnerUpdateCmd();
        command.setVersion(node.getVersion());
        command.setOwnerId(ownerId);
        ProjectNodeDTO updated = nodeService.updateOwner(projectId, nodeId, command);
        return new CommandResult(operation.getId(), "SUCCEEDED", "节点负责人已更新", Map.of("node", updated),
                List.of("project-detail", "project-dashboard", "task-board"));
    }

    private void ensureWritable(ProjectNodeDO node) {
        if (NodeStatus.isReadOnly(node.getStatus())) {
            throw BusinessException.forbidden("节点已锁定，回滚后才可以分配节点负责人");
        }
    }

    private void requireActiveUser(Long userId) {
        UserDO user = userService.listByIds(List.of(userId)).stream().findFirst().orElse(null);
        if (user == null) throw BusinessException.notFound("账号不存在");
        if (!UserStatus.ACTIVE.name().equals(user.getStatus())) {
            throw BusinessException.error("只能选择已激活的账号");
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
            throw BusinessException.error("节点负责人预览版本信息无效");
        }
    }

    private Map<String, Object> readArguments(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw BusinessException.error("节点负责人参数无效");
        }
    }
}
