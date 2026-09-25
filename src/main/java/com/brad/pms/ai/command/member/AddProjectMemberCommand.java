package com.brad.pms.ai.command.member;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.ai.command.PmsCommandVersionGuard;
import com.brad.pms.common.enums.MemberRole;
import com.brad.pms.common.enums.UserStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectMemberDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectMemberMapper;
import com.brad.pms.service.MemberService;
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
public class AddProjectMemberCommand implements PmsCommand {

    private static final Set<String> ALLOWED_ARGUMENTS = Set.of("projectId", "userId", "role");

    private final MemberService memberService;
    private final ProjectPermissionService permissionService;
    private final ProjectMemberMapper memberMapper;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Override
    public CommandName name() {
        return CommandName.MEMBER_ADD;
    }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> arguments = request.arguments();
        CommandArgumentReader.rejectUnknown(arguments, ALLOWED_ARGUMENTS, "member.add");
        Long projectId = CommandArgumentReader.requiredLong(arguments, "projectId");
        Long userId = CommandArgumentReader.requiredLong(arguments, "userId");
        int role = CommandArgumentReader.optionalInteger(arguments, "role", MemberRole.MEMBER.getCode());
        requireAssignableRole(role);
        ProjectDO project = permissionService.requireProjectManageable(projectId, "维护项目成员");
        requireActiveUser(userId);
        ProjectMemberDO existing = memberMapper.selectOne(new LambdaQueryWrapper<ProjectMemberDO>()
                .eq(ProjectMemberDO::getProjectId, projectId)
                .eq(ProjectMemberDO::getUserId, userId)
                .last("LIMIT 1"));
        if (existing != null) throw BusinessException.error("该用户已在项目中");

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "project-member");
        change.put("action", "add");
        change.put("projectId", projectId);
        change.put("userId", userId);
        change.put("role", role);
        change.put("projectVersion", project.getVersion());
        return new CommandPreview(null, name(), Instant.now().plusSeconds(600), request.contextVersion(),
                List.of("成员添加会沿用 PMS 的项目成员权限规则"), List.of(change),
                List.of("project-detail", "project-list", "project-dashboard"));
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = readArguments(operation.getArgumentsJson());
        CommandArgumentReader.rejectUnknown(arguments, ALLOWED_ARGUMENTS, "member.add");
        Long projectId = CommandArgumentReader.requiredLong(arguments, "projectId");
        Long userId = CommandArgumentReader.requiredLong(arguments, "userId");
        int role = CommandArgumentReader.optionalInteger(arguments, "role", MemberRole.MEMBER.getCode());
        requireAssignableRole(role);
        ProjectDO project = permissionService.requireProjectManageable(projectId, "维护项目成员");
        assertFresh(operation, project.getVersion());
        ProjectMemberDO member = memberService.add(projectId, userId, role);
        return new CommandResult(operation.getId(), "SUCCEEDED", "项目成员已添加", Map.of(
                "memberId", member.getId(), "projectId", projectId, "userId", userId, "role", role),
                List.of("project-detail", "project-list", "project-dashboard"));
    }

    private void requireAssignableRole(int role) {
        if (role != MemberRole.ADMIN.getCode() && role != MemberRole.MEMBER.getCode()) {
            throw BusinessException.error("成员角色只能是管理员（1）或成员（2）");
        }
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
            List<Map<String, Object>> changes = objectMapper.readValue(operation.getExpectedVersionsJson(), new TypeReference<>() { });
            if (!changes.isEmpty()) {
                PmsCommandVersionGuard.requireMatch("项目", changes.get(0).get("projectVersion"), projectVersion);
            }
        } catch (JsonProcessingException e) {
            throw BusinessException.error("成员添加预览版本信息无效");
        }
    }

    private Map<String, Object> readArguments(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw BusinessException.error("成员添加参数无效");
        }
    }
}
