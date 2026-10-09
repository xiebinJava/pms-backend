-- Keep the already-applied V58 migration immutable. This idempotent follow-up
-- completes the project-type column only for a development database whose
-- conflicting V50 connector script was manually repaired to the canonical V50
-- workflow migration before startup.
SET @pms_add_project_creation_enabled = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE pms_project_type ADD COLUMN project_creation_enabled BOOLEAN NOT NULL DEFAULT TRUE AFTER status',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pms_project_type'
      AND COLUMN_NAME = 'project_creation_enabled'
);
PREPARE pms_stmt FROM @pms_add_project_creation_enabled;
EXECUTE pms_stmt;
DEALLOCATE PREPARE pms_stmt;
