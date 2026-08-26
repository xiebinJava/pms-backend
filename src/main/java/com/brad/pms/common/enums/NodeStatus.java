package com.brad.pms.common.enums;

import lombok.Getter;

@Getter
public enum NodeStatus {

    NOT_STARTED(0, "未开始"),
    IN_PROGRESS(1, "进行中"),
    COMPLETED(2, "已完成"),
    TERMINATED(3, "已终止");

    private final int code;
    private final String label;

    NodeStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static String labelOf(int code) {
        for (NodeStatus status : values()) {
            if (status.code == code) return status.label;
        }
        return String.valueOf(code);
    }

    public static boolean isReadOnly(Integer code) {
        return code != null && (code == COMPLETED.code || code == TERMINATED.code);
    }
}
