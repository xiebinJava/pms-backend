ALTER TABLE pms_ai_operation
    ADD COLUMN contract_id VARCHAR(160) NULL AFTER context_version,
    ADD COLUMN contract_version VARCHAR(80) NULL AFTER contract_id;

CREATE INDEX idx_pms_ai_operation_contract
    ON pms_ai_operation (contract_id, contract_version);
