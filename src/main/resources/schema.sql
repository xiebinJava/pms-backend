-- 项目管理系统核心表结构（H2 / MySQL 兼容）
CREATE TABLE IF NOT EXISTS sys_user (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    password   VARCHAR(100) NOT NULL,
    nickname   VARCHAR(50),
    email      VARCHAR(100),
    avatar     VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS project (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0未开始 1进行中 2已完成 3已归档',
    priority    TINYINT      NOT NULL DEFAULT 1 COMMENT '0低 1中 2高 3紧急',
    owner_id    BIGINT       NOT NULL,
    start_date  DATE,
    end_date    DATE,
    progress    TINYINT      NOT NULL DEFAULT 0 COMMENT '0-100',
    created_by  BIGINT,
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
    parent_id   BIGINT,
    title       VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
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
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0待开始 1进行中 2已完成',
    sort        INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_task_project ON project_task (project_id);
CREATE INDEX idx_task_status ON project_task (project_id, status);
CREATE INDEX idx_member_project ON project_member (project_id);
CREATE INDEX idx_follower_project ON project_follower (project_id);
CREATE INDEX idx_milestone_project ON project_milestone (project_id);
CREATE INDEX idx_comment_project ON project_comment (project_id);
CREATE INDEX idx_node_project ON project_node (project_id, sort);
