CREATE TABLE pms_ai_conversation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_pms_ai_conversation_user (user_id, updated_at),
    KEY idx_pms_ai_conversation_resource (resource_type, resource_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pms_ai_conversation_turn (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    message_text LONGTEXT NOT NULL,
    draft_json LONGTEXT NULL,
    request_id VARCHAR(160) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_pms_ai_conversation_turn_conversation (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pms_ai_plan (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    turn_id BIGINT NOT NULL,
    intent VARCHAR(80) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id BIGINT NOT NULL,
    node_id BIGINT NULL,
    context_version BIGINT NULL,
    context_snapshot_json LONGTEXT NOT NULL,
    draft_json LONGTEXT NOT NULL,
    preview_operation_id VARCHAR(64) NULL,
    idempotency_key VARCHAR(160) NULL,
    status VARCHAR(32) NOT NULL,
    verification_json LONGTEXT NULL,
    error_code VARCHAR(80) NULL,
    error_message VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    confirmed_at DATETIME NULL,
    executed_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_pms_ai_plan_conversation (conversation_id, created_at),
    KEY idx_pms_ai_plan_resource (resource_type, resource_id, updated_at),
    UNIQUE KEY uk_pms_ai_plan_conversation_idempotency (conversation_id, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pms_ai_operation_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    operation_id VARCHAR(64) NULL,
    sequence_no BIGINT NOT NULL,
    event_type VARCHAR(48) NOT NULL,
    detail_json LONGTEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pms_ai_operation_event_plan_sequence (plan_id, sequence_no),
    KEY idx_pms_ai_operation_event_conversation (conversation_id, sequence_no),
    KEY idx_pms_ai_operation_event_operation (operation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
