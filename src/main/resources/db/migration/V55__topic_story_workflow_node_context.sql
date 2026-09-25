-- Keep project-node context and topic-workflow-node context distinct.
ALTER TABLE project_node_development_story
    ADD COLUMN topic_workflow_node_id BIGINT NULL AFTER topic_id;

ALTER TABLE project_node_development_story
    ADD KEY idx_development_story_topic_workflow_node (topic_id, topic_workflow_node_id, sort);

ALTER TABLE pms_development_item_workflow
    ADD COLUMN project_mount_node_key VARCHAR(80) NULL AFTER source_node_id,
    ADD COLUMN story_mount_template_version_id BIGINT NULL AFTER template_version_id,
    ADD COLUMN story_mount_node_key VARCHAR(80) NULL AFTER story_mount_template_version_id;

ALTER TABLE pms_development_item_workflow
    ADD KEY idx_development_item_workflow_project_mount (item_type, project_mount_node_key),
    ADD KEY idx_development_item_workflow_story_mount (item_type, story_mount_template_version_id, story_mount_node_key);

CREATE TABLE pms_development_workflow_migration_issue (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    workflow_id   BIGINT NULL,
    item_type     VARCHAR(20) NOT NULL,
    item_id       BIGINT NOT NULL,
    issue_code    VARCHAR(80) NOT NULL,
    details       VARCHAR(500) NOT NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_development_workflow_migration_issue (item_type, item_id, issue_code),
    KEY idx_development_workflow_migration_issue_workflow (workflow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Existing topic workflows already pin their topic template version. Capture
-- the project mount key from that immutable version, never from the current
-- default template.
UPDATE pms_development_item_workflow w
JOIN pms_workflow_template_version v ON v.id = w.template_version_id
SET w.project_mount_node_key = NULLIF(
        JSON_UNQUOTE(JSON_EXTRACT(v.definition_json, '$.sourceProjectNodeKey')), '')
WHERE w.item_type = 'TOPIC'
  AND w.project_mount_node_key IS NULL
  AND JSON_VALID(v.definition_json)
  AND JSON_UNQUOTE(JSON_EXTRACT(v.definition_json, '$.sourceProjectNodeKey')) IS NOT NULL;

-- Existing topic workflows do not contain a deterministic story mount
-- snapshot. Never derive one from the current default story template: that
-- template may have changed after the topic was created. Leave the snapshot
-- empty and let the migration issue below make the historical gap visible.

-- Existing bound stories inherit the concrete node from their parent topic
-- workflow only when the snapshot resolves to exactly one node.
UPDATE project_node_development_story s
JOIN pms_development_item_workflow tw
  ON tw.item_type = 'TOPIC'
 AND tw.item_id = s.topic_id
JOIN pms_development_item_workflow_node n
  ON n.workflow_id = tw.id
 AND n.node_key = tw.story_mount_node_key
SET s.topic_workflow_node_id = n.id
WHERE s.topic_id IS NOT NULL
  AND s.topic_workflow_node_id IS NULL
  AND tw.story_mount_node_key IS NOT NULL;

INSERT IGNORE INTO pms_development_workflow_migration_issue
    (workflow_id, item_type, item_id, issue_code, details)
SELECT w.id, w.item_type, w.item_id, 'TOPIC_PROJECT_MOUNT_UNRESOLVED',
       '专题流程版本没有可解析的项目挂载节点 key，未自动迁移项目节点上下文'
FROM pms_development_item_workflow w
WHERE w.item_type = 'TOPIC'
  AND w.project_mount_node_key IS NULL;

INSERT IGNORE INTO pms_development_workflow_migration_issue
    (workflow_id, item_type, item_id, issue_code, details)
SELECT w.id, w.item_type, w.item_id, 'TOPIC_STORY_MOUNT_UNRESOLVED',
       '没有唯一可用的故事模板挂载节点，未自动迁移故事拆分挂载点'
FROM pms_development_item_workflow w
WHERE w.item_type = 'TOPIC'
  AND w.story_mount_node_key IS NULL;

INSERT IGNORE INTO pms_development_workflow_migration_issue
    (workflow_id, item_type, item_id, issue_code, details)
SELECT sw.id, 'STORY', s.id, 'STORY_TOPIC_NODE_UNRESOLVED',
       '故事已关联专题，但专题流程实例没有唯一可解析的故事挂载节点'
FROM project_node_development_story s
LEFT JOIN pms_development_item_workflow tw
  ON tw.item_type = 'TOPIC'
 AND tw.item_id = s.topic_id
LEFT JOIN pms_development_item_workflow_node n
  ON n.workflow_id = tw.id
 AND n.node_key = tw.story_mount_node_key
LEFT JOIN pms_development_item_workflow sw
  ON sw.item_type = 'STORY'
 AND sw.item_id = s.id
WHERE s.topic_id IS NOT NULL
  AND s.topic_workflow_node_id IS NULL
  AND n.id IS NULL;
