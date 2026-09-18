package com.brad.pms.ai.command.member;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.common.enums.MemberRole;
import com.brad.pms.dto.response.ProjectMemberDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectMemberDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectMemberMapper;
import com.brad.pms.service.MemberService;
import com.brad.pms.service.ProjectPermissionService;
import com.brad.pms.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberCommandTest {

    @Mock MemberService memberService;
    @Mock ProjectPermissionService permissionService;
    @Mock ProjectMemberMapper memberMapper;
    @Mock UserService userService;

    @Test
    void addMemberRequiresAnActiveAccountAndCapturesProjectVersion() {
        when(permissionService.requireProjectManageable(22L, "维护项目成员")).thenReturn(project(4));
        UserDO user = new UserDO();
        user.setId(31L);
        user.setStatus("ACTIVE");
        when(userService.listByIds(List.of(31L))).thenReturn(List.of(user));
        when(memberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        AddProjectMemberCommand command = new AddProjectMemberCommand(memberService, permissionService,
                memberMapper, userService, new ObjectMapper());

        CommandPreview preview = command.preview(new CommandPreviewRequest(CommandName.MEMBER_ADD,
                Map.of("projectId", 22L, "userId", 31L), "project-detail", "v1"));

        assertThat(preview.changes()).singleElement().satisfies(change -> {
            assertThat(change).containsEntry("role", MemberRole.MEMBER.getCode());
            assertThat(change).containsEntry("projectVersion", 4);
        });
        ProjectMemberDO member = new ProjectMemberDO();
        member.setId(51L);
        when(memberService.add(22L, 31L, MemberRole.MEMBER.getCode())).thenReturn(member);
        CommandResult result = command.execute(operation("{\"projectId\":22,\"userId\":31}"));

        verify(memberService).add(22L, 31L, MemberRole.MEMBER.getCode());
        assertThat(result.message()).isEqualTo("项目成员已添加");
    }

    @Test
    void removeMemberChecksTheMemberVersionAndProtectsTheOwner() {
        when(permissionService.requireProjectManageable(22L, "维护项目成员")).thenReturn(project(4));
        ProjectMemberDO member = new ProjectMemberDO();
        member.setId(51L);
        member.setProjectId(22L);
        member.setUserId(31L);
        member.setRole(MemberRole.MEMBER.getCode());
        member.setVersion(2);
        when(memberMapper.selectById(51L)).thenReturn(member);
        RemoveProjectMemberCommand command = new RemoveProjectMemberCommand(memberService, permissionService,
                memberMapper, new ObjectMapper());

        CommandPreview preview = command.preview(new CommandPreviewRequest(CommandName.MEMBER_REMOVE,
                Map.of("projectId", 22L, "memberId", 51L), "project-detail", "v1"));
        assertThat(preview.changes()).singleElement().satisfies(change ->
                assertThat(change).containsEntry("memberVersion", 2));

        CommandResult result = command.execute(operation("{\"projectId\":22,\"memberId\":51}",
                "[{\"projectVersion\":4,\"memberVersion\":2}]"));
        verify(memberService).remove(22L, 51L);
        assertThat(result.message()).isEqualTo("项目成员已移除");
    }

    private static ProjectDO project(int version) {
        ProjectDO project = new ProjectDO();
        project.setId(22L);
        project.setVersion(version);
        return project;
    }

    private static AiOperationDO operation(String arguments) {
        return operation(arguments, "[{\"projectVersion\":4,\"memberVersion\":2}]");
    }

    private static AiOperationDO operation(String arguments, String versions) {
        AiOperationDO operation = new AiOperationDO();
        operation.setId("op-member-1");
        operation.setArgumentsJson(arguments);
        operation.setExpectedVersionsJson(versions);
        return operation;
    }
}
