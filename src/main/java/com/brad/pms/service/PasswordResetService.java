package com.brad.pms.service;

import com.brad.pms.config.EnterpriseDataMigration;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.PasswordResetConfirmRequest;
import com.brad.pms.dto.request.PasswordResetRequest;
import com.brad.pms.dto.response.ResetTokenResponse;
import com.brad.pms.entity.PasswordResetTokenDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.PasswordResetTokenMapper;
import com.brad.pms.mapper.UserMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class PasswordResetService {
    private final UserMapper userMapper;
    private final PasswordResetTokenMapper tokenMapper;
    private final AuthService authService;
    private final OperationLogService operationLogService;
    private final ObjectProvider<PasswordResetNotifier> notifierProvider;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    @Value("${pms.auth.password-reset-expose-token:false}")
    private boolean exposeToken;

    public PasswordResetService(UserMapper userMapper, PasswordResetTokenMapper tokenMapper,
                                AuthService authService, OperationLogService operationLogService,
                                ObjectProvider<PasswordResetNotifier> notifierProvider) {
        this.userMapper = userMapper;
        this.tokenMapper = tokenMapper;
        this.authService = authService;
        this.operationLogService = operationLogService;
        this.notifierProvider = notifierProvider;
    }

    @Transactional
    public ResetTokenResponse request(PasswordResetRequest request) {
        UserDO user = userMapper.findByUsernameNormalized(EnterpriseDataMigration.normalizeUsername(request.getUsername()));
        // Return a generic response for unknown names to avoid account enumeration.
        if (user == null) return new ResetTokenResponse("", LocalDateTime.now().plusMinutes(30).toString());
        String raw = randomToken();
        PasswordResetTokenDO token = new PasswordResetTokenDO();
        token.setUserId(user.getId()); token.setTokenHash(AuthService.sha256(raw)); token.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        tokenMapper.insert(token);
        String resetUrl = "/auth/reset-password?token=" + raw;
        PasswordResetNotifier notifier = notifierProvider.getIfAvailable();
        if (notifier != null) {
            notifier.send(user, resetUrl, token.getExpiresAt());
        } else if (!exposeToken) {
            throw BusinessException.error("未配置密码重置通知器，暂不能发出重置链接");
        }
        return new ResetTokenResponse(exposeToken ? resetUrl : "", token.getExpiresAt().toString());
    }

    @Transactional
    public void confirm(PasswordResetConfirmRequest request) {
        PasswordResetTokenDO token = tokenMapper.findPending(AuthService.sha256(request.getToken()));
        if (token == null || token.getExpiresAt().isBefore(LocalDateTime.now())) throw BusinessException.error("重置链接已失效");
        UserDO user = userMapper.selectById(token.getUserId());
        if (user == null) throw BusinessException.error("账号不存在");
        user.setPassword(encoder.encode(request.getPassword())); user.setPasswordChangedAt(LocalDateTime.now()); user.setFailedLoginCount(0); user.setLockedUntil(null); user.setStatus("ACTIVE");
        userMapper.updateById(user); tokenMapper.markUsed(token.getId()); authService.revokeAllSessions(user.getId(), "PASSWORD_RESET");
        operationLogService.record("PASSWORD_RESET", "USER", user.getId(), null, java.util.Map.of("status", "ACTIVE"));
    }

    private String randomToken() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
}
