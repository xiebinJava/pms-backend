package com.brad.pms.ai.command.member;

import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.ai.command.PmsCommandVersionGuard;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectMemberDO;
import com.brad.pms.mapper.ProjectMemberMapper;
import com.brad.pms.service.MemberService;
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
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RemoveProjectMemberCommand implements PmsCommand {

    private static final Set<String> ALLOWED_ARGUMENTS = Set.of("projectId", "memberId");

    private final MemberService memberService;
    private final ProjectPermissionService permissionService;
    private final ProjectMemberMapper memberMapper;
    private final ObjectMapper objectMapper;

    @Override
    public CommandName name() {
        return CommandName.MEMBER_REMOVE;
    }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        CommandArgumentReader.rejectUnknown(request.arguments(), ALLOWED_ARGUMENTS, "member.remove");
        Long projectId = CommandArgumentReader.requiredLong(request.arguments(), "projectId");
        Long memberId = CommandArgumentReader.requiredLong(request.arguments(), "memberId");
        ProjectDO project = permissionService.requireProjectManageable(projectId, "维护项目成员");
        ProjectMemberDO member = requireRemovableMember(projectId, memberId);

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "project-member");
        change.put("action", "remove");
        change.put("projectId", projectId);
        change.put("memberId", memberId);
        change.put("userId", member.getUserId());
        change.put("role", member.getRole());
        change.put("projectVersion", project.getVersion());
        change.put("memberVersion", member.getVersion());
        return new CommandPreview(null, name(), Instant.now().plusSeconds(600), request.contextVersion(),
                List.of("项目负责人不能被移除；如需变更负责人，请使用项目编辑流程"), List.of(change),
                List.of("project-detail", "project-list", "project-dashboard"));
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = readArguments(operation.getArgumentsJson());
        CommandArgumentReader.rejectUnknown(arguments, ALLOWED_ARGUMENTS, "member.remove");
        Long projectId = CommandArgumentReader.requiredLong(arguments, "projectId");
        Long memberId = CommandArgumentReader.requiredLong(arguments, "memberId");
        ProjectDO project = permissionService.requireProjectManageable(projectId, "维护项目成员");
        ProjectMemberDO member = requireRemovableMember(projectId, memberId);
        assertFresh(operation, project.getVersion(), member.getVersion());
        memberService.remove(projectId, memberId);
        return new CommandResult(operation.getId(), "SUCCEEDED", "项目成员已移除", Map.of(
                "memberId", memberId, "projectId", projectId, "userId", member.getUserId()),
                List.of("project-detail", "project-list", "project-dashboard"));
    }

    private ProjectMemberDO requireRemovableMember(Long projectId, Long memberId) {
        ProjectMemberDO member = memberMapper.selectById(memberId);
        if (member == null || !projectId.equals(member.getProjectId())) {
            throw BusinessException.error("成员不存在");
        }
        if (member.getRole() == null || member.getRole() == 0) {
            throw BusinessException.error("项目负责人不可移除");
        }
        return member;
    }

    private void assertFresh(AiOperationDO operation, Integer projectVersion, Integer memberVersion) {
        try {
            List<Map<String, Object>> changes = objectMapper.readValue(operation.getExpectedVersionsJson(), new TypeReference<>() { });
            if (!changes.isEmpty()) {
                Map<String, Object> change = changes.get(0);
                PmsCommandVersionGuard.requireMatch("项目", change.get("projectVersion"), projectVersion);
                PmsCommandVersionGuard.requireMatch("项目成员", change.get("memberVersion"), memberVersion);
            }
        } catch (JsonProcessingException e) {
            throw BusinessException.error("成员移除预览版本信息无效");
        }
    }

    private Map<String, Object> readArguments(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw BusinessException.error("成员移除参数无效");
        }
    }
}
