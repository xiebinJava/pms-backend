package com.brad.pms.ai.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Stable names for user-facing PMS commands. */
public enum CommandName {
    TASK_CREATE("task.create"),
    TASK_ASSIGN("task.assign");

    private final String code;

    CommandName(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static CommandName fromCode(String code) {
        for (CommandName name : values()) {
            if (name.code.equals(code)) return name;
        }
        throw new IllegalArgumentException("Unsupported command: " + code);
    }
}
