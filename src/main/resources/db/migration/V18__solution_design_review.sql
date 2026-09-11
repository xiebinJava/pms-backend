CREATE TABLE project_node_solution_package (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id       BIGINT       NOT NULL,
    node_id          BIGINT       NOT NULL,
    package_version  VARCHAR(20),
    product_solution TEXT,
    technical_solution TEXT,
    summary          TEXT,
    scope_coverage   TEXT,
    rollout_premise  TEXT,
    status           VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT草稿 SUBMITTED已提交',
    version          INT          NOT NULL DEFAULT 0,
    created_by       BIGINT,
    created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_node_solution_package_project_node (project_id, node_id),
    INDEX idx_node_solution_package_node (project_id, node_id)
);

CREATE TABLE project_node_solution_review (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id    BIGINT       NOT NULL,
    node_id       BIGINT       NOT NULL,
    review_type   VARCHAR(32)  NOT NULL COMMENT 'BUSINESS_PRODUCT业务产品 TECHNICAL技术 TEST_RELEASE测试发布',
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待评审 PASSED已通过',
    comment       VARCHAR(2000),
    completed_by  BIGINT,
    completed_at  TIMESTAMP NULL,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_node_solution_review_type (project_id, node_id, review_type),
    INDEX idx_node_solution_review_node (project_id, node_id)
);

CREATE TABLE project_node_solution_decision (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id    BIGINT       NOT NULL,
    node_id       BIGINT       NOT NULL,
    result        VARCHAR(32)  COMMENT 'PASS通过 CONDITIONAL_PASS有条件通过 RETURN_FOR_CHANGES退回修改',
    reason        VARCHAR(2000),
    conditions    VARCHAR(2000),
    status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT待确认 CONFIRMED已确认',
    confirmed_by  BIGINT,
    confirmed_at  TIMESTAMP NULL,
    version       INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_node_solution_decision_project_node (project_id, node_id),
    INDEX idx_node_solution_decision_node (project_id, node_id)
);
