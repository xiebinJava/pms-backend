package com.brad.pms.workflow;

import com.brad.pms.common.exception.BusinessException;

import java.util.Locale;

public enum DevelopmentItemType {
    TOPIC("topic-management"),
    STORY("story-management");

    private final String processTypeCode;

    DevelopmentItemType(String processTypeCode) {
        this.processTypeCode = processTypeCode;
    }

    public String processTypeCode() {
        return processTypeCode;
    }

    public static DevelopmentItemType from(String value) {
        if (value == null) throw BusinessException.error("研发事项类型不正确");
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw BusinessException.error("研发事项类型不正确");
        }
    }
}
