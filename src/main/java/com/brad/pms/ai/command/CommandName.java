package com.brad.pms.ai.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Stable names for user-facing PMS commands. */
public enum CommandName {
    MEMBER_ADD("member.add"),
    MEMBER_REMOVE("member.remove"),
    NODE_COMPLETE("node.complete"),
    NODE_ROLLBACK("node.rollback"),
    NODE_OWNER_UPDATE("node.owner.update"),
    NODE_SCHEDULE_UPDATE("node.schedule.update"),
    PROJECT_ARCHIVE("project.archive"),
    PROJECT_CREATE("project.create"),
    PROJECT_DELETE("project.delete"),
    TASK_CREATE("task.create"),
    TASK_ASSIGN("task.assign"),
    TASK_UPDATE("task.update");

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
