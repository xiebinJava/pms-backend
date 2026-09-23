ALTER TABLE pms_development_item_workflow_node
    ADD COLUMN field_values_json JSON NULL AFTER end_date;
