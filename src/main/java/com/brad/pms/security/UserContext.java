package com.brad.pms.security;

import com.brad.pms.common.exception.BusinessException;

/**
 * 当前登录用户上下文（ThreadLocal 用户上下文）
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long userId() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw BusinessException.unauthorized("未登录");
        }
        return user.getId();
    }

    public static String username() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUsername();
    }

    public static String nickname() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getNickname();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
