package com.brad.pms.common.enums;

import lombok.Getter;

@Getter
public enum ProjectStatus {

    ACTIVE(1, "进行中"),
    COMPLETED(2, "已完成"),
    TERMINATED(3, "已终止");

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
        if (code == 0) return ACTIVE.label;
        return String.valueOf(code);
    }

    /**
     * 旧数据曾使用 0 表示项目未开始；项目生命周期规范统一从进行中开始，读取时兼容旧值。
     */
    public static int normalize(Integer code) {
        return code == null || code == 0 ? ACTIVE.code : code;
    }

    public static boolean isReadOnly(Integer code) {
        int normalized = normalize(code);
        return normalized == COMPLETED.code || normalized == TERMINATED.code;
    }
}
