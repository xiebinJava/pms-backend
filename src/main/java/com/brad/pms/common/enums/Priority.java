package com.brad.pms.common.enums;

import lombok.Getter;

@Getter
public enum Priority {

    LOW(0, "低"),
    MEDIUM(1, "中"),
    HIGH(2, "高"),
    URGENT(3, "紧急");

    private final int code;
    private final String label;

    Priority(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static String labelOf(int code) {
        for (Priority p : values()) {
            if (p.code == code) return p.label;
        }
        return String.valueOf(code);
    }
}
