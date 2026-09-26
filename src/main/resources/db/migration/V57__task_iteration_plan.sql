ALTER TABLE project_task
    ADD COLUMN iteration_plan_id BIGINT NULL AFTER node_id;

ALTER TABLE project_task
    ADD KEY idx_task_iteration_plan (project_id, iteration_plan_id, parent_id, sort);
