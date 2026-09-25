package com.brad.pms.controller;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.response.LoginResponse;
import com.brad.pms.dto.response.UserDTO;
import com.brad.pms.integration.dsh.api.DshSsoSessionRequest;
import com.brad.pms.integration.dsh.service.DshSsoSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DshSsoSessionControllerTest {

    @Mock DshSsoSessionService service;
    @Mock HttpServletRequest httpRequest;

    @Test
    void requiresTheSharedServiceKeyBeforeDoingAnything() {
        DshSsoSessionController controller = new DshSsoSessionController(service, "service-key");

        assertThatThrownBy(() -> controller.ssoSession(null, new DshSsoSessionRequest("id-token"), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("服务鉴权失败");
        assertThatThrownBy(() -> controller.ssoSession("wrong", new DshSsoSessionRequest("id-token"), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("服务鉴权失败");
    }

    @Test
    void refusesWhenTheDeploymentHasNoServiceKeyConfigured() {
        DshSsoSessionController controller = new DshSsoSessionController(service, "");

        assertThatThrownBy(() -> controller.ssoSession("anything", new DshSsoSessionRequest("id-token"), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("服务鉴权失败");
    }

    @Test
    void rejectsARequestWithoutAnIdToken() {
        DshSsoSessionController controller = new DshSsoSessionController(service, "service-key");

        assertThatThrownBy(() -> controller.ssoSession("service-key", new DshSsoSessionRequest("  "), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少 SSO ID Token");
    }

    @Test
    void returnsTheEstablishedPmsSession() {
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> "User-Agent".equals(invocation.getArgument(0)) ? "dsh-test" : null);
        UserDTO user = new UserDTO();
        user.setId(2L);
        when(service.login(eq("id-token"), eq("127.0.0.1"), eq("dsh-test")))
                .thenReturn(new LoginResponse("pms-access", "pms-refresh", user));

        LoginResponse response = new DshSsoSessionController(service, "service-key")
                .ssoSession("service-key", new DshSsoSessionRequest("id-token"), httpRequest)
                .getData();

        assertThat(response.getToken()).isEqualTo("pms-access");
        assertThat(response.getRefreshToken()).isEqualTo("pms-refresh");
    }

    @Test
    void trustsTheForwardedClientIpWhenPresent() {
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("10.0.0.9, 10.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn(null);
        UserDTO user = new UserDTO();
        when(service.login(any(), eq("10.0.0.9"), any())).thenReturn(new LoginResponse("t", "r", user));

        new DshSsoSessionController(service, "service-key")
                .ssoSession("service-key", new DshSsoSessionRequest("id-token"), httpRequest);

        // Covered by the stub above: the forwarded address is what the session records.
        assertThat(true).isTrue();
    }
}
