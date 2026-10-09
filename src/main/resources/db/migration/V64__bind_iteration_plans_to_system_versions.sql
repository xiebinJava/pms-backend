ALTER TABLE project_node_iteration_plan
    ADD COLUMN system_version_id BIGINT NULL AFTER node_id,
    ADD KEY idx_node_iteration_plan_system_version (system_version_id),
    ADD CONSTRAINT fk_node_iteration_plan_system_version
        FOREIGN KEY (system_version_id) REFERENCES pms_system_version (id);
