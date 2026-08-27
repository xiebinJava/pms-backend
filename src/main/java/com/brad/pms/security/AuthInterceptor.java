package com.brad.pms.security;

import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录鉴权拦截器：解析 Authorization: Bearer <token>，写入 UserContext
 */
@Slf4j
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final UserMapper userMapper;

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
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                LoginUser loginUser = tokenProvider.parseToken(header.substring(BEARER_PREFIX.length()));
                UserDO persistedUser = userMapper.selectById(loginUser.getId());
                if (persistedUser == null) {
                    writeUnauthorized(response, "用户不存在");
                    return false;
                }
                // 每次请求从数据库刷新系统角色，确保管理员权限变更即时生效，兼容旧令牌。
                loginUser.setUsername(persistedUser.getUsername());
                loginUser.setNickname(persistedUser.getNickname());
                loginUser.setSystemRole(persistedUser.getSystemRole());
                UserContext.set(loginUser);
                return true;
            } catch (Exception e) {
                log.debug("token 解析失败: {}", e.getMessage());
            }
        }
        writeUnauthorized(response, "未登录或登录已过期");
        return false;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"" + message + "\",\"data\":null}");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
