package com.brad.pms.security;

import com.brad.pms.entity.PermissionDO;
import com.brad.pms.mapper.PermissionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    @Mock PermissionMapper permissionMapper;

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void feedbackManagerPermissionIncludesReadAndWriteCapabilities() {
        UserContext.set(new LoginUser(7L, "manager", "反馈管理员", 0));
        PermissionDO manage = new PermissionDO();
        manage.setCode(PermissionCode.FEEDBACK_MANAGE);
        when(permissionMapper.findLiveByUserId(7L)).thenReturn(List.of(manage));

        AuthorizationService service = new AuthorizationService(permissionMapper);

        assertThat(service.has(PermissionCode.FEEDBACK_MANAGE)).isTrue();
        assertThat(service.has(PermissionCode.FEEDBACK_READ)).isTrue();
        assertThat(service.has(PermissionCode.FEEDBACK_WRITE)).isTrue();
        assertThat(service.has(PermissionCode.PROJECT_READ)).isFalse();
    }
}
