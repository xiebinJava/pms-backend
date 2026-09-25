package com.brad.pms.ai.context;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Stable page identifiers exchanged between the PMS UI, the AI service, and
 * the command layer. Keep the wire values backwards compatible.
 */
public enum PageContextType {
    PROJECT_LIST("project-list"),
    PROJECT_DASHBOARD("project-dashboard"),
    PROJECT_DETAIL("project-detail"),
    WORKFLOW_TEMPLATE("workflow-template");

    private final String code;

    PageContextType(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static PageContextType fromCode(String code) {
        for (PageContextType type : values()) {
            if (type.code.equals(code)) return type;
        }
        throw new IllegalArgumentException("Unsupported page context type: " + code);
    }
}
