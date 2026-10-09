package com.brad.pms.ai.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Stable names for user-facing PMS commands. */
public enum CommandName {
    BATCH_WRITE("batch.write"),
    REQUIREMENT_CREATE("requirement.create"),
    REQUIREMENT_UPDATE("requirement.update"),
    REQUIREMENT_EXECUTION_TARGET_LINK("requirement.execution-target.link"),
    REQUIREMENT_EXECUTION_TARGET_CHANGE("requirement.execution-target.change"),
    REQUIREMENT_EXECUTION_TARGET_UNLINK("requirement.execution-target.unlink"),
    FOLLOWER_ADD("follower.add"),
    FOLLOWER_REMOVE("follower.remove"),
    MEMBER_ADD("member.add"),
    MEMBER_REMOVE("member.remove"),
    NODE_COMPLETE("node.complete"),
    NODE_FIELD_UPDATE("node.field.update"),
    NODE_ROLLBACK("node.rollback"),
    NODE_OWNER_UPDATE("node.owner.update"),
    NODE_SCHEDULE_UPDATE("node.schedule.update"),
    PROJECT_ARCHIVE("project.archive"),
    PROJECT_CREATE("project.create"),
    PROJECT_DELETE("project.delete"),
    PROJECT_UPDATE("project.update"),
    TOPIC_CREATE("topic.create"),
    TOPIC_UPDATE("topic.update"),
    TOPIC_PROJECT_LINK("topic.project.link"),
    STORY_CREATE("story.create"),
    STORY_UPDATE("story.update"),
    STORY_TOPIC_LINK("story.topic.link"),
    DEVELOPMENT_ITEM_NODE_OWNER_UPDATE("development-item.node.owner.update"),
    DEVELOPMENT_ITEM_NODE_SCHEDULE_UPDATE("development-item.node.schedule.update"),
    DEVELOPMENT_ITEM_NODE_FIELD_UPDATE("development-item.node.field.update"),
    DEVELOPMENT_ITEM_NODE_COMPLETE("development-item.node.complete"),
    DEVELOPMENT_ITEM_TASK_CREATE("development-item.task.create"),
    ITERATION_PLAN_CREATE("iteration-plan.create"),
    ITERATION_PLAN_UPDATE("iteration-plan.update"),
    ITERATION_PLAN_STORY_ADD("iteration-plan.story.add"),
    ITERATION_PLAN_STORY_REMOVE("iteration-plan.story.remove"),
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
