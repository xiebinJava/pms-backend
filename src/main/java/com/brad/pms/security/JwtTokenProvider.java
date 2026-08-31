package com.brad.pms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 生成与解析（轻量登录方案，便于开源）
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expireMillis;

    public JwtTokenProvider(@Value("${pms.jwt.secret}") String secret,
                            @Value("${pms.auth.access-expire-minutes:30}") long accessExpireMinutes) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("PMS_JWT_SECRET 至少需要 32 字节，请通过环境变量配置随机密钥");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = accessExpireMinutes * 60_000L;
    }

    public String createToken(LoginUser user) {
        return createAccessToken(user.getId(), user.getSessionId());
    }

    public String createAccessToken(Long userId, Long sessionId) {
        Date now = new Date();
        var builder = Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expireMillis));
        if (sessionId != null) builder.claim("sid", sessionId);
        return builder.signWith(key, SignatureAlgorithm.HS256).compact();
    }

    public LoginUser parseToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        LoginUser user = new LoginUser();
        user.setId(Long.valueOf(claims.getSubject()));
        Number sessionId = claims.get("sid", Number.class);
        user.setSessionId(sessionId == null ? null : sessionId.longValue());
        return user;
    }
}
