package com.brad.pms.audit;

/** Stable resource categories persisted in the audit log. */
public enum AuditResourceType {
    PROJECT,
    PROJECT_NODE,
    PROJECT_NODE_FIELD_ATTACHMENT,
    TASK,
    PROJECT_MEMBER,
    PROJECT_FOLLOWER,
    PROJECT_COMMENT,
    TASK_ATTACHMENT,
    PROJECT_IMAGE,
    ROLE,
    WORKFLOW_TEMPLATE,
    PROJECT_TYPE,
    USER,
    ORG_UNIT,
    IMPORT_JOB,
    FEEDBACK_TICKET,
    AUDIT
}
