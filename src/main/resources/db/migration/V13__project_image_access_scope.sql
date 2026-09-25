CREATE TABLE IF NOT EXISTS project_image (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id    BIGINT       NOT NULL,
    file_key      VARCHAR(255) NOT NULL,
    original_name VARCHAR(255),
    content_type  VARCHAR(120),
    size_bytes    BIGINT,
    created_by    BIGINT,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted       BOOLEAN NOT NULL DEFAULT FALSE,
    version       INT NOT NULL DEFAULT 0,
    CONSTRAINT project_image_project_fk FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT project_image_creator_fk FOREIGN KEY (created_by) REFERENCES sys_user (id)
);

CREATE UNIQUE INDEX uk_project_image_file_key ON project_image (project_id, file_key);
CREATE INDEX idx_project_image_project ON project_image (project_id, deleted, created_at);
