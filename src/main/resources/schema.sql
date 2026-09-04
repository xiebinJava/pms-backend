-- 项目管理系统核心表结构（H2 / MySQL 兼容）
CREATE TABLE IF NOT EXISTS sys_user (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    password   VARCHAR(100) NOT NULL,
    nickname   VARCHAR(50),
    email      VARCHAR(100),
    avatar     VARCHAR(255),
    system_role TINYINT     NOT NULL DEFAULT 0 COMMENT '0普通用户 1管理员',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS project (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1进行中 2已完成 3已终止 4已删除',
    priority    TINYINT      NOT NULL DEFAULT 1 COMMENT '0低 1中 2高 3紧急',
    project_level TINYINT    NOT NULL DEFAULT 0 COMMENT '0常规(C) 1重要(B) 2关键(A) 3战略(S)',
    owner_id    BIGINT       NOT NULL,
    start_date  DATE,
    end_date    DATE,
    progress    TINYINT      NOT NULL DEFAULT 0 COMMENT '0-100',
    created_by  BIGINT,
    project_manager_id BIGINT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS project_member (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    role       TINYINT     NOT NULL DEFAULT 2 COMMENT '0负责人 1管理员 2成员',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (project_id, user_id)
);

CREATE TABLE IF NOT EXISTS project_image (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id    BIGINT       NOT NULL,
    file_key      VARCHAR(255) NOT NULL,
    original_name VARCHAR(255),
    content_type  VARCHAR(120),
    size_bytes    BIGINT,
    created_by    BIGINT,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted       BOOLEAN NOT NULL DEFAULT FALSE,
    version       INT NOT NULL DEFAULT 0,
    UNIQUE (project_id, file_key)
);

CREATE TABLE IF NOT EXISTS project_follower (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (project_id, user_id)
);

CREATE TABLE IF NOT EXISTS project_task (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    node_id     BIGINT,
    parent_id   BIGINT,
    title       VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    deliverable VARCHAR(2000),
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0待办 1进行中 2已完成',
    priority    TINYINT      NOT NULL DEFAULT 1,
    assignee_id BIGINT,
    milestone_id BIGINT,
    sort        INT          NOT NULL DEFAULT 0,
    due_date    DATE,
    created_by  BIGINT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS project_milestone (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    title       VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    due_date    DATE,
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0未开始 1进行中 2已完成',
    created_by  BIGINT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS project_comment (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT        NOT NULL,
    task_id    BIGINT,
    content    VARCHAR(2000) NOT NULL,
    user_id    BIGINT        NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS project_node (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    node_key    VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    deliverable VARCHAR(1000),
    roles       VARCHAR(500),
    owner_id    BIGINT,
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0未开始 1进行中 2已完成 3已终止',
    sort        INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS project_node_solution_package (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id       BIGINT       NOT NULL,
    node_id          BIGINT       NOT NULL,
    package_version  VARCHAR(20),
    product_solution VARCHAR(4000),
    technical_solution VARCHAR(4000),
    summary          VARCHAR(2000),
    scope_coverage   VARCHAR(2000),
    rollout_premise  VARCHAR(2000),
    status           VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT草稿 SUBMITTED已提交',
    version          INT          NOT NULL DEFAULT 0,
    created_by       BIGINT,
    created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_node_solution_package_project_node (project_id, node_id),
    INDEX idx_node_solution_package_node (project_id, node_id)
);

CREATE TABLE IF NOT EXISTS project_node_solution_review (
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

CREATE TABLE IF NOT EXISTS project_node_solution_decision (
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

CREATE INDEX idx_task_project ON project_task (project_id);
CREATE INDEX idx_task_node ON project_task (project_id, node_id);
CREATE INDEX idx_task_status ON project_task (project_id, status);
CREATE INDEX idx_member_project ON project_member (project_id);
CREATE INDEX idx_follower_project ON project_follower (project_id);
CREATE INDEX idx_milestone_project ON project_milestone (project_id);
CREATE INDEX idx_comment_project ON project_comment (project_id);
CREATE INDEX idx_node_project ON project_node (project_id, sort);

CREATE TABLE IF NOT EXISTS project_lifecycle_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    action      VARCHAR(30)  NOT NULL,
    reason      VARCHAR(500) NOT NULL,
    from_status TINYINT,
    to_status   TINYINT,
    operator_id BIGINT       NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_lifecycle_project ON project_lifecycle_log (project_id, created_at);

CREATE TABLE IF NOT EXISTS sys_operation_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator_id   BIGINT,
    action        VARCHAR(80) NOT NULL,
    resource_type VARCHAR(60) NOT NULL,
    resource_id   BIGINT,
    project_id    BIGINT,
    before_json   VARCHAR(4000),
    after_json    VARCHAR(4000),
    reason        VARCHAR(500),
    result        VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
    request_id    VARCHAR(80),
    ip            VARCHAR(64),
    user_agent    VARCHAR(500),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_operation_log_resource ON sys_operation_log (resource_type, resource_id, created_at);
CREATE INDEX idx_operation_log_project_created ON sys_operation_log (project_id, created_at);
CREATE INDEX idx_operation_log_operator_created ON sys_operation_log (operator_id, created_at);
CREATE INDEX idx_operation_log_action_created ON sys_operation_log (action, created_at);
CREATE INDEX idx_operation_log_result_created ON sys_operation_log (result, created_at);
