package com.brad.pms.ai.command;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PmsCommandScopeGuardTest {

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void rejectsAPmsDelegationTokenThatLacksTheCommandsDomainScope() {
        delegation(List.of("pms:command:preview"));

        assertThatThrownBy(() -> PmsCommandScopeGuard.requireScope(CommandName.NODE_COMPLETE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有该命令的权限范围");
    }

    @Test
    void allowsAPmsDelegationTokenThatHoldsEveryDeclaredScope() {
        delegation(List.of("pms:command:preview", "pms:command:execute", "pms:workflow:write"));

        assertThatCode(() -> PmsCommandScopeGuard.requireScope(CommandName.NODE_COMPLETE))
                .doesNotThrowAnyException();
    }

    @Test
    void leavesLegacyAiDelegationTokensUntouched() {
        delegation(List.of("ai:command:preview"));

        assertThatCode(() -> PmsCommandScopeGuard.requireScope(CommandName.NODE_COMPLETE))
                .doesNotThrowAnyException();
    }

    @Test
    void leavesPlainUserSessionsUntouched() {
        LoginUser user = new LoginUser(7L, "alex", "张伟");
        UserContext.set(user);

        assertThatCode(() -> PmsCommandScopeGuard.requireScope(CommandName.NODE_COMPLETE))
                .doesNotThrowAnyException();
    }

    private static void delegation(List<String> scopes) {
        LoginUser user = new LoginUser(7L, "dsh", "DSH");
        user.setDelegationScopes(scopes);
        UserContext.set(user);
    }
}
