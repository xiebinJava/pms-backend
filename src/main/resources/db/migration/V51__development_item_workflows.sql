CREATE TABLE pms_development_item_workflow (
    id                    BIGINT NOT NULL AUTO_INCREMENT,
    item_type             VARCHAR(20) NOT NULL,
    item_id               BIGINT NOT NULL,
    project_id            BIGINT NOT NULL,
    source_node_id        BIGINT NOT NULL,
    template_version_id   BIGINT NOT NULL,
    version               INT NOT NULL DEFAULT 0,
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_development_item_workflow_item (item_type, item_id),
    KEY idx_development_item_workflow_project (project_id, item_type, id),
    CONSTRAINT fk_development_item_workflow_project FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT fk_development_item_workflow_source_node FOREIGN KEY (source_node_id)
        REFERENCES project_node (id) ON DELETE CASCADE,
    CONSTRAINT fk_development_item_workflow_template_version FOREIGN KEY (template_version_id) REFERENCES pms_workflow_template_version (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pms_development_item_workflow_node (
    id                    BIGINT NOT NULL AUTO_INCREMENT,
    workflow_id           BIGINT NOT NULL,
    node_key              VARCHAR(80) NOT NULL,
    name                  VARCHAR(160) NOT NULL,
    description           VARCHAR(1000) NULL,
    deliverable           VARCHAR(1000) NULL,
    sort                  INT NOT NULL DEFAULT 0,
    status                TINYINT NOT NULL DEFAULT 0 COMMENT '0 locked, 1 active, 2 completed',
    owner_id              BIGINT NULL,
    start_date            DATE NULL,
    end_date              DATE NULL,
    version               INT NOT NULL DEFAULT 0,
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_development_item_workflow_node_key (workflow_id, node_key),
    KEY idx_development_item_workflow_node_order (workflow_id, sort, id),
    CONSTRAINT fk_development_item_workflow_node_workflow FOREIGN KEY (workflow_id)
        REFERENCES pms_development_item_workflow (id) ON DELETE CASCADE,
    CONSTRAINT fk_development_item_workflow_node_owner FOREIGN KEY (owner_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pms_development_item_task (
    id                    BIGINT NOT NULL AUTO_INCREMENT,
    workflow_id           BIGINT NOT NULL,
    node_id               BIGINT NOT NULL,
    parent_id             BIGINT NULL,
    title                 VARCHAR(300) NOT NULL,
    description           TEXT NULL,
    status                TINYINT NOT NULL DEFAULT 0 COMMENT '0 todo, 1 in progress, 2 done',
    priority              TINYINT NOT NULL DEFAULT 1,
    assignee_id           BIGINT NULL,
    due_date              DATE NULL,
    sort                  INT NOT NULL DEFAULT 0,
    deleted               BOOLEAN NOT NULL DEFAULT FALSE,
    version               INT NOT NULL DEFAULT 0,
    created_by            BIGINT NULL,
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_development_item_task_node (workflow_id, node_id, deleted, sort, id),
    KEY idx_development_item_task_parent (parent_id, deleted),
    CONSTRAINT fk_development_item_task_workflow FOREIGN KEY (workflow_id)
        REFERENCES pms_development_item_workflow (id) ON DELETE CASCADE,
    CONSTRAINT fk_development_item_task_node FOREIGN KEY (node_id)
        REFERENCES pms_development_item_workflow_node (id) ON DELETE CASCADE,
    CONSTRAINT fk_development_item_task_parent FOREIGN KEY (parent_id)
        REFERENCES pms_development_item_task (id) ON DELETE CASCADE,
    CONSTRAINT fk_development_item_task_assignee FOREIGN KEY (assignee_id) REFERENCES sys_user (id),
    CONSTRAINT fk_development_item_task_creator FOREIGN KEY (created_by) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
