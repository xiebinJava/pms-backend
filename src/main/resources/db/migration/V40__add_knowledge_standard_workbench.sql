CREATE TABLE project_node_knowledge_baseline (
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

CREATE TABLE project_node_knowledge_asset (
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

CREATE TABLE project_node_knowledge_action (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    node_id     BIGINT       NOT NULL,
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

UPDATE project_node
SET description = '沉淀项目经验与管理标准，更新项目流程和常用检查项，整理可复用的模板、清单与案例，推动改进行动落地。'
WHERE node_key = 'knowledge'
  AND (description LIKE '%归档%' OR description LIKE '%文档%');
