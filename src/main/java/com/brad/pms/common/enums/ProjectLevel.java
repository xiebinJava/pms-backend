package com.brad.pms.common.enums;

import lombok.Getter;

/** Project importance, kept separate from delivery urgency (Priority). */
@Getter
public enum ProjectLevel {

    /** 常规项目（C）：日常改进或小型项目。 */
    ROUTINE(0, "常规项目（C）"),
    /** 重要项目（B）：部门级重点项目。 */
    IMPORTANT(1, "重要项目（B）"),
    /** 关键项目（A）：跨部门、影响业务线核心指标。 */
    KEY(2, "关键项目（A）"),
    /** 战略项目（S）：直接影响公司战略。 */
    STRATEGIC(3, "战略项目（S）");

    private final int code;
    private final String label;

    ProjectLevel(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static boolean isValid(Integer code) {
        if (code == null) return false;
        for (ProjectLevel level : values()) {
            if (level.code == code) return true;
        }
        return false;
    }

    public static String labelOf(Integer code) {
        if (code != null) {
            for (ProjectLevel level : values()) {
                if (level.code == code) return level.label;
            }
        }
        return String.valueOf(code);
    }
}
