package com.brad.pms.ai.command.follower;

import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.ai.command.PmsCommandVersionGuard;
import com.brad.pms.common.enums.UserStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.service.FollowerService;
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

/**
 * Adds one project follower. Followers are additive here on purpose: an edit
 * must never replace the whole follower list with what the caller happened to
 * mention in a sentence.
 */
@Component
@RequiredArgsConstructor
public class AddProjectFollowerCommand implements PmsCommand {

    private static final Set<String> ALLOWED_ARGUMENTS = Set.of("projectId", "userId");

    private static final List<String> REFRESH_SCOPES =
            List.of("project-detail", "project-list", "project-dashboard");

    private final FollowerService followerService;
    private final ProjectPermissionService permissionService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Override
    public CommandName name() {
        return CommandName.FOLLOWER_ADD;
    }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> arguments = request.arguments();
        CommandArgumentReader.rejectUnknown(arguments, ALLOWED_ARGUMENTS, "follower.add");
        Long projectId = CommandArgumentReader.requiredLong(arguments, "projectId");
        Long userId = CommandArgumentReader.requiredLong(arguments, "userId");
        ProjectDO project = permissionService.requireProjectManageable(projectId, "维护项目关注人");
        requireActiveUser(userId);
        if (followerService.listUserIds(projectId).contains(userId)) {
            throw BusinessException.error("该用户已是项目关注人");
        }

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "project-follower");
        change.put("action", "add");
        change.put("projectId", projectId);
        change.put("userId", userId);
        change.put("projectVersion", project.getVersion());
        return new CommandPreview(null, name(), Instant.now().plusSeconds(600), request.contextVersion(),
                List.of("只新增这一位关注人，其他关注人保持不变"), List.of(change), REFRESH_SCOPES);
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = readArguments(operation.getArgumentsJson());
        CommandArgumentReader.rejectUnknown(arguments, ALLOWED_ARGUMENTS, "follower.add");
        Long projectId = CommandArgumentReader.requiredLong(arguments, "projectId");
        Long userId = CommandArgumentReader.requiredLong(arguments, "userId");
        ProjectDO project = permissionService.requireProjectManageable(projectId, "维护项目关注人");
        requireActiveUser(userId);
        assertFresh(operation, project.getVersion());
        followerService.add(projectId, userId);
        return new CommandResult(operation.getId(), "SUCCEEDED", "关注人已添加", Map.of(
                "projectId", projectId, "userId", userId), REFRESH_SCOPES);
    }

    private void requireActiveUser(Long userId) {
        UserDO user = userService.listByIds(List.of(userId)).stream().findFirst().orElse(null);
        if (user == null) throw BusinessException.notFound("账号不存在");
        if (!UserStatus.ACTIVE.name().equals(user.getStatus())) {
            throw BusinessException.error("只能选择已激活的账号");
        }
    }

    private void assertFresh(AiOperationDO operation, Integer projectVersion) {
        try {
            List<Map<String, Object>> changes = objectMapper.readValue(
                    operation.getExpectedVersionsJson(), new TypeReference<>() { });
            if (!changes.isEmpty()) {
                PmsCommandVersionGuard.requireMatch("项目", changes.get(0).get("projectVersion"), projectVersion);
            }
        } catch (JsonProcessingException e) {
            throw BusinessException.error("关注人添加预览版本信息无效");
        }
    }

    private Map<String, Object> readArguments(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw BusinessException.error("关注人添加参数无效");
        }
    }
}
