ALTER TABLE pms_ai_conversation
    ADD COLUMN event_sequence BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS pms_ai_event_sequence_rebuild (
    id BIGINT NOT NULL,
    sequence_no BIGINT NOT NULL,
    PRIMARY KEY (id)
);

DELETE FROM pms_ai_event_sequence_rebuild;

REPLACE INTO pms_ai_event_sequence_rebuild (id, sequence_no)
SELECT id,
       ROW_NUMBER() OVER (PARTITION BY conversation_id ORDER BY created_at, id) AS sequence_no
FROM pms_ai_operation_event;

-- Put every row in a unique temporary value before removing the old plan-scoped key.
-- This makes the migration safe to retry after an interrupted DDL statement.
UPDATE pms_ai_operation_event
SET sequence_no = -id;

ALTER TABLE pms_ai_operation_event
    DROP INDEX uk_pms_ai_operation_event_plan_sequence;

UPDATE pms_ai_operation_event e
JOIN pms_ai_event_sequence_rebuild r ON r.id = e.id
SET e.sequence_no = r.sequence_no;

DROP TABLE pms_ai_event_sequence_rebuild;

UPDATE pms_ai_conversation c
SET c.event_sequence = COALESCE((
    SELECT MAX(e.sequence_no)
    FROM pms_ai_operation_event e
    WHERE e.conversation_id = c.id
), 0);

ALTER TABLE pms_ai_operation_event
    ADD UNIQUE KEY uk_pms_ai_operation_event_conversation_sequence (conversation_id, sequence_no);
