package com.brad.pms.ai.command;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommandPreviewServiceScopeTest {

    @Mock PmsCommandRegistry registry;
    @Mock AiOperationService operationService;

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void rejectsWorkflowPreviewWhenDelegationTokenLacksWorkflowWriteScope() {
        LoginUser user = new LoginUser(7L, "alex", "张伟");
        user.setDelegationScopes(List.of("pms:command:preview"));
        UserContext.set(user);

        CommandPreviewService service = new CommandPreviewService(registry, operationService);

        assertThatThrownBy(() -> service.preview(new CommandPreviewRequest(
                        CommandName.NODE_COMPLETE, Map.of("projectId", 22L, "nodeId", 7L), "ctx", "v1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有该命令的权限范围");
    }

    @Test
    void allowsWorkflowPreviewWhenTheDelegationTokenHoldsEveryDeclaredScope() {
        LoginUser user = new LoginUser(7L, "dsh", "DSH");
        user.setDelegationScopes(List.of("pms:command:preview", "pms:command:execute", "pms:workflow:write"));
        UserContext.set(user);
        PmsCommand command = mock(PmsCommand.class);
        CommandPreview proposal = new CommandPreview(null, CommandName.NODE_COMPLETE,
                Instant.now().plusSeconds(60), "v1", List.of(), List.of(), List.of("project-detail"));
        when(registry.require(CommandName.NODE_COMPLETE)).thenReturn(command);
        when(command.preview(any(CommandPreviewRequest.class))).thenReturn(proposal);
        when(operationService.persistPreview(eq(7L), any(CommandPreviewRequest.class), eq(proposal)))
                .thenReturn(proposal);

        CommandPreview result = new CommandPreviewService(registry, operationService).preview(
                new CommandPreviewRequest(CommandName.NODE_COMPLETE,
                        Map.of("projectId", 22L, "nodeId", 7L), "ctx", "v1"));

        assertThat(result).isSameAs(proposal);
    }
}
