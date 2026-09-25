package com.brad.pms.controller;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.integration.dsh.api.DshAuthorizationCodeExchangeRequest;
import com.brad.pms.integration.dsh.api.DshTokenExchangeResponse;
import com.brad.pms.integration.dsh.service.DshAuthorizationCodeService;
import com.brad.pms.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DshAuthorizationCodeExchangeControllerTest {

    @Test
    void exchangesOneTimeCodeWithServiceKey() {
        DshAuthorizationCodeService service = mock(DshAuthorizationCodeService.class);
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        DshAuthorizationCodeService.ConsumedAuthorizationCode consumed =
                new DshAuthorizationCodeService.ConsumedAuthorizationCode(
                        7L, 9L, "dsh-1", "project_assistant", List.of("pms:project:read"));
        when(service.consume(any())).thenReturn(consumed);
        when(tokenProvider.createDshDelegationToken(
                eq(7L), eq(9L), eq("dsh-1"), eq("project_assistant"), any()))
                .thenReturn("short-lived-token");
        DshAuthorizationCodeExchangeController controller =
                new DshAuthorizationCodeExchangeController(tokenProvider, service, "server-key");

        ResponseResult<DshTokenExchangeResponse> response = controller.exchange(
                "server-key", new DshAuthorizationCodeExchangeRequest(
                        "one-time-code", "dsh-1", "project_assistant", Set.of("pms:project:read")));

        assertThat(response.getData().token()).isEqualTo("short-lived-token");
        assertThat(response.getData().audience()).isEqualTo("dsh-pms");
        assertThat(response.getData().expiresInSeconds()).isEqualTo(120);
        verify(service).consume(any());
    }

    @Test
    void rejectsUnknownServiceKeyBeforeConsumingCode() {
        DshAuthorizationCodeService service = mock(DshAuthorizationCodeService.class);
        DshAuthorizationCodeExchangeController controller =
                new DshAuthorizationCodeExchangeController(mock(JwtTokenProvider.class), service, "server-key");

        assertThatThrownBy(() -> controller.exchange("wrong-key", new DshAuthorizationCodeExchangeRequest(
                "one-time-code", "dsh-1", "project_assistant", Set.of("pms:project:read"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PMS_DSH_SERVICE_AUTH_FAILED");
    }
}
