CREATE TABLE pms_requirement (
    id                       BIGINT NOT NULL AUTO_INCREMENT,
    title                    VARCHAR(200) NOT NULL,
    description              TEXT NULL,
    priority                 TINYINT NOT NULL DEFAULT 1 COMMENT '0低 1中 2高 3紧急',
    owner_id                 BIGINT NULL,
    execution_target_type    VARCHAR(20) NULL,
    execution_target_id      BIGINT NULL,
    status                   VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted                  BOOLEAN NOT NULL DEFAULT FALSE,
    version                  INT NOT NULL DEFAULT 0,
    created_by               BIGINT NULL,
    created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_requirement_status (deleted, status, updated_at),
    KEY idx_requirement_owner (owner_id, deleted),
    KEY idx_requirement_execution_target (execution_target_type, execution_target_id),
    CONSTRAINT chk_requirement_execution_target_pair CHECK (
        (execution_target_type IS NULL AND execution_target_id IS NULL)
        OR (execution_target_type IS NOT NULL AND execution_target_id IS NOT NULL)
    ),
    CONSTRAINT chk_requirement_execution_target_type CHECK (
        execution_target_type IS NULL
        OR execution_target_type IN ('PROJECT', 'TOPIC', 'STORY')
    ),
    CONSTRAINT fk_requirement_owner FOREIGN KEY (owner_id) REFERENCES sys_user (id),
    CONSTRAINT fk_requirement_creator FOREIGN KEY (created_by) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pms_requirement_execution_target_history (
    id                       BIGINT NOT NULL AUTO_INCREMENT,
    requirement_id           BIGINT NOT NULL,
    action                   VARCHAR(20) NOT NULL,
    target_type              VARCHAR(20) NULL,
    target_id                BIGINT NULL,
    previous_target_type     VARCHAR(20) NULL,
    previous_target_id       BIGINT NULL,
    reason                   VARCHAR(500) NULL,
    operator_id              BIGINT NULL,
    created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_requirement_target_history_requirement (requirement_id, created_at, id),
    KEY idx_requirement_target_history_target (target_type, target_id, created_at),
    CONSTRAINT fk_requirement_target_history_requirement FOREIGN KEY (requirement_id)
        REFERENCES pms_requirement (id) ON DELETE CASCADE,
    CONSTRAINT fk_requirement_target_history_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
