ALTER TABLE project_node_iteration_plan
    MODIFY COLUMN project_id BIGINT NULL,
    MODIFY COLUMN node_id BIGINT NULL;
