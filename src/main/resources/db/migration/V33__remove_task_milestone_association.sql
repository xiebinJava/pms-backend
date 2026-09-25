ALTER TABLE project_task
    DROP FOREIGN KEY task_milestone_fk;

ALTER TABLE project_task
    DROP COLUMN milestone_id;
