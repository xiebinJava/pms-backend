package com.brad.pms.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Central route and scope policy for short-lived AI delegation tokens.
 *
 * <p>Legacy {@code /ai/*} routes keep their existing scopes while the
 * integration facade uses the narrower {@code pms:*} scopes. Keeping this
 * mapping in one place prevents new integration endpoints from accidentally
 * bypassing the delegation boundary.</p>
 */
public final class AiDelegationRoutePolicy {

    public boolean isAllowed(HttpServletRequest request, String token, JwtTokenProvider tokenProvider) {
        String path = normalizePath(request);
        String method = request.getMethod();

        if (path.startsWith("/integration/ai/v1/")
                && !tokenProvider.isAiDelegationToken(token)) {
            return false;
        }

        if ("POST".equalsIgnoreCase(method) && "/ai/context/inspect".equals(path)) {
            return tokenProvider.hasAiDelegationScope(token, "ai:context:read");
        }
        if ("POST".equalsIgnoreCase(method) && "/ai/commands/preview".equals(path)) {
            return tokenProvider.hasAiDelegationScope(token, "ai:command:preview");
        }
        if ("POST".equalsIgnoreCase(method) && "/ai/query/tasks".equals(path)) {
            return tokenProvider.hasAiDelegationScope(token, "ai:query:read");
        }
        if ("GET".equalsIgnoreCase(method) && "/ai/commands".equals(path)) {
            return tokenProvider.hasAiDelegationScope(token, "ai:command:preview");
        }

        if ("GET".equalsIgnoreCase(method) && "/integration/ai/v1/capabilities".equals(path)) {
            return tokenProvider.hasAiDelegationScope(token, "pms:query:read");
        }
        if ("POST".equalsIgnoreCase(method) && "/integration/ai/v1/query".equals(path)) {
            return tokenProvider.hasAiDelegationScope(token, "pms:query:read");
        }
        if ("GET".equalsIgnoreCase(method)
                && path.matches("/integration/ai/v1/context/[^/]+/[^/]+")) {
            return tokenProvider.hasAiDelegationScope(token, "pms:query:read");
        }
        if ("GET".equalsIgnoreCase(method)
                && path.matches("/integration/ai/v1/resources/(project|requirement|topic|story)/[^/]+")) {
            return tokenProvider.hasAiDelegationScope(token, "pms:query:read");
        }
        if ("POST".equalsIgnoreCase(method)
                && "/integration/ai/v1/operations/execute".equals(path)) {
            return tokenProvider.hasAiDelegationScope(token, "pms:command:execute");
        }
        return false;
    }

    private String normalizePath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (path.startsWith("/api/")) {
            path = path.substring("/api".length());
        }
        return path;
    }
}
