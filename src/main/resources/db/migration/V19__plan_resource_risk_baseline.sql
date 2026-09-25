CREATE TABLE project_node_plan_baseline (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id   BIGINT       NOT NULL,
    node_id      BIGINT       NOT NULL,
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿 1已确认',
    confirmed_by BIGINT,
    confirmed_at TIMESTAMP NULL,
    version      INT          NOT NULL DEFAULT 0,
    created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_node_plan_baseline_project_node UNIQUE (project_id, node_id),
    INDEX idx_node_plan_baseline_node (node_id)
);

CREATE TABLE project_node_plan_item (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    node_id     BIGINT       NOT NULL,
    title       VARCHAR(300) NOT NULL,
    owner_id    BIGINT,
    start_date  DATE,
    end_date    DATE,
    deliverable VARCHAR(1000),
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待开始 IN_PROGRESS进行中 CONFIRMED已确认',
    sort        INT          NOT NULL DEFAULT 0,
    created_by  BIGINT,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_node_plan_item_node (project_id, node_id, sort)
);

CREATE TABLE project_node_resource (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT        NOT NULL,
    node_id    BIGINT        NOT NULL,
    role_name  VARCHAR(100)  NOT NULL,
    owner_id   BIGINT,
    focus      VARCHAR(1000),
    status     VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待确认 CONFIRMED已确认',
    sort       INT           NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_node_resource_node (project_id, node_id, sort)
);

CREATE TABLE project_node_risk (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT        NOT NULL,
    node_id     BIGINT        NOT NULL,
    title       VARCHAR(300)  NOT NULL,
    level       VARCHAR(16)   NOT NULL COMMENT 'HIGH高 MEDIUM中 LOW低',
    owner_id    BIGINT,
    response    VARCHAR(1000),
    status      VARCHAR(20)   NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN跟进中 MITIGATED已有措施',
    sort        INT           NOT NULL DEFAULT 0,
    created_by  BIGINT,
    created_at  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_node_risk_node (project_id, node_id, sort)
);
