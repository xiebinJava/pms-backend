package com.brad.pms.ai.command.follower;

import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.service.FollowerService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowerCommandTest {

    @Mock FollowerService followerService;
    @Mock ProjectPermissionService permissionService;
    @Mock UserService userService;

    @Test
    void addFollowerRequiresAnActiveAccountAndCapturesProjectVersion() {
        when(permissionService.requireProjectManageable(22L, "维护项目关注人")).thenReturn(project(4));
        when(userService.listByIds(List.of(31L))).thenReturn(List.of(activeUser(31L)));
        when(followerService.listUserIds(22L)).thenReturn(List.of(9L));
        AddProjectFollowerCommand command = new AddProjectFollowerCommand(
                followerService, permissionService, userService, new ObjectMapper());

        CommandPreview preview = command.preview(new CommandPreviewRequest(CommandName.FOLLOWER_ADD,
                Map.of("projectId", 22L, "userId", 31L), "project-detail", "v1"));

        assertThat(preview.command()).isEqualTo(CommandName.FOLLOWER_ADD);
        assertThat(preview.changes()).singleElement().satisfies(change -> {
            assertThat(change).containsEntry("entity", "project-follower");
            assertThat(change).containsEntry("action", "add");
            assertThat(change).containsEntry("userId", 31L);
            assertThat(change).containsEntry("projectVersion", 4);
        });

        CommandResult result = command.execute(operation("{\"projectId\":22,\"userId\":31}"));

        verify(followerService).add(22L, 31L);
        assertThat(result.message()).isEqualTo("关注人已添加");
    }

    @Test
    void addFollowerRejectsSomeoneWhoAlreadyFollowsTheProject() {
        when(permissionService.requireProjectManageable(22L, "维护项目关注人")).thenReturn(project(4));
        when(userService.listByIds(List.of(31L))).thenReturn(List.of(activeUser(31L)));
        when(followerService.listUserIds(22L)).thenReturn(List.of(31L));
        AddProjectFollowerCommand command = new AddProjectFollowerCommand(
                followerService, permissionService, userService, new ObjectMapper());

        assertThatThrownBy(() -> command.preview(new CommandPreviewRequest(CommandName.FOLLOWER_ADD,
                Map.of("projectId", 22L, "userId", 31L), "project-detail", "v1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该用户已是项目关注人");
    }

    @Test
    void removeFollowerRejectsSomeoneWhoIsNotFollowing() {
        when(permissionService.requireProjectManageable(22L, "维护项目关注人")).thenReturn(project(4));
        when(followerService.listUserIds(22L)).thenReturn(List.of(9L));
        RemoveProjectFollowerCommand command = new RemoveProjectFollowerCommand(
                followerService, permissionService, new ObjectMapper());

        assertThatThrownBy(() -> command.preview(new CommandPreviewRequest(CommandName.FOLLOWER_REMOVE,
                Map.of("projectId", 22L, "userId", 31L), "project-detail", "v1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该用户不是项目关注人");
        verify(followerService, org.mockito.Mockito.never()).remove(22L, 31L);
    }

    @Test
    void removeFollowerExecutesASingleRemovalAndRejectsAStalePreview() {
        when(permissionService.requireProjectManageable(22L, "维护项目关注人")).thenReturn(project(4));
        when(followerService.listUserIds(22L)).thenReturn(List.of(31L, 9L));
        RemoveProjectFollowerCommand command = new RemoveProjectFollowerCommand(
                followerService, permissionService, new ObjectMapper());

        CommandPreview preview = command.preview(new CommandPreviewRequest(CommandName.FOLLOWER_REMOVE,
                Map.of("projectId", 22L, "userId", 31L), "project-detail", "v1"));
        assertThat(preview.changes()).singleElement().satisfies(change -> {
            assertThat(change).containsEntry("action", "remove");
            assertThat(change).containsEntry("projectVersion", 4);
        });

        assertThatThrownBy(() -> command.execute(operationWithVersions(
                "{\"projectId\":22,\"userId\":31}", "[{\"projectVersion\":3}]")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目已发生变化，请重新生成预览");

        CommandResult result = command.execute(operation("{\"projectId\":22,\"userId\":31}"));
        verify(followerService).remove(22L, 31L);
        assertThat(result.message()).isEqualTo("关注人已移除");
    }

    private static ProjectDO project(int version) {
        ProjectDO project = new ProjectDO();
        project.setId(22L);
        project.setVersion(version);
        return project;
    }

    private static UserDO activeUser(Long id) {
        UserDO user = new UserDO();
        user.setId(id);
        user.setStatus("ACTIVE");
        return user;
    }

    private static AiOperationDO operation(String arguments) {
        return operationWithVersions(arguments, "[{\"projectVersion\":4}]");
    }

    private static AiOperationDO operationWithVersions(String arguments, String versions) {
        AiOperationDO operation = new AiOperationDO();
        operation.setId("op-follower-1");
        operation.setArgumentsJson(arguments);
        operation.setExpectedVersionsJson(versions);
        return operation;
    }
}
