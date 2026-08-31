-- Retention jobs filter by created_at before excluding successful results.
-- Keep created_at as the leading column so bounded deletes can use the index.
CREATE INDEX idx_login_log_retention ON sys_login_log (created_at, result);
