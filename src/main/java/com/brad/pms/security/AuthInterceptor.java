package com.brad.pms.security;

import com.brad.pms.entity.UserDO;
import com.brad.pms.entity.AuthSessionDO;
import com.brad.pms.common.enums.UserStatus;
import com.brad.pms.mapper.AuthSessionMapper;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 登录鉴权拦截器：解析 Authorization: Bearer <token>，写入 UserContext
 */
@Slf4j
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final UserMapper userMapper;
    private final AuthSessionMapper authSessionMapper;
    private final AuthorizationService authorizationService;
    private final AiDelegationRoutePolicy aiDelegationRoutePolicy;

    public AuthInterceptor(JwtTokenProvider tokenProvider, UserMapper userMapper,
                           AuthSessionMapper authSessionMapper,
                           AuthorizationService authorizationService) {
        this(tokenProvider, userMapper, authSessionMapper, authorizationService,
                new AiDelegationRoutePolicy());
    }

    public AuthInterceptor(JwtTokenProvider tokenProvider, UserMapper userMapper,
                           AuthSessionMapper authSessionMapper,
                           AuthorizationService authorizationService,
                           AiDelegationRoutePolicy aiDelegationRoutePolicy) {
        this.tokenProvider = tokenProvider;
        this.userMapper = userMapper;
        this.authSessionMapper = authSessionMapper;
        this.authorizationService = authorizationService;
        this.aiDelegationRoutePolicy = aiDelegationRoutePolicy;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod method = (HandlerMethod) handler;
        if (method.hasMethodAnnotation(IgnoreAuth.class)
                || method.getBeanType().isAnnotationPresent(IgnoreAuth.class)) {
            return true;
        }

        String header = request.getHeader("Authorization");
        String delegationToken = request.getHeader("X-PMS-AI-Delegation");
        boolean aiDelegation = delegationToken != null && !delegationToken.isBlank();
        String tokenHeader = aiDelegation ? BEARER_PREFIX + delegationToken : header;
        if (tokenHeader != null && tokenHeader.startsWith(BEARER_PREFIX)) {
            try {
                String token = tokenHeader.substring(BEARER_PREFIX.length());
                LoginUser loginUser = aiDelegation
                        ? tokenProvider.parseAiDelegationToken(token)
                        : tokenProvider.parseToken(token);
                if (aiDelegation && !allowedAiDelegationRoute(request, token)) {
                    writeError(response, HttpServletResponse.SC_FORBIDDEN, "AI 委托令牌没有访问该接口的权限");
                    return false;
                }
                UserDO persistedUser = userMapper.selectById(loginUser.getId());
                if (persistedUser == null) {
                    writeUnauthorized(response, "用户不存在");
                    return false;
                }
                if (!UserStatus.ACTIVE.name().equals(persistedUser.getStatus())) {
                    writeUnauthorized(response, "账号不可用");
                    return false;
                }
                if (loginUser.getSessionId() == null) {
                    writeUnauthorized(response, "登录会话已失效，请重新登录");
                    return false;
                }
                AuthSessionDO session = authSessionMapper.selectById(loginUser.getSessionId());
                if (session == null || session.getUserId() == null
                        || !session.getUserId().equals(persistedUser.getId())
                        || session.getRevokedAt() != null
                        || session.getExpiresAt() == null
                        || !session.getExpiresAt().isAfter(java.time.LocalDateTime.now())) {
                    writeUnauthorized(response, "登录会话已失效，请重新登录");
                    return false;
                }
                // 每次请求从数据库刷新系统角色，确保管理员权限变更即时生效，兼容旧令牌。
                loginUser.setUsername(persistedUser.getUsername());
                loginUser.setNickname(persistedUser.getNickname());
                loginUser.setSystemRole(persistedUser.getSystemRole());
                loginUser.setNameZh(persistedUser.getNameZh());
                loginUser.setDisplayName(com.brad.pms.convertor.Convertors.userDisplayName(persistedUser));
                UserContext.set(loginUser);
                RequirePermission required = method.getMethodAnnotation(RequirePermission.class);
                if (required == null) required = method.getBeanType().getAnnotation(RequirePermission.class);
                if (required != null) authorizationService.require(required.value());
                return true;
            } catch (BusinessException e) {
                writeError(response, e.getCode(), e.getMessage());
                return false;
            } catch (Exception e) {
                log.debug("token 解析失败: {}", e.getClass().getSimpleName());
            }
        }
        writeUnauthorized(response, "未登录或登录已过期");
        return false;
    }

    private boolean allowedAiDelegationRoute(HttpServletRequest request, String token) {
        return aiDelegationRoutePolicy.isAllowed(request, token, tokenProvider);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, message);
    }

    private void writeError(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        String requestId = MDC.get("requestId");
        if (requestId != null) response.setHeader("X-Request-Id", requestId);
        String safeMessage = jsonEscape(message == null ? "请求失败" : message);
        String safeRequestId = requestId == null ? "null" : "\"" + jsonEscape(requestId) + "\"";
        response.getWriter().write("{\"code\":" + code + ",\"msg\":\"" + safeMessage
                + "\",\"data\":null,\"requestId\":" + safeRequestId + "}");
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
