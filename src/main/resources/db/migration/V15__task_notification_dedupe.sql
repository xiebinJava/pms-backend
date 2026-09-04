-- Reminder idempotency key. Existing event notifications keep NULL.
ALTER TABLE user_notification ADD COLUMN dedupe_key VARCHAR(128) NULL;

-- Reminder rows are read/marked-read only in phase one, so dedupe keys are
-- never reused through a soft-delete cycle.
CREATE UNIQUE INDEX uk_user_notification_dedupe
    ON user_notification (user_id, type, dedupe_key);
