UPDATE pms_system_version
SET status = 'PLANNED'
WHERE status = 'DRAFT';

UPDATE pms_system_version
SET status = 'DEVELOPING'
WHERE status = 'TESTING';

UPDATE pms_system_version_history
SET from_status = 'PLANNED'
WHERE from_status = 'DRAFT';

UPDATE pms_system_version_history
SET to_status = 'PLANNED'
WHERE to_status = 'DRAFT';

UPDATE pms_system_version_history
SET from_status = 'DEVELOPING'
WHERE from_status = 'TESTING';

UPDATE pms_system_version_history
SET to_status = 'DEVELOPING'
WHERE to_status = 'TESTING';

ALTER TABLE pms_system_version
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PLANNED';

ALTER TABLE pms_system_version
    DROP CHECK chk_pms_system_version_status,
    ADD CONSTRAINT chk_pms_system_version_status CHECK (
        status IN ('PLANNED', 'DEVELOPING', 'RELEASED', 'ARCHIVED')
    );
