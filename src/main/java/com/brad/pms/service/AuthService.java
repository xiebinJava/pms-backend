package com.brad.pms.service;

import com.brad.pms.common.enums.UserStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.config.EnterpriseDataMigration;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.request.LoginRequest;
import com.brad.pms.dto.response.LoginResponse;
import com.brad.pms.dto.response.UserDTO;
import com.brad.pms.entity.AuthSessionDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.AuthSessionMapper;
import com.brad.pms.mapper.UserMapper;
import com.brad.pms.security.JwtTokenProvider;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import com.brad.pms.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/** Local account authentication with short-lived access JWTs and revocable refresh sessions. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final AuthSessionMapper authSessionMapper;
    private final JwtTokenProvider tokenProvider;
    private final AuthorizationService authorizationService;

    @Value("${pms.auth.refresh-expire-days:30}")
    private long refreshExpireDays;
    @Value("${pms.auth.max-failed-logins:5}")
    private int maxFailedLogins;
    @Value("${pms.auth.lock-minutes:15}")
    private long lockMinutes;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public LoginResponse login(LoginRequest request) {
        return login(request, null, null);
    }

    @Transactional
    public LoginResponse login(LoginRequest request, String ip, String userAgent) {
        String normalized = EnterpriseDataMigration.normalizeUsername(request.getUsername());
        UserDO user = userMapper.findByUsernameNormalized(normalized);
        if (user == null) throw BusinessException.unauthorized("用户名或密码错误");
        LocalDateTime now = LocalDateTime.now();
        if (UserStatus.DISABLED.name().equals(user.getStatus())) {
            throw BusinessException.unauthorized("账号已停用");
        }
        if (UserStatus.LOCKED.name().equals(user.getStatus())
                && user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw BusinessException.unauthorized("账号已暂时锁定，请稍后重试");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            registerFailure(user, now);
            throw BusinessException.unauthorized("用户名或密码错误");
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setStatus(UserStatus.ACTIVE.name());
        user.setLastLoginAt(now);
        userMapper.updateById(user);

        String refreshToken = randomToken();
        AuthSessionDO session = new AuthSessionDO();
        session.setUserId(user.getId());
        session.setRefreshTokenHash(sha256(refreshToken));
        session.setExpiresAt(now.plusDays(refreshExpireDays));
        session.setIp(ip);
        session.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 500)));
        authSessionMapper.insert(session);

        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getNickname(), user.getSystemRole(),
                user.getNameZh(), Convertors.userDisplayName(user), session.getId());
        String accessToken = tokenProvider.createToken(loginUser);
        UserDTO dto = Convertors.toUser(user);
        dto.setPermissionCodes(authorizationService.effectivePermissionCodes(user.getId()));
        return new LoginResponse(accessToken, refreshToken, dto);
    }

    @Transactional
    public LoginResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw BusinessException.unauthorized("刷新令牌不能为空");
        }
        AuthSessionDO session = authSessionMapper.findByRefreshTokenHash(sha256(refreshToken));
        if (session == null || session.getRevokedAt() != null || session.getExpiresAt() == null
                || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw BusinessException.unauthorized("刷新令牌已失效");
        }
        UserDO user = userMapper.selectById(session.getUserId());
        if (user == null || !UserStatus.ACTIVE.name().equals(user.getStatus())) {
            throw BusinessException.unauthorized("账号不可用");
        }
        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getNickname(), user.getSystemRole(),
                user.getNameZh(), Convertors.userDisplayName(user), session.getId());
        UserDTO dto = Convertors.toUser(user);
        dto.setPermissionCodes(authorizationService.effectivePermissionCodes(user.getId()));
        return new LoginResponse(tokenProvider.createToken(loginUser), refreshToken, dto);
    }

    @Transactional
    public void revokeAllSessions(Long userId, String reason) {
        if (userId != null) authSessionMapper.revokeAllByUserId(userId, reason == null ? "REVOKED" : reason);
    }

    public UserDTO me() {
        Long userId = UserContext.userId();
        UserDO user = userMapper.selectById(userId);
        if (user == null) throw BusinessException.unauthorized("用户不存在");
        UserDTO dto = Convertors.toUser(user);
        dto.setPermissionCodes(authorizationService.effectivePermissionCodes(user.getId()));
        return dto;
    }

    private void registerFailure(UserDO user, LocalDateTime now) {
        int failures = user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount();
        failures++;
        user.setFailedLoginCount(failures);
        if (failures >= maxFailedLogins) {
            user.setStatus(UserStatus.LOCKED.name());
            user.setLockedUntil(now.plusMinutes(lockMinutes));
        }
        userMapper.updateById(user);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法计算令牌摘要", e);
        }
    }
}
