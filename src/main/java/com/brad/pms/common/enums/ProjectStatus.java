package com.brad.pms.common.enums;

import lombok.Getter;

@Getter
public enum ProjectStatus {

    PLANNING(0, "未开始"),
    ACTIVE(1, "进行中"),
    COMPLETED(2, "已完成"),
    ARCHIVED(3, "已归档");

    private final int code;
    private final String label;

    ProjectStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static String labelOf(int code) {
        for (ProjectStatus s : values()) {
            if (s.code == code) return s.label;
        }
        return String.valueOf(code);
    }
}
