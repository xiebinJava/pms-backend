-- Task-detail indexes and first-class attachments.
-- parent_id / task_id already exist; they were unused by APIs until now.

CREATE INDEX idx_task_parent ON project_task (parent_id);
CREATE INDEX idx_comment_task ON project_comment (task_id);

CREATE TABLE project_task_attachment (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id       BIGINT       NOT NULL,
    project_id    BIGINT       NOT NULL,
    file_key      VARCHAR(80)  NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100),
    size_bytes    BIGINT       NOT NULL,
    created_by    BIGINT,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    version       INT          NOT NULL DEFAULT 0,
    CONSTRAINT attachment_task_fk FOREIGN KEY (task_id) REFERENCES project_task (id),
    CONSTRAINT attachment_project_fk FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT attachment_creator_fk FOREIGN KEY (created_by) REFERENCES sys_user (id)
);

CREATE INDEX idx_attachment_task ON project_task_attachment (task_id, deleted);
