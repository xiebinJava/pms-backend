-- Some existing databases recorded the task schedule history migration without
-- actually creating the table. Keep this repair forward-only and idempotent.
CREATE TABLE IF NOT EXISTS project_task_schedule_history (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id         BIGINT NOT NULL,
    task_id            BIGINT NOT NULL,
    previous_due_date  DATE NULL,
    next_due_date      DATE NULL,
    change_type        VARCHAR(24) NOT NULL,
    operator_id        BIGINT NULL,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_task_schedule_history_task (task_id, created_at),
    KEY idx_task_schedule_history_project (project_id, created_at),
    CONSTRAINT task_schedule_history_project_fk
        FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT task_schedule_history_task_fk
        FOREIGN KEY (task_id) REFERENCES project_task (id),
    CONSTRAINT task_schedule_history_operator_fk
        FOREIGN KEY (operator_id) REFERENCES sys_user (id)
);
