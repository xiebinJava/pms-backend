CREATE TABLE pms_project_type (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    status TINYINT NOT NULL DEFAULT 1,
    sort INT NOT NULL DEFAULT 0,
    default_template_version_id BIGINT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pms_project_type_code (code),
    KEY idx_pms_project_type_status_sort (status, sort, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pms_workflow_template (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    project_type_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(500) NULL,
    latest_version_no INT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pms_workflow_template_code (code),
    KEY idx_pms_workflow_template_type (project_type_id, deleted, id),
    CONSTRAINT fk_pms_workflow_template_type FOREIGN KEY (project_type_id) REFERENCES pms_project_type (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pms_workflow_template_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    template_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    definition_json LONGTEXT NOT NULL,
    published_at DATETIME NULL,
    created_by BIGINT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pms_workflow_template_version (template_id, version_no),
    KEY idx_pms_workflow_template_version_status (template_id, status, version_no),
    CONSTRAINT fk_pms_workflow_version_template FOREIGN KEY (template_id) REFERENCES pms_workflow_template (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE project_node_field_value (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    field_key VARCHAR(64) NOT NULL,
    value_json LONGTEXT NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_node_field_value (node_id, field_key),
    KEY idx_project_node_field_value_project (project_id, node_id),
    CONSTRAINT fk_project_node_field_value_project FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT fk_project_node_field_value_node FOREIGN KEY (node_id) REFERENCES project_node (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE project_node_field_attachment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    field_key VARCHAR(64) NOT NULL,
    file_key VARCHAR(500) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(160) NULL,
    size_bytes BIGINT NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_project_node_field_attachment (project_id, node_id, field_key, id),
    CONSTRAINT fk_project_node_field_attachment_project FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT fk_project_node_field_attachment_node FOREIGN KEY (node_id) REFERENCES project_node (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE project
    ADD COLUMN project_type_id BIGINT NULL AFTER project_level,
    ADD COLUMN workflow_template_version_id BIGINT NULL AFTER project_type_id,
    ADD KEY idx_project_type (project_type_id),
    ADD KEY idx_project_workflow_version (workflow_template_version_id);

ALTER TABLE pms_project_type
    ADD CONSTRAINT fk_pms_project_type_default_version
        FOREIGN KEY (default_template_version_id) REFERENCES pms_workflow_template_version (id);

ALTER TABLE project
    ADD CONSTRAINT fk_project_type FOREIGN KEY (project_type_id) REFERENCES pms_project_type (id),
    ADD CONSTRAINT fk_project_workflow_version FOREIGN KEY (workflow_template_version_id)
        REFERENCES pms_workflow_template_version (id);
