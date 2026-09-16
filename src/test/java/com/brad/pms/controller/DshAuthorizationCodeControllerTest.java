package com.brad.pms.controller;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.integration.dsh.api.DshAuthorizationCodeIssueRequest;
import com.brad.pms.integration.dsh.api.DshAuthorizationCodeIssueResponse;
import com.brad.pms.integration.dsh.service.DshAuthorizationCodeService;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DshAuthorizationCodeControllerTest {

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void issuesCodeForCurrentPmsSessionOnly() {
        DshAuthorizationCodeService service = mock(DshAuthorizationCodeService.class);
        when(service.issue(eq(7L), eq(9L), eq(new DshAuthorizationCodeIssueRequest(
                "dsh-1", "project_assistant", Set.of("pms:project:read")))))
                .thenReturn(new DshAuthorizationCodeIssueResponse("code", 90, List.of("pms:project:read")));
        UserContext.set(new LoginUser(7L, "alex", "张伟", 1, null, null, 9L));
        DshAuthorizationCodeController controller = new DshAuthorizationCodeController(service);

        ResponseResult<DshAuthorizationCodeIssueResponse> response = controller.issue(
                new DshAuthorizationCodeIssueRequest(
                        "dsh-1", "project_assistant", Set.of("pms:project:read")));

        assertThat(response.getData().authorizationCode()).isEqualTo("code");
        verify(service).issue(eq(7L), eq(9L), eq(new DshAuthorizationCodeIssueRequest(
                "dsh-1", "project_assistant", Set.of("pms:project:read"))));
    }

    @Test
    void refusesToIssueWithoutPmsLoginContext() {
        DshAuthorizationCodeController controller = new DshAuthorizationCodeController(
                mock(DshAuthorizationCodeService.class));

        assertThatThrownBy(() -> controller.issue(new DshAuthorizationCodeIssueRequest(
                "dsh-1", "project_assistant", Set.of("pms:project:read"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PMS_DSH_SESSION_INVALID");
    }
}
