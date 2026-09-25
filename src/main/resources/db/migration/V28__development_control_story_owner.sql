ALTER TABLE project_node_development_story
    ADD COLUMN owner_id BIGINT NULL;

CREATE INDEX idx_node_development_story_owner
    ON project_node_development_story (project_id, owner_id);
