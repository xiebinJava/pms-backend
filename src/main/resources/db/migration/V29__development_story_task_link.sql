ALTER TABLE project_task
    ADD COLUMN development_story_id BIGINT NULL;

CREATE INDEX idx_task_development_story
    ON project_task (project_id, node_id, development_story_id);
