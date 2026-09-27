ALTER TABLE pms_ai_operation
    ADD COLUMN source_client VARCHAR(32) NULL AFTER user_id,
    ADD COLUMN request_id VARCHAR(160) NULL AFTER source_client,
    ADD COLUMN execution_mode VARCHAR(24) NULL AFTER request_id;

CREATE INDEX idx_pms_ai_operation_source_request
    ON pms_ai_operation (source_client, request_id);
