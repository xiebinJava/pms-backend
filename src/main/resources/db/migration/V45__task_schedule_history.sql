CREATE TABLE project_task_schedule_history (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id         BIGINT NOT NULL,
    task_id            BIGINT NOT NULL,
    previous_due_date  DATE NULL,
    next_due_date      DATE NULL,
    change_type        VARCHAR(24) NOT NULL,
    operator_id        BIGINT NULL,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT task_schedule_history_project_fk
        FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT task_schedule_history_task_fk
        FOREIGN KEY (task_id) REFERENCES project_task (id),
    CONSTRAINT task_schedule_history_operator_fk
        FOREIGN KEY (operator_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_task_schedule_history_task
    ON project_task_schedule_history (task_id, created_at);
CREATE INDEX idx_task_schedule_history_project
    ON project_task_schedule_history (project_id, created_at);
