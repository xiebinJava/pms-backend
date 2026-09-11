CREATE TABLE project_node_baseline (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id    BIGINT       NOT NULL,
    node_id       BIGINT       NOT NULL,
    objective     VARCHAR(2000),
    deliverable   VARCHAR(2000),
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿 1已确认',
    confirmed_by  BIGINT,
    confirmed_at  TIMESTAMP NULL,
    version       INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_node_baseline_project_node (project_id, node_id),
    INDEX idx_node_baseline_node (node_id)
);

CREATE TABLE project_node_scope_item (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT      NOT NULL,
    node_id     BIGINT      NOT NULL,
    direction   VARCHAR(8)  NOT NULL COMMENT 'IN纳入 OUT不纳入',
    title       VARCHAR(500) NOT NULL,
    sort        INT         NOT NULL DEFAULT 0,
    created_by  BIGINT,
    created_at  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_node_scope_item_node (project_id, node_id, direction, sort)
);

CREATE TABLE project_node_requirement (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id          BIGINT       NOT NULL,
    node_id             BIGINT       NOT NULL,
    code                VARCHAR(32)  NOT NULL,
    name                VARCHAR(300) NOT NULL,
    description         VARCHAR(1000),
    type                VARCHAR(16)  NOT NULL COMMENT 'BUSINESS FUNCTIONAL CONSTRAINT',
    priority            TINYINT      NOT NULL DEFAULT 1 COMMENT '1低 2中 3高',
    acceptance_criteria VARCHAR(1000) NOT NULL,
    status              TINYINT      NOT NULL DEFAULT 0 COMMENT '0待评审 1已确认',
    sort                INT          NOT NULL DEFAULT 0,
    created_by          BIGINT,
    created_at          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_node_requirement_code (project_id, node_id, code),
    INDEX idx_node_requirement_node (project_id, node_id, sort)
);
