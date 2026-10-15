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
import java.util.List;
import java.util.Set;

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

    /** A short-lived, scope-limited token for an AI client. */
    public String createAiDelegationToken(Long userId, Long sessionId, Set<String> scopes) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + 120_000L))
                .claim("sid", sessionId)
                .claim("kind", "AI_DELEGATION")
                .claim("scope", scopes == null ? List.of() : scopes.stream().sorted().toList())
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public LoginUser parseAiDelegationToken(String token) {
        Claims claims = parseClaims(token);
        if (!"AI_DELEGATION".equals(claims.get("kind", String.class))) {
            throw new IllegalArgumentException("不是 AI 委托令牌");
        }
        LoginUser user = loginUserFromClaims(claims);
        return user;
    }

    /** Identifies an AI delegation token without exposing parser failures. */
    public boolean isAiDelegationToken(String token) {
        try {
            parseAiDelegationToken(token);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public boolean hasAiDelegationScope(String token, String scope) {
        Claims claims = parseClaims(token);
        if (!"AI_DELEGATION".equals(claims.get("kind", String.class))) return false;
        Object raw = claims.get("scope");
        if (!(raw instanceof List<?> scopes)) return false;
        return scopes.stream().anyMatch(scope::equals);
    }

    public LoginUser parseToken(String token) {
        return loginUserFromClaims(parseClaims(token));
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private LoginUser loginUserFromClaims(Claims claims) {
        LoginUser user = new LoginUser();
        user.setId(Long.valueOf(claims.getSubject()));
        Number sessionId = claims.get("sid", Number.class);
        user.setSessionId(sessionId == null ? null : sessionId.longValue());
        if ("AI_DELEGATION".equals(claims.get("kind", String.class))) {
            Object rawScopes = claims.get("scope");
            if (rawScopes instanceof java.util.List<?> scopes) {
                user.setDelegationScopes(scopes.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList());
            } else {
                user.setDelegationScopes(java.util.List.of());
            }
        }
        return user;
    }
}
