CREATE TABLE sys_external_identity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    issuer VARCHAR(512) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL,
    email_snapshot VARCHAR(320) NULL,
    provider_type VARCHAR(32) NOT NULL DEFAULT 'oidc',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_external_identity_issuer_subject (issuer(191), subject),
    KEY idx_external_identity_user_id (user_id),
    CONSTRAINT fk_external_identity_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
