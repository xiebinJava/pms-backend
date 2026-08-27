package com.brad.pms.security;

import com.brad.pms.common.enums.SystemRole;
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

    /**
     * 用于只读权限计算：内部初始化或公开读取场景没有登录上下文时返回 null。
     * 任何写操作仍必须使用 {@link #userId()} 强制校验登录状态。
     */
    public static Long userIdOrNull() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getId();
    }

    public static String username() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUsername();
    }

    public static String nickname() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getNickname();
    }

    public static boolean isAdministrator() {
        LoginUser user = HOLDER.get();
        return user != null && SystemRole.isAdministrator(user.getSystemRole());
    }

    public static void clear() {
        HOLDER.remove();
    }
}
