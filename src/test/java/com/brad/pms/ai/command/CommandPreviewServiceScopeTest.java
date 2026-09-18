package com.brad.pms.ai.command;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        when(registry.require(CommandName.NODE_COMPLETE)).thenReturn(mock(PmsCommand.class));

        CommandPreviewService service = new CommandPreviewService(registry, operationService);

        assertThatThrownBy(() -> service.preview(new CommandPreviewRequest(
                        CommandName.NODE_COMPLETE, Map.of("projectId", 22L, "nodeId", 7L), "ctx", "v1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有该命令的权限范围");
    }
}
