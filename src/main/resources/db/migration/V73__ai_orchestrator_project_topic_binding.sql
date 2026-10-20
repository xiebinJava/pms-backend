ALTER TABLE pms_ai_plan
    ADD COLUMN node_version BIGINT NULL AFTER node_id,
    ADD COLUMN component_key VARCHAR(160) NULL AFTER node_version;
