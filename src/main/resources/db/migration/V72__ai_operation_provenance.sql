ALTER TABLE pms_ai_operation
    ADD COLUMN provenance_json LONGTEXT NULL AFTER expected_versions_json;
