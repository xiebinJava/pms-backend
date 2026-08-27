package com.brad.pms.common.enums;

import lombok.Getter;

/**
 * 系统级角色。它与项目成员角色分开，避免把“项目管理员”误当成全局管理员。
 */
@Getter
public enum SystemRole {

    USER(0, "普通用户"),
    ADMINISTRATOR(1, "管理员");

    private final int code;
    private final String label;

    SystemRole(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static boolean isAdministrator(Integer code) {
        return code != null && ADMINISTRATOR.code == code;
    }
}
