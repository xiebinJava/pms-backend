-- Development topics and stories may run independently of a project.
ALTER TABLE project_node_development_topic
    MODIFY COLUMN project_id BIGINT NULL,
    MODIFY COLUMN node_id BIGINT NULL;

ALTER TABLE project_node_development_story
    MODIFY COLUMN project_id BIGINT NULL,
    MODIFY COLUMN node_id BIGINT NULL,
    MODIFY COLUMN topic_id BIGINT NULL;

ALTER TABLE pms_development_item_workflow
    MODIFY COLUMN project_id BIGINT NULL,
    MODIFY COLUMN source_node_id BIGINT NULL;

CREATE TABLE pms_project_member_auto_managed (
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id, user_id),
    UNIQUE KEY uk_project_member_auto_managed (project_id, user_id),
    CONSTRAINT fk_project_member_auto_managed_member
        FOREIGN KEY (project_id, user_id) REFERENCES project_member (project_id, user_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pms_project_member_assignment_ref (
    project_id BIGINT NOT NULL,
    item_type VARCHAR(20) NOT NULL,
    item_id BIGINT NOT NULL,
    assignment_type VARCHAR(40) NOT NULL,
    assignment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id, item_type, item_id, assignment_type, assignment_id),
    UNIQUE KEY uk_project_member_assignment_ref
        (project_id, item_type, item_id, assignment_type, assignment_id),
    KEY idx_assignment_ref_project_user (project_id, user_id),
    KEY idx_assignment_ref_item (project_id, item_type, item_id),
    CONSTRAINT fk_project_member_assignment_ref_project
        FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT fk_project_member_assignment_ref_user
        FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Backfill references for existing project-bound assignments. Existing project
-- members are intentionally not marked auto-managed: their origin is unknown.
INSERT INTO pms_project_member_assignment_ref
    (project_id, item_type, item_id, assignment_type, assignment_id, user_id)
SELECT t.project_id, 'TOPIC', t.id, 'TOPIC_OWNER', t.id, t.owner_id
FROM project_node_development_topic t
WHERE t.project_id IS NOT NULL
  AND t.owner_id IS NOT NULL
  AND t.deleted = FALSE;

INSERT INTO pms_project_member_assignment_ref
    (project_id, item_type, item_id, assignment_type, assignment_id, user_id)
SELECT s.project_id, 'STORY', s.id, 'STORY_OWNER', s.id, s.owner_id
FROM project_node_development_story s
WHERE s.project_id IS NOT NULL
  AND s.owner_id IS NOT NULL;

INSERT INTO pms_project_member_assignment_ref
    (project_id, item_type, item_id, assignment_type, assignment_id, user_id)
SELECT w.project_id, w.item_type, w.item_id, 'WORKFLOW_NODE_OWNER', n.id, n.owner_id
FROM pms_development_item_workflow w
JOIN pms_development_item_workflow_node n ON n.workflow_id = w.id
WHERE w.project_id IS NOT NULL
  AND w.item_type IN ('TOPIC', 'STORY')
  AND n.owner_id IS NOT NULL;

INSERT INTO pms_project_member_assignment_ref
    (project_id, item_type, item_id, assignment_type, assignment_id, user_id)
SELECT w.project_id, w.item_type, w.item_id, 'TASK_ASSIGNEE', t.id, t.assignee_id
FROM pms_development_item_workflow w
JOIN pms_development_item_task t ON t.workflow_id = w.id
WHERE w.project_id IS NOT NULL
  AND w.item_type IN ('TOPIC', 'STORY')
  AND t.assignee_id IS NOT NULL
  AND t.deleted = FALSE;
