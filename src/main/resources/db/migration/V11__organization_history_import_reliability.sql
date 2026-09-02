-- Append-only organization history and durable import failure details.
-- OceanBase MySQL mode compatible; no existing business rows are rewritten.
CREATE TABLE sys_org_unit_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    org_unit_id BIGINT NOT NULL,
    action VARCHAR(40) NOT NULL,
    operator_id BIGINT,
    before_json VARCHAR(10000),
    after_json VARCHAR(10000),
    request_id VARCHAR(80),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT org_unit_history_org_fk FOREIGN KEY (org_unit_id) REFERENCES sys_org_unit (id),
    CONSTRAINT org_unit_history_operator_fk FOREIGN KEY (operator_id) REFERENCES sys_user (id)
);

ALTER TABLE sys_import_job ADD COLUMN failure_reason VARCHAR(500);
ALTER TABLE sys_import_job ADD COLUMN failed_at TIMESTAMP;

CREATE INDEX idx_org_unit_history_org_created ON sys_org_unit_history (org_unit_id, created_at, id);
CREATE INDEX idx_org_unit_history_operator_created ON sys_org_unit_history (operator_id, created_at);
