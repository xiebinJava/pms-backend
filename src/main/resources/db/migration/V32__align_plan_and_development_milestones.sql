DROP TABLE project_node_plan_item;

ALTER TABLE project_node_development_topic
    ADD COLUMN milestone_id BIGINT NULL;

ALTER TABLE project_node_development_topic
    DROP COLUMN iteration;

CREATE INDEX idx_node_development_topic_milestone
    ON project_node_development_topic (project_id, milestone_id);
