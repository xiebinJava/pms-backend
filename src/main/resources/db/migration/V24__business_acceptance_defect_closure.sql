CREATE TABLE project_node_acceptance_baseline (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id    BIGINT       NOT NULL,
    node_id       BIGINT       NOT NULL,
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿 1已确认',
    result        VARCHAR(24)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待确认 PASS通过 CONDITIONAL_PASS条件通过',
    residual_items VARCHAR(2000),
    confirmed_by  BIGINT,
    confirmed_at  TIMESTAMP NULL,
    version       INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_node_acceptance_baseline_project_node UNIQUE (project_id, node_id),
    INDEX idx_node_acceptance_baseline_node (node_id)
);

CREATE TABLE project_node_acceptance_item (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id          BIGINT       NOT NULL,
    node_id             BIGINT       NOT NULL,
    requirement_id      BIGINT       NOT NULL,
    requirement_code    VARCHAR(64)  NOT NULL,
    requirement_name    VARCHAR(300) NOT NULL,
    acceptance_criteria VARCHAR(1000),
    result              VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待验收 PASS通过 FAIL失败 BLOCKED阻塞',
    note                VARCHAR(1000),
    sort                INT          NOT NULL DEFAULT 0,
    created_by          BIGINT,
    created_at          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_node_acceptance_item_requirement UNIQUE (project_id, node_id, requirement_id),
    INDEX idx_node_acceptance_item_node (project_id, node_id, sort)
);

CREATE TABLE project_node_acceptance_defect (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    node_id     BIGINT       NOT NULL,
    defect_key  VARCHAR(64)  NOT NULL,
    title       VARCHAR(300) NOT NULL,
    severity    VARCHAR(16)  NOT NULL COMMENT 'HIGH高 MEDIUM中 LOW低',
    status      VARCHAR(24)  NOT NULL COMMENT 'OPEN处理中 RESOLVED已解决 CLOSED已关闭',
    impact      VARCHAR(500),
    created_by  BIGINT,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_node_acceptance_defect_key UNIQUE (project_id, node_id, defect_key),
    INDEX idx_node_acceptance_defect_node (project_id, node_id)
);
