package com.brad.pms.common.enums;

import lombok.Getter;

@Getter
public enum ProjectStatus {

    ACTIVE(1, "进行中"),
    COMPLETED(2, "已完成"),
    TERMINATED(3, "已终止"),
    DELETED(4, "已删除");

    private final int code;
    private final String label;

    ProjectStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static String labelOf(int code) {
        int normalized = normalize(code);
        for (ProjectStatus s : values()) {
            if (s.code == normalized) return s.label;
        }
        return String.valueOf(code);
    }

    /**
     * 兼容历史数据：项目状态 0 或空值统一按进行中处理。
     */
    public static int normalize(Integer code) {
        return code == null || code == 0 ? ACTIVE.code : code;
    }

    public static boolean isReadOnly(Integer code) {
        int normalized = normalize(code);
        return normalized == COMPLETED.code || normalized == TERMINATED.code || normalized == DELETED.code;
    }

    public static boolean isOpen(Integer code) {
        int normalized = normalize(code);
        return normalized == ACTIVE.code;
    }
}
