CREATE TABLE project_node_iteration_plan (
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

ALTER TABLE project_node_development_story
    ADD COLUMN iteration_plan_id BIGINT NULL AFTER topic_id;

ALTER TABLE project_node_development_story
    ADD INDEX idx_node_development_story_iteration_plan (project_id, iteration_plan_id);
