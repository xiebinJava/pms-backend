ALTER TABLE sys_user ADD COLUMN name_zh VARCHAR(80);
ALTER TABLE sys_user ADD COLUMN username_normalized VARCHAR(50);
ALTER TABLE sys_user ADD COLUMN phone VARCHAR(40);
ALTER TABLE sys_user ADD COLUMN status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE sys_user ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0;
ALTER TABLE sys_user ADD COLUMN locked_until TIMESTAMP NULL;
ALTER TABLE sys_user ADD COLUMN last_login_at TIMESTAMP NULL;
ALTER TABLE sys_user ADD COLUMN password_changed_at TIMESTAMP NULL;

ALTER TABLE project ADD COLUMN org_unit_id BIGINT NULL;

CREATE TABLE sys_company_profile (
    id BIGINT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    logo VARCHAR(500),
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    security_policy_json VARCHAR(4000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_org_unit_type (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(80) NOT NULL,
    sort INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_org_unit_type_code UNIQUE (code)
);

CREATE TABLE sys_org_unit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT,
    type_id BIGINT NOT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(120) NOT NULL,
    leader_user_id BIGINT,
    sort INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    path VARCHAR(1000) NOT NULL DEFAULT '/',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_org_unit_code UNIQUE (code)
);

CREATE TABLE sys_position (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(120) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_position_code UNIQUE (code)
);

CREATE TABLE sys_user_position (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    org_unit_id BIGINT NOT NULL,
    position_id BIGINT,
    manager_user_id BIGINT,
    assignment_type VARCHAR(16) NOT NULL DEFAULT 'PRIMARY',
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    start_date DATE NOT NULL,
    end_date DATE,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(120) NOT NULL,
    builtin BOOLEAN NOT NULL DEFAULT FALSE,
    data_scope_type VARCHAR(32) NOT NULL DEFAULT 'SELF',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_role_code UNIQUE (code)
);

CREATE TABLE sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(120) NOT NULL,
    name VARCHAR(120) NOT NULL,
    resource_type VARCHAR(16) NOT NULL,
    parent_id BIGINT,
    http_method VARCHAR(10),
    path_pattern VARCHAR(255),
    sort INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_permission_code UNIQUE (code)
);

CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    scope_org_unit_id BIGINT,
    start_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_at TIMESTAMP,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_role_org_scope (
    role_id BIGINT NOT NULL,
    org_unit_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, org_unit_id)
);

CREATE TABLE sys_invitation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    used_at TIMESTAMP
);

CREATE TABLE sys_auth_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    refresh_token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    revoke_reason VARCHAR(255),
    ip VARCHAR(64),
    user_agent VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_password_reset_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_login_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    login_name VARCHAR(80) NOT NULL,
    result VARCHAR(16) NOT NULL,
    reason VARCHAR(255),
    ip VARCHAR(64),
    user_agent VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator_id BIGINT,
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(60) NOT NULL,
    resource_id BIGINT,
    before_json VARCHAR(4000),
    after_json VARCHAR(4000),
    request_id VARCHAR(80),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_import_job (
    id VARCHAR(36) PRIMARY KEY,
    import_type VARCHAR(24) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    row_count INT NOT NULL DEFAULT 0,
    error_count INT NOT NULL DEFAULT 0,
    preview_json MEDIUMTEXT,
    error_json MEDIUMTEXT,
    created_by BIGINT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    committed_at TIMESTAMP
);

CREATE UNIQUE INDEX uk_user_username_normalized ON sys_user (username_normalized);
CREATE INDEX idx_org_unit_parent_status ON sys_org_unit (parent_id, status, sort);
CREATE INDEX idx_user_position_user_status ON sys_user_position (user_id, status, is_primary);
CREATE INDEX idx_user_position_org_status ON sys_user_position (org_unit_id, status, is_primary);
CREATE INDEX idx_user_role_user_status ON sys_user_role (user_id, status, start_at, end_at);
CREATE INDEX idx_role_scope_org ON sys_role_org_scope (org_unit_id, role_id);
CREATE INDEX idx_auth_session_user_status ON sys_auth_session (user_id, revoked_at, expires_at);
CREATE INDEX idx_operation_log_resource ON sys_operation_log (resource_type, resource_id, created_at);
CREATE INDEX idx_import_job_creator_status ON sys_import_job (created_by, status, created_at);
CREATE INDEX idx_project_org_unit ON project (org_unit_id, status);
