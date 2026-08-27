package com.brad.pms.service;

import com.brad.pms.dto.request.LoginRequest;
import com.brad.pms.dto.response.LoginResponse;
import com.brad.pms.entity.AuthSessionDO;
import com.brad.pms.entity.LoginLogDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.AuthSessionMapper;
import com.brad.pms.mapper.LoginLogMapper;
import com.brad.pms.mapper.UserMapper;
import com.brad.pms.security.JwtTokenProvider;
import com.brad.pms.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {
    @Mock UserMapper userMapper;
    @Mock AuthSessionMapper sessionMapper;
    @Mock LoginLogMapper loginLogMapper;
    @Mock JwtTokenProvider tokenProvider;
    @Mock AuthorizationService authorizationService;
    private AuthService authService;
    private UserDO user;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userMapper, sessionMapper, loginLogMapper, tokenProvider, authorizationService);
        when(authorizationService.effectivePermissionCodes(anyLong())).thenReturn(java.util.List.of("admin:user:read"));
        ReflectionTestUtils.setField(authService, "maxFailedLogins", 5);
        ReflectionTestUtils.setField(authService, "lockMinutes", 15L);
        ReflectionTestUtils.setField(authService, "refreshExpireDays", 30L);
        user = new UserDO();
        user.setId(7L);
        user.setUsername("Brad.Xie");
        user.setUsernameNormalized("brad.xie");
        user.setNameZh("谢斌");
        user.setNickname("谢斌");
        user.setPassword(new BCryptPasswordEncoder().encode("CorrectPassword1!"));
        user.setStatus("ACTIVE");
        user.setFailedLoginCount(0);
        when(userMapper.findByUsernameNormalized(anyString())).thenReturn(user);
        when(userMapper.updateById(any(UserDO.class))).thenReturn(1);
        when(sessionMapper.insert(any(AuthSessionDO.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, AuthSessionDO.class).setId(99L);
            return 1;
        });
        when(loginLogMapper.insert(any(LoginLogDO.class))).thenReturn(1);
        when(tokenProvider.createToken(any())).thenReturn("access");
    }

    @ParameterizedTest
    @ValueSource(strings = {"brad.xie", "Brad.Xie", "BRAD.XIE"})
    void loginUsesNormalizedEnglishName(String loginName) {
        LoginRequest request = new LoginRequest();
        request.setUsername(loginName);
        request.setPassword("CorrectPassword1!");

        LoginResponse response = authService.login(request);

        assertThat(response.getUser().getDisplayName()).isEqualTo("谢斌（Brad.Xie）");
        verify(userMapper).findByUsernameNormalized(loginName.toLowerCase());
    }

    @Test
    void disabledUserCannotRefreshSession() {
        user.setStatus("DISABLED");
        AuthSessionDO session = new AuthSessionDO();
        session.setId(99L);
        session.setUserId(7L);
        session.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(sessionMapper.findByRefreshTokenHash(anyString())).thenReturn(session);

        assertThatThrownBy(() -> authService.refresh("refresh"))
                .hasMessage("账号不可用");
    }

    @Test
    void invalidPasswordPersistsFailureAuditWithoutCredentials() {
        LoginRequest request = new LoginRequest();
        request.setUsername("Brad.Xie");
        request.setPassword("WrongPassword1!");

        assertThatThrownBy(() -> authService.login(request, "10.0.0.8", "test-agent"))
                .hasMessage("用户名或密码错误");

        assertThat(user.getFailedLoginCount()).isEqualTo(1);
        verify(loginLogMapper).insert(argThat(log ->
                "FAILURE".equals(log.getResult())
                        && Long.valueOf(7L).equals(log.getUserId())
                        && "brad.xie".equals(log.getLoginName())
                        && "INVALID_CREDENTIALS".equals(log.getReason())
                        && "10.0.0.8".equals(log.getIp())
                        && "test-agent".equals(log.getUserAgent())));
    }

    @Test
    void successfulLoginPersistsSuccessAudit() {
        LoginRequest request = new LoginRequest();
        request.setUsername("Brad.Xie");
        request.setPassword("CorrectPassword1!");

        authService.login(request, "10.0.0.8", "test-agent");

        verify(loginLogMapper).insert(argThat(log ->
                "SUCCESS".equals(log.getResult())
                        && Long.valueOf(7L).equals(log.getUserId())
                        && "LOGIN_SUCCESS".equals(log.getReason())));
    }

    @Test
    void unknownUsernameStillWritesGenericFailureAudit() {
        when(userMapper.findByUsernameNormalized("unknown.user")).thenReturn(null);
        LoginRequest request = new LoginRequest();
        request.setUsername("Unknown.User");
        request.setPassword("WrongPassword1!");

        assertThatThrownBy(() -> authService.login(request, "10.0.0.9", "test-agent"))
                .hasMessage("用户名或密码错误");

        verify(loginLogMapper).insert(argThat(log ->
                "FAILURE".equals(log.getResult())
                        && log.getUserId() == null
                        && "unknown.user".equals(log.getLoginName())
                        && "INVALID_CREDENTIALS".equals(log.getReason())));
    }
}
