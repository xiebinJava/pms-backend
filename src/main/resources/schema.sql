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
    product_solution VARCHAR(4000),
    technical_solution VARCHAR(4000),
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
    reviewer_id   BIGINT,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待评审 PASSED已通过',
    comment       VARCHAR(2000),
    completed_by  BIGINT,
    completed_at  TIMESTAMP NULL,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_node_solution_review_type (project_id, node_id, review_type),
    INDEX idx_node_solution_review_node (project_id, node_id),
    INDEX idx_node_solution_review_reviewer (project_id, node_id, reviewer_id)
);

CREATE TABLE IF NOT EXISTS project_node_solution_decision (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id    BIGINT       NOT NULL,
    node_id       BIGINT       NOT NULL,
    result        VARCHAR(32)  COMMENT 'PASS通过 CONDITIONAL_PASS有条件通过 RETURN_FOR_CHANGES退回修改',
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

CREATE TABLE IF NOT EXISTS project_node_acceptance_baseline (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id     BIGINT      NOT NULL,
    node_id        BIGINT      NOT NULL,
    status         TINYINT     NOT NULL DEFAULT 0 COMMENT '0草稿 1已确认',
    result         VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待确认 PASS通过 CONDITIONAL_PASS条件通过',
    residual_items VARCHAR(2000),
    confirmed_by   BIGINT,
    confirmed_at   TIMESTAMP NULL,
    requirement_baseline_version INT,
    version        INT         NOT NULL DEFAULT 0,
    created_at     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_node_acceptance_baseline_project_node (project_id, node_id),
    INDEX idx_node_acceptance_baseline_node (node_id)
);

CREATE TABLE IF NOT EXISTS project_node_acceptance_item (
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
    UNIQUE KEY uk_node_acceptance_item_requirement (project_id, node_id, requirement_id),
    INDEX idx_node_acceptance_item_node (project_id, node_id, sort)
);

CREATE TABLE IF NOT EXISTS project_node_acceptance_defect (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    node_id     BIGINT       NOT NULL,
    source_defect_id BIGINT,
    defect_key  VARCHAR(64)  NOT NULL,
    title       VARCHAR(300) NOT NULL,
    severity    VARCHAR(16)  NOT NULL,
    status      VARCHAR(24)  NOT NULL,
    impact      VARCHAR(500),
    created_by  BIGINT,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_node_acceptance_defect_key (project_id, node_id, defect_key),
    INDEX idx_node_acceptance_defect_node (project_id, node_id),
    INDEX idx_node_acceptance_defect_source_id (source_defect_id)
);

CREATE TABLE IF NOT EXISTS project_node_development_baseline (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id        BIGINT       NOT NULL,
    node_id           BIGINT       NOT NULL,
    current_iteration VARCHAR(120),
    version           INT          NOT NULL DEFAULT 0,
    created_by        BIGINT,
    created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_node_development_baseline_project_node (project_id, node_id),
    INDEX idx_node_development_baseline_node (node_id)
);

CREATE TABLE IF NOT EXISTS project_node_iteration_plan (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    node_id     BIGINT       NOT NULL,
    name        VARCHAR(200) NOT NULL,
    owner_id    BIGINT,
    goal        VARCHAR(500),
    status      VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    start_date  DATE,
    due_date    DATE,
    sort        INT          NOT NULL DEFAULT 0,
    created_by  BIGINT,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_node_iteration_plan_node (project_id, node_id, sort),
    INDEX idx_node_iteration_plan_owner (project_id, owner_id)
);

CREATE TABLE IF NOT EXISTS project_node_development_topic (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id           BIGINT       NOT NULL,
    node_id              BIGINT       NOT NULL,
    title                VARCHAR(200) NOT NULL,
    owner_id             BIGINT,
    milestone_id         BIGINT,
    latest_build_version VARCHAR(120),
    test_status          VARCHAR(20)  NOT NULL DEFAULT 'NOT_STARTED',
    sort                 INT          NOT NULL DEFAULT 0,
    created_by           BIGINT,
    created_at           TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_node_development_topic_node (project_id, node_id, sort),
    INDEX idx_node_development_topic_owner (project_id, owner_id),
    INDEX idx_node_development_topic_milestone (project_id, milestone_id)
);

CREATE TABLE IF NOT EXISTS project_node_development_story (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id   BIGINT       NOT NULL,
    node_id      BIGINT       NOT NULL,
    topic_id     BIGINT       NOT NULL,
    iteration_plan_id BIGINT,
    title        VARCHAR(300) NOT NULL,
    owner_id     BIGINT,
    status       VARCHAR(20)  NOT NULL DEFAULT 'NOT_STARTED',
    progress     INT          NOT NULL DEFAULT 0,
    story_points INT          NOT NULL DEFAULT 0,
    start_date   DATE,
    due_date     DATE,
    blocker      VARCHAR(500),
    sort         INT          NOT NULL DEFAULT 0,
    created_by   BIGINT,
    created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_node_development_story_topic (project_id, node_id, topic_id, sort),
    INDEX idx_node_development_story_status (project_id, node_id, status),
    INDEX idx_node_development_story_owner (project_id, owner_id),
    INDEX idx_node_development_story_iteration_plan (project_id, iteration_plan_id)
);

CREATE TABLE IF NOT EXISTS project_node_release_baseline (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id            BIGINT       NOT NULL,
    node_id               BIGINT       NOT NULL,
    release_version       VARCHAR(120),
    release_window_start  TIMESTAMP NULL,
    release_window_end    TIMESTAMP NULL,
    release_type          VARCHAR(32)  NOT NULL DEFAULT 'GRAY',
    package_ready         BOOLEAN      NOT NULL DEFAULT FALSE,
    config_confirmed      BOOLEAN      NOT NULL DEFAULT FALSE,
    rollback_ready        BOOLEAN      NOT NULL DEFAULT FALSE,
    monitoring_confirmed  BOOLEAN      NOT NULL DEFAULT FALSE,
    on_call_confirmed     BOOLEAN      NOT NULL DEFAULT FALSE,
    decision_result       VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    decision_note         VARCHAR(2000),
    handover_notes        VARCHAR(2000),
    observation_items     VARCHAR(2000),
    emergency_contact     VARCHAR(500),
    version               INT          NOT NULL DEFAULT 0,
    created_by            BIGINT,
    created_at            TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_node_release_baseline_project_node (project_id, node_id),
    INDEX idx_node_release_baseline_node (node_id)
);

CREATE TABLE IF NOT EXISTS project_node_value_review (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id                BIGINT       NOT NULL,
    node_id                   BIGINT       NOT NULL,
    result_status             VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    actual_result             VARCHAR(4000),
    retrospective_conclusion VARCHAR(4000),
    follow_up_actions         VARCHAR(2000),
    version                   INT          NOT NULL DEFAULT 0,
    created_by                BIGINT,
    created_at                TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_node_value_review_project_node (project_id, node_id),
    INDEX idx_node_value_review_node (node_id)
);

CREATE TABLE IF NOT EXISTS project_node_knowledge_baseline (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    node_id     BIGINT       NOT NULL,
    version     INT          NOT NULL DEFAULT 0,
    created_by  BIGINT,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_node_knowledge_baseline_project_node (project_id, node_id),
    INDEX idx_node_knowledge_baseline_node (node_id)
);

CREATE TABLE IF NOT EXISTS project_node_knowledge_asset (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id   BIGINT       NOT NULL,
    node_id      BIGINT       NOT NULL,
    name         VARCHAR(200) NOT NULL,
    source       VARCHAR(200),
    asset_type   VARCHAR(20)  NOT NULL DEFAULT 'CASE',
    improvement  VARCHAR(500),
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    sort         INT          NOT NULL DEFAULT 0,
    created_by   BIGINT,
    created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_node_knowledge_asset_node (project_id, node_id, sort),
    INDEX idx_node_knowledge_asset_status (project_id, node_id, status)
);

CREATE TABLE IF NOT EXISTS project_node_knowledge_action (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    node_id      BIGINT       NOT NULL,
    title       VARCHAR(300) NOT NULL,
    note        VARCHAR(500),
    owner_id    BIGINT,
    due_date    DATE,
    status      VARCHAR(20)  NOT NULL DEFAULT 'NOT_STARTED',
    sort        INT          NOT NULL DEFAULT 0,
    created_by  BIGINT,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_node_knowledge_action_node (project_id, node_id, sort),
    INDEX idx_node_knowledge_action_owner (project_id, owner_id),
    INDEX idx_node_knowledge_action_status (project_id, node_id, status)
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
