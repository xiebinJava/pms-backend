package com.brad.pms.ai.connector;

import com.brad.pms.ai.command.AiOperationService;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomaticCommandExecutionServiceTest {

    @Mock
    AiOperationService operationService;

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void rejectsAutomaticExecutionWithoutAnAuthenticatedUser() {
        AutomaticCommandExecutionService service = new AutomaticCommandExecutionService(operationService);

        assertThatThrownBy(() -> service.execute(request(CommandName.PROJECT_CREATE, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未登录");
    }

    @Test
    void normalizesGlobalOperationsWithoutRequiringPageContext() {
        UserContext.set(new LoginUser(7L, "admin", "管理员"));
        CommandResult expected = new CommandResult("op-1", "SUCCEEDED", "项目已创建", Map.of(), List.of());
        when(operationService.executeAutomatically(eq(7L), any(), eq("idem-1"), eq("pms-cli"), eq("req-1")))
                .thenReturn(expected);

        AutomaticCommandExecutionService service = new AutomaticCommandExecutionService(operationService);
        CommandResult actual = service.execute(request(CommandName.PROJECT_CREATE, null));

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<CommandPreviewRequest> captor = ArgumentCaptor.forClass(CommandPreviewRequest.class);
        verify(operationService).executeAutomatically(eq(7L), captor.capture(), eq("idem-1"), eq("pms-cli"), eq("req-1"));
        assertThat(captor.getValue().contextId()).isEqualTo("global:pms");
        assertThat(captor.getValue().contextVersion()).isEqualTo("v1");
    }

    @Test
    void requiresConcreteContextForNodeOperations() {
        UserContext.set(new LoginUser(7L, "admin", "管理员"));
        AutomaticCommandExecutionService service = new AutomaticCommandExecutionService(operationService);

        assertThatThrownBy(() -> service.execute(request(CommandName.NODE_COMPLETE, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("节点操作必须提供具体上下文");
    }

    @Test
    void requiresConcreteContextForIterationPlanOperations() {
        UserContext.set(new LoginUser(7L, "admin", "管理员"));
        AutomaticCommandExecutionService service = new AutomaticCommandExecutionService(operationService);

        assertThatThrownBy(() -> service.execute(request(CommandName.ITERATION_PLAN_CREATE, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("节点操作必须提供具体上下文");
    }

    private AutomaticOperationRequest request(CommandName command, OperationContext context) {
        return new AutomaticOperationRequest(
                command,
                Map.of("name", "测试"),
                context == null ? null : context.id(),
                context == null ? null : context.version(),
                null,
                null,
                "idem-1",
                "pms-cli",
                "req-1");
    }

    private record OperationContext(String id, String version) {
    }
}
