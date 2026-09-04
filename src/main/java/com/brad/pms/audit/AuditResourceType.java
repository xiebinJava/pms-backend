package com.brad.pms.audit;

/** Stable resource categories persisted in the audit log. */
public enum AuditResourceType {
    PROJECT,
    PROJECT_NODE,
    TASK,
    PROJECT_MEMBER,
    PROJECT_FOLLOWER,
    PROJECT_COMMENT,
    TASK_ATTACHMENT,
    PROJECT_IMAGE,
    ROLE,
    USER,
    ORG_UNIT,
    IMPORT_JOB,
    FEEDBACK_TICKET,
    AUDIT
}
