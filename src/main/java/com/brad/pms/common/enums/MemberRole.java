package com.brad.pms.common.enums;

import lombok.Getter;

@Getter
public enum MemberRole {

    OWNER(0, "负责人"),
    ADMIN(1, "管理员"),
    MEMBER(2, "成员");

    private final int code;
    private final String label;

    MemberRole(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static String labelOf(int code) {
        for (MemberRole r : values()) {
            if (r.code == code) return r.label;
        }
        return String.valueOf(code);
    }
}
