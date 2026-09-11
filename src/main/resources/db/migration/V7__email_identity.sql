-- Email is the canonical identity for new accounts. The legacy username
-- columns remain for backwards-compatible reads and historical references.
ALTER TABLE sys_user ADD COLUMN email_normalized VARCHAR(320);

UPDATE sys_user
SET email_normalized = LOWER(TRIM(email))
WHERE email IS NOT NULL AND TRIM(email) <> '';

CREATE UNIQUE INDEX uk_user_email_normalized ON sys_user (email_normalized);
CREATE INDEX idx_user_email ON sys_user (email);
