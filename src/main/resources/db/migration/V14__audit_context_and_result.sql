ALTER TABLE sys_operation_log ADD COLUMN project_id BIGINT NULL;
ALTER TABLE sys_operation_log ADD COLUMN reason VARCHAR(500) NULL;
ALTER TABLE sys_operation_log ADD COLUMN result VARCHAR(16) NOT NULL DEFAULT 'SUCCESS';
ALTER TABLE sys_operation_log ADD COLUMN ip VARCHAR(64) NULL;
ALTER TABLE sys_operation_log ADD COLUMN user_agent VARCHAR(500) NULL;

CREATE INDEX idx_operation_log_project_created ON sys_operation_log (project_id, created_at);
CREATE INDEX idx_operation_log_operator_created ON sys_operation_log (operator_id, created_at);
CREATE INDEX idx_operation_log_action_created ON sys_operation_log (action, created_at);
CREATE INDEX idx_operation_log_result_created ON sys_operation_log (result, created_at);
