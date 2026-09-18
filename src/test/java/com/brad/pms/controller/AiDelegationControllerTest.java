package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.security.JwtTokenProvider;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiDelegationControllerTest {

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void normalDelegationIncludesReadOnlyTaskQueryScope() {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        when(tokenProvider.createAiDelegationToken(eq(7L), eq(null), anySet()))
                .thenReturn("delegation-token");
        UserContext.set(new LoginUser(7L, "alex", "张伟"));

        ResponseResult<AiDelegationController.DelegationResponse> response =
                new AiDelegationController(tokenProvider).issue();

        assertThat(response.getData().scopes()).containsExactly(
                "ai:command:preview", "ai:context:read", "ai:query:read");
    }
}
