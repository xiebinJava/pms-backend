CREATE TABLE pms_system (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(5000) NULL,
    owner_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pms_system_code (code),
    KEY idx_pms_system_status_updated (status, updated_at, id),
    CONSTRAINT chk_pms_system_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT fk_pms_system_owner FOREIGN KEY (owner_id) REFERENCES sys_user (id),
    CONSTRAINT fk_pms_system_creator FOREIGN KEY (created_by) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pms_system_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    system_id BIGINT NOT NULL,
    version_no VARCHAR(64) NOT NULL,
    version_name VARCHAR(200) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    planned_release_date DATE NULL,
    released_at DATETIME NULL,
    release_notes VARCHAR(10000) NULL,
    owner_id BIGINT NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pms_system_version_no (system_id, version_no),
    KEY idx_pms_system_version_status (system_id, status, updated_at, id),
    CONSTRAINT chk_pms_system_version_status CHECK (
        status IN ('DRAFT', 'PLANNED', 'DEVELOPING', 'TESTING', 'RELEASED', 'ARCHIVED')
    ),
    CONSTRAINT fk_pms_system_version_system FOREIGN KEY (system_id) REFERENCES pms_system (id),
    CONSTRAINT fk_pms_system_version_owner FOREIGN KEY (owner_id) REFERENCES sys_user (id),
    CONSTRAINT fk_pms_system_version_creator FOREIGN KEY (created_by) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pms_system_version_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(16) NULL,
    to_status VARCHAR(16) NULL,
    reason VARCHAR(500) NULL,
    operator_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_pms_system_version_history_version (version_id, created_at, id),
    CONSTRAINT fk_pms_system_version_history_version
        FOREIGN KEY (version_id) REFERENCES pms_system_version (id) ON DELETE CASCADE,
    CONSTRAINT fk_pms_system_version_history_operator
        FOREIGN KEY (operator_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
