package com.brad.pms.service;

import com.brad.pms.dto.request.PasswordResetRequest;
import com.brad.pms.dto.response.ResetTokenResponse;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.PasswordResetTokenMapper;
import com.brad.pms.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {
    @Mock UserMapper userMapper;
    @Mock PasswordResetTokenMapper tokenMapper;
    @Mock AuthService authService;
    @Mock OperationLogService operationLogService;
    @Mock PasswordResetNotifier notifier;
    @Mock ObjectProvider<PasswordResetNotifier> notifierProvider;

    @Test
    void productionModeSendsResetLinkWithoutReturningRawToken() {
        UserDO user = new UserDO();
        user.setId(7L); user.setUsername("Alex.Zhang"); user.setUsernameNormalized("alex.zhang"); user.setNameZh("张伟");
        when(userMapper.findByUsernameNormalized("alex.zhang")).thenReturn(user);
        when(notifierProvider.getIfAvailable()).thenReturn(notifier);

        PasswordResetService service = new PasswordResetService(userMapper, tokenMapper, authService, operationLogService, notifierProvider);
        ReflectionTestUtils.setField(service, "exposeToken", false);
        PasswordResetRequest request = new PasswordResetRequest(); request.setUsername("ALEX.ZHANG");

        ResetTokenResponse response = service.request(request);

        assertThat(response.getResetUrl()).isEmpty();
        verify(notifier).send(eq(user), contains("/auth/reset-password?token="), any());
    }
}
