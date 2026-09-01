package com.brad.pms.service;

import com.brad.pms.auth.AuthProviderCatalog;
import com.brad.pms.auth.LdapAuthProvider;
import com.brad.pms.auth.OidcAuthProvider;
import com.brad.pms.dto.request.LoginRequest;
import com.brad.pms.dto.request.PasswordChangeRequest;
import com.brad.pms.dto.response.LoginResponse;
import com.brad.pms.entity.AuthSessionDO;
import com.brad.pms.entity.LoginLogDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.AuthSessionMapper;
import com.brad.pms.mapper.LoginLogMapper;
import com.brad.pms.mapper.UserMapper;
import com.brad.pms.security.JwtTokenProvider;
import com.brad.pms.security.AuthorizationService;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.ArgumentMatchers;
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
    @Mock AuthProviderCatalog authProviderCatalog;
    @Mock OidcAuthProvider oidcAuthProvider;
    @Mock LdapAuthProvider ldapAuthProvider;
    private AuthService authService;
    private UserDO user;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userMapper, sessionMapper, loginLogMapper, tokenProvider, authorizationService,
                authProviderCatalog, oidcAuthProvider, ldapAuthProvider);
        when(authorizationService.effectivePermissionCodes(anyLong())).thenReturn(java.util.List.of("admin:user:read"));
        ReflectionTestUtils.setField(authService, "maxFailedLogins", 5);
        ReflectionTestUtils.setField(authService, "lockMinutes", 15L);
        ReflectionTestUtils.setField(authService, "refreshExpireDays", 30L);
        user = new UserDO();
        user.setId(7L);
        user.setUsername("Alex.Zhang");
        user.setUsernameNormalized("alex.zhang");
        user.setNameZh("张伟");
        user.setNickname("张伟");
        user.setPassword(new BCryptPasswordEncoder().encode("CorrectPassword1!"));
        user.setStatus("ACTIVE");
        user.setFailedLoginCount(0);
        when(userMapper.findByUsernameNormalized(anyString())).thenReturn(user);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(userMapper.updateById(any(UserDO.class))).thenReturn(1);
        when(sessionMapper.insert(any(AuthSessionDO.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, AuthSessionDO.class).setId(99L);
            return 1;
        });
        when(loginLogMapper.insert(any(LoginLogDO.class))).thenReturn(1);
        when(tokenProvider.createToken(any())).thenReturn("access");
    }

    @ParameterizedTest
    @ValueSource(strings = {"alex.zhang", "Alex.Zhang", "ALEX.ZHANG"})
    void loginUsesNormalizedEnglishName(String loginName) {
        LoginRequest request = new LoginRequest();
        request.setUsername(loginName);
        request.setPassword("CorrectPassword1!");

        LoginResponse response = authService.login(request);

        assertThat(response.getUser().getDisplayName()).isEqualTo("张伟（Alex.Zhang）");
        verify(userMapper).findByUsernameNormalized(loginName.toLowerCase());
    }

    @Test
    void loginUsesNormalizedEmailAsPrimaryIdentity() {
        user.setEmail("Alex.Zhang@Example.com");
        user.setEmailNormalized("alex.zhang@example.com");
        when(userMapper.findByEmailNormalized("alex.zhang@example.com")).thenReturn(user);

        LoginRequest request = new LoginRequest();
        request.setEmail("  ALEX.ZHANG@EXAMPLE.COM ");
        request.setPassword("CorrectPassword1!");

        LoginResponse response = authService.login(request);

        assertThat(response.getUser().getEmail()).isEqualTo("Alex.Zhang@Example.com");
        verify(userMapper).findByEmailNormalized("alex.zhang@example.com");
        verify(userMapper, never()).findByUsernameNormalized(anyString());
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
        request.setUsername("Alex.Zhang");
        request.setPassword("WrongPassword1!");

        assertThatThrownBy(() -> authService.login(request, "10.0.0.8", "test-agent"))
                .hasMessage("邮箱或密码错误");

        assertThat(user.getFailedLoginCount()).isEqualTo(1);
        verify(loginLogMapper).insert(ArgumentMatchers.<LoginLogDO>argThat(log ->
                "FAILURE".equals(log.getResult())
                        && Long.valueOf(7L).equals(log.getUserId())
                        && "alex.zhang".equals(log.getLoginName())
                        && "INVALID_CREDENTIALS".equals(log.getReason())
                        && "10.0.0.8".equals(log.getIp())
                        && "test-agent".equals(log.getUserAgent())));
    }

    @Test
    void successfulLoginPersistsSuccessAudit() {
        LoginRequest request = new LoginRequest();
        request.setUsername("Alex.Zhang");
        request.setPassword("CorrectPassword1!");

        authService.login(request, "10.0.0.8", "test-agent");

        verify(loginLogMapper).insert(ArgumentMatchers.<LoginLogDO>argThat(log ->
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
                .hasMessage("邮箱或密码错误");

        verify(loginLogMapper).insert(ArgumentMatchers.<LoginLogDO>argThat(log ->
                "FAILURE".equals(log.getResult())
                        && log.getUserId() == null
                        && "unknown.user".equals(log.getLoginName())
                && "INVALID_CREDENTIALS".equals(log.getReason())));
    }

    @Test
    void refreshRotatesRefreshTokenAndPersistsNewSession() {
        AuthSessionDO session = new AuthSessionDO();
        session.setId(99L);
        session.setUserId(7L);
        session.setRefreshTokenHash(AuthService.sha256("refresh"));
        session.setExpiresAt(LocalDateTime.now().plusDays(1));
        session.setIp("10.0.0.1");
        session.setUserAgent("old-agent");
        when(sessionMapper.findByRefreshTokenHash(anyString())).thenReturn(session);
        when(sessionMapper.revokeForRotation(eq(99L), eq(7L), anyString(), eq("REFRESH_ROTATED"))).thenReturn(1);
        when(sessionMapper.insert(any(AuthSessionDO.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, AuthSessionDO.class).setId(100L);
            return 1;
        });
        when(tokenProvider.createToken(any(LoginUser.class))).thenReturn("rotated-access");

        LoginResponse response = authService.refresh("refresh", "10.0.0.2", "new-agent");

        assertThat(response.getAccessToken()).isEqualTo("rotated-access");
        assertThat(response.getRefreshToken()).isNotEqualTo("refresh").isNotBlank();
        verify(sessionMapper).revokeForRotation(eq(99L), eq(7L), eq(AuthService.sha256("refresh")), eq("REFRESH_ROTATED"));
        verify(sessionMapper).insert(ArgumentMatchers.<AuthSessionDO>argThat(next -> next.getId().equals(100L)
                && next.getUserId().equals(7L)
                && "10.0.0.2".equals(next.getIp())
                && "new-agent".equals(next.getUserAgent())));
    }

    @Test
    void passwordChangeUpdatesHashAndRevokesSessions() {
        UserContext.set(new LoginUser(7L, "Alex.Zhang", "张伟", 0, "张伟", "张伟（Alex.Zhang）", 99L));
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setCurrentPassword("CorrectPassword1!");
        request.setNewPassword("NewCorrectPassword2!");

        authService.changePassword(request);

        assertThat(new BCryptPasswordEncoder().matches("NewCorrectPassword2!", user.getPassword())).isTrue();
        verify(sessionMapper).revokeAllByUserId(7L, "PASSWORD_CHANGED");
        UserContext.clear();
    }

    @Test
    void currentSessionCanBeRevokedWithoutRevokingOtherSessions() {
        when(sessionMapper.revokeById(99L, 7L, "USER_LOGOUT")).thenReturn(1);

        authService.revokeSession(7L, 99L, "USER_LOGOUT");

        verify(sessionMapper).revokeById(99L, 7L, "USER_LOGOUT");
        verify(sessionMapper, never()).revokeAllByUserId(anyLong(), anyString());
    }

    @Test
    void oidcLoginLinksAnExistingActiveUser() {
        user.setEmailNormalized("alex.zhang@example.com");
        when(oidcAuthProvider.exchange("code-1", "state-1"))
                .thenReturn(new com.brad.pms.auth.AuthenticatedIdentity("alex.zhang@example.com", "alex.zhang@example.com", "oidc"));
        when(userMapper.findByEmailNormalized("alex.zhang@example.com")).thenReturn(user);

        LoginResponse response = authService.loginOidc("code-1", "state-1", "10.0.0.8", "test-agent");

        assertThat(response.getAccessToken()).isEqualTo("access");
        verify(loginLogMapper).insert(ArgumentMatchers.<LoginLogDO>argThat(log ->
                "SUCCESS".equals(log.getResult()) && "OIDC_LOGIN_SUCCESS".equals(log.getReason())));
    }

    @Test
    void oidcLoginDoesNotCreateMissingUsers() {
        when(oidcAuthProvider.exchange("code-1", "state-1"))
                .thenReturn(new com.brad.pms.auth.AuthenticatedIdentity("unknown@example.com", "unknown@example.com", "oidc"));
        when(userMapper.findByEmailNormalized("unknown@example.com")).thenReturn(null);

        assertThatThrownBy(() -> authService.loginOidc("code-1", "state-1", "10.0.0.8", "test-agent"))
                .hasMessage("账号未开通，请联系管理员邀请");
        verify(sessionMapper, never()).insert(any(AuthSessionDO.class));
    }
}
