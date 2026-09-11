package com.brad.pms.common.enums;

import lombok.Getter;

@Getter
public enum TaskStatus {

    TODO(0, "待办"),
    DOING(1, "进行中"),
    DONE(2, "已完成");

    private final int code;
    private final String label;

    TaskStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static String labelOf(int code) {
        for (TaskStatus s : values()) {
            if (s.code == code) return s.label;
        }
        return String.valueOf(code);
    }
}
