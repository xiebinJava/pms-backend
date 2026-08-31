package com.brad.pms.security;

import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small in-process guard for credential and upload endpoints. It is deliberately
 * conservative: the client IP is always part of the key, while a username
 * parameter/header is included when a caller supplies one. Account-level
 * failed-login locking remains enforced by AuthService.
 */
public class RequestRateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestRateLimitInterceptor.class);

    private static final String RETRY_AFTER = "Retry-After";
    private static final String MESSAGE = "请求过于频繁，请稍后重试";

    private final int capacity;
    private final long windowMillis;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RequestRateLimitInterceptor(int capacity, Duration window, Clock clock) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
        this.capacity = capacity;
        this.windowMillis = window.toMillis();
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!isSensitivePost(request)) return true;

        long now = clock.millis();
        String key = rateKey(request);
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || now >= current.resetAtMillis) return new Window(now + windowMillis, 1);
            if (current.count >= capacity) {
                current.count++;
                return current;
            }
            current.count++;
            return current;
        });
        evictExpired(now);
        if (window.count > capacity) {
            return reject(response, Math.max(1, (window.resetAtMillis - now + 999) / 1000));
        }
        return true;
    }

    private boolean isSensitivePost(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return false;
        String path = normalizedPath(request);
        return "/auth/login".equals(path)
                || "/auth/refresh".equals(path)
                || "/auth/activate".equals(path)
                || "/auth/password-reset/request".equals(path)
                || "/auth/password-reset/confirm".equals(path)
                || "/admin/users/invite".equals(path)
                || path.startsWith("/admin/import/")
                || "/projects/images".equals(path);
    }

    private String normalizedPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (path.startsWith("/api/")) path = path.substring("/api".length());
        return path;
    }

    private String rateKey(HttpServletRequest request) {
        String account = request.getParameter("username");
        if (account == null || account.isBlank()) account = request.getHeader("X-Login-Username");
        if (account == null || account.isBlank()) account = "-";
        return request.getRemoteAddr() + "|" + normalizedPath(request) + "|" + account.trim().toLowerCase();
    }

    private boolean reject(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        log.warn("security_event=RATE_LIMIT_BLOCKED requestId={} path={}",
                MDC.get("requestId"), "sensitive-endpoint");
        response.setStatus(429);
        response.setHeader(RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType("application/json;charset=UTF-8");
        String requestId = MDC.get("requestId");
        String requestIdJson = requestId == null ? "null" : "\"" + escape(requestId) + "\"";
        response.getWriter().write("{\"code\":429,\"msg\":\"" + MESSAGE
                + "\",\"data\":null,\"requestId\":" + requestIdJson + "}");
        return false;
    }

    private void evictExpired(long now) {
        if (windows.size() <= 10_000) return;
        windows.entrySet().removeIf(entry -> entry.getValue().resetAtMillis <= now);
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private static final class Window {
        private final long resetAtMillis;
        private int count;

        private Window(long resetAtMillis, int count) {
            this.resetAtMillis = resetAtMillis;
            this.count = count;
        }
    }
}
