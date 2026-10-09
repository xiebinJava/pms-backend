ALTER TABLE pms_requirement
    ADD COLUMN system_id BIGINT NULL AFTER org_unit_id,
    ADD KEY idx_requirement_system (system_id, deleted),
    ADD CONSTRAINT fk_requirement_system FOREIGN KEY (system_id) REFERENCES pms_system (id);

ALTER TABLE project_node_iteration_plan
    ADD COLUMN system_id BIGINT NULL AFTER node_id,
    ADD KEY idx_iteration_plan_system (system_id),
    ADD CONSTRAINT fk_iteration_plan_system FOREIGN KEY (system_id) REFERENCES pms_system (id);

UPDATE project_node_iteration_plan plan
LEFT JOIN pms_system_version version ON version.id = plan.system_version_id
SET plan.system_id = version.system_id
WHERE plan.system_id IS NULL AND version.system_id IS NOT NULL;
