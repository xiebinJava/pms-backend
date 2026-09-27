-- Archived: this file is not in Flyway's active migration location.
-- See migration-archive/README.md and active V58__ai_connector_operation_metadata.sql.
ALTER TABLE pms_ai_operation
    ADD COLUMN source_client VARCHAR(32) NULL AFTER user_id,
    ADD COLUMN request_id VARCHAR(160) NULL AFTER source_client,
    ADD COLUMN execution_mode VARCHAR(24) NULL AFTER request_id;

CREATE INDEX idx_pms_ai_operation_source_request
    ON pms_ai_operation (source_client, request_id);
