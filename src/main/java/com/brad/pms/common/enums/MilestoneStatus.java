package com.brad.pms.common.enums;

import lombok.Getter;

@Getter
public enum MilestoneStatus {

    PENDING(0, "未开始"),
    ACTIVE(1, "进行中"),
    COMPLETED(2, "已完成");

    private final int code;
    private final String label;

    MilestoneStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static String labelOf(int code) {
        for (MilestoneStatus s : values()) {
            if (s.code == code) return s.label;
        }
        return String.valueOf(code);
    }
}
