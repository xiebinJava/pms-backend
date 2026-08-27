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
                            @Value("${pms.jwt.expire-hours}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 3600_000L;
    }

    public String createToken(LoginUser user) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("nickname", user.getNickname())
                .claim("systemRole", user.getSystemRole())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expireMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public LoginUser parseToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        LoginUser user = new LoginUser();
        user.setId(Long.valueOf(claims.getSubject()));
        user.setUsername(claims.get("username", String.class));
        user.setNickname(claims.get("nickname", String.class));
        user.setSystemRole(claims.get("systemRole", Integer.class));
        return user;
    }
}
