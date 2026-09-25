CREATE TABLE pms_dsh_authorization_codes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code_hash CHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    pms_session_id BIGINT NOT NULL,
    dsh_session_id VARCHAR(128) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    scopes_json LONGTEXT NOT NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pms_dsh_authorization_code_hash (code_hash),
    KEY idx_pms_dsh_authorization_code_session (pms_session_id, expires_at),
    KEY idx_pms_dsh_authorization_code_dsh_session (dsh_session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
