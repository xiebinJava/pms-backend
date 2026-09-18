package com.brad.pms.controller;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.integration.dsh.api.DshTokenExchangeRequest;
import com.brad.pms.integration.dsh.api.DshTokenExchangeResponse;
import com.brad.pms.security.JwtTokenProvider;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DshTokenExchangeControllerTest {

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void exchangesCurrentPmsSessionOnlyWithServerKey() {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        when(tokenProvider.createDshDelegationToken(eq(7L), eq(9L), eq("dsh-1"), eq("project_assistant"), any()))
                .thenReturn("short-lived-dsh-token");
        UserContext.set(new LoginUser(7L, "alex", "张伟", 1, null, null, 9L));
        DshTokenExchangeController controller = new DshTokenExchangeController(tokenProvider, "server-key");

        ResponseResult<DshTokenExchangeResponse> response = controller.exchange(
                "server-key",
                new DshTokenExchangeRequest("dsh-1", "project_assistant", Set.of("pms:project:read")));

        assertThat(response.getData().token()).isEqualTo("short-lived-dsh-token");
        assertThat(response.getData().audience()).isEqualTo("dsh-pms");
        assertThat(response.getData().scopes()).containsExactly("pms:project:read");
        verify(tokenProvider).createDshDelegationToken(
                eq(7L), eq(9L), eq("dsh-1"), eq("project_assistant"), eq(Set.of("pms:project:read")));
    }

    @Test
    void rejectsBrowserOrUnknownServiceCaller() {
        UserContext.set(new LoginUser(7L, "alex", "张伟", 1, null, null, 9L));
        DshTokenExchangeController controller = new DshTokenExchangeController(mock(JwtTokenProvider.class), "server-key");

        assertThatThrownBy(() -> controller.exchange(null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(ResponseResult.FORBIDDEN));
        assertThatThrownBy(() -> controller.exchange("wrong-key", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(ResponseResult.FORBIDDEN));
    }

    @Test
    void rejectsScopeEscalation() {
        UserContext.set(new LoginUser(7L, "alex", "张伟", 1, null, null, 9L));
        DshTokenExchangeController controller = new DshTokenExchangeController(mock(JwtTokenProvider.class), "server-key");

        assertThatThrownBy(() -> controller.exchange(
                "server-key", new DshTokenExchangeRequest("dsh-1", "project_assistant", Set.of("pms:project:write"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(ResponseResult.FORBIDDEN));
    }
}
