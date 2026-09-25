CREATE TABLE project_task_requirement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    requirement_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_requirement (task_id, requirement_id),
    KEY idx_requirement_tasks (project_id, node_id, requirement_id),
    KEY idx_task_requirement (task_id)
);
