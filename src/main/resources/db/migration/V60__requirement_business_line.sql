ALTER TABLE pms_requirement
    ADD COLUMN org_unit_id BIGINT NULL AFTER owner_id,
    ADD KEY idx_requirement_org_unit (org_unit_id, deleted),
    ADD CONSTRAINT fk_requirement_org_unit FOREIGN KEY (org_unit_id) REFERENCES sys_org_unit (id);
