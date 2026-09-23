ALTER TABLE pms_project_type
    ADD COLUMN project_creation_enabled BOOLEAN NOT NULL DEFAULT TRUE AFTER status;
