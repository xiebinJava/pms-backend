ALTER TABLE pms_development_item_workflow
    ADD COLUMN terminal_status VARCHAR(32) NULL AFTER template_version_id,
    ADD KEY idx_development_item_workflow_terminal_status (item_type, terminal_status, item_id);
