-- This migration may run against a database where the old V46 script partially
-- applied before the migration filename was corrected. Each change is therefore
-- guarded through information_schema instead of using MySQL syntax that is not
-- supported by every 8.x minor release.
SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE sys_operation_log ADD COLUMN dsh_session_id VARCHAR(128) NULL',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_operation_log'
      AND COLUMN_NAME = 'dsh_session_id'
);
PREPARE migration_stmt FROM @migration_sql;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE sys_operation_log ADD COLUMN dsh_agent_id VARCHAR(100) NULL',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_operation_log'
      AND COLUMN_NAME = 'dsh_agent_id'
);
PREPARE migration_stmt FROM @migration_sql;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE sys_operation_log ADD COLUMN dsh_agent_version VARCHAR(200) NULL',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_operation_log'
      AND COLUMN_NAME = 'dsh_agent_version'
);
PREPARE migration_stmt FROM @migration_sql;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE sys_operation_log ADD COLUMN dsh_workspace VARCHAR(80) NULL',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_operation_log'
      AND COLUMN_NAME = 'dsh_workspace'
);
PREPARE migration_stmt FROM @migration_sql;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE sys_operation_log ADD COLUMN dsh_tool VARCHAR(100) NULL',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_operation_log'
      AND COLUMN_NAME = 'dsh_tool'
);
PREPARE migration_stmt FROM @migration_sql;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE sys_operation_log ADD COLUMN dsh_operation_id VARCHAR(128) NULL',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_operation_log'
      AND COLUMN_NAME = 'dsh_operation_id'
);
PREPARE migration_stmt FROM @migration_sql;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'CREATE INDEX idx_operation_log_dsh_session ON sys_operation_log (dsh_session_id, created_at)',
        'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_operation_log'
      AND INDEX_NAME = 'idx_operation_log_dsh_session'
);
PREPARE migration_stmt FROM @migration_sql;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'CREATE INDEX idx_operation_log_dsh_agent ON sys_operation_log (dsh_agent_id, created_at)',
        'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_operation_log'
      AND INDEX_NAME = 'idx_operation_log_dsh_agent'
);
PREPARE migration_stmt FROM @migration_sql;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;
