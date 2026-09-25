ALTER TABLE project_task
    DROP INDEX idx_task_development_story;

ALTER TABLE project_task
    DROP COLUMN development_story_id;
